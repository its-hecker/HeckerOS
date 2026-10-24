#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Build a pinned GPL LibrePods source variant with a distinct application identity."""
import hashlib,json,os,re,shutil,subprocess,tarfile,tempfile,urllib.parse,zipfile
from pathlib import Path
from android_app_sdk import prepare_sdk
ROOT=Path(__file__).resolve().parents[1]
FOLDER=ROOT/'prebuilts/librepods'

def recipe_id():
    files=[FOLDER/'source.json',ROOT/'framework/earbuds/HeckerEarbudsView.java',ROOT/'tools/build_librepods.py',ROOT/'tools/android_app_sdk.py']+sorted((FOLDER/'hecker').glob('*'))
    digest=hashlib.sha256()
    for path in files:digest.update(str(path.relative_to(ROOT)).encode()+b'\0'+path.read_bytes()+b'\0')
    return digest.hexdigest()

def cached_apk(recipe):
    apk=FOLDER/'prebuilt/HeckerLibrePods.apk';meta=apk.with_name('build.json')
    if not apk.is_file() or not meta.is_file():return None
    try:record=json.loads(meta.read_text())
    except (ValueError,OSError):return None
    return apk if record.get('recipe')==recipe and hashlib.sha256(apk.read_bytes()).hexdigest()==record.get('sha256') else None

def java_environment():
    env=os.environ.copy();candidates=[]
    if env.get('JAVA_HOME'):candidates.append(Path(env['JAVA_HOME'])/'bin/java')
    for parent in (ROOT,*ROOT.parents):candidates.extend(parent/'prebuilts/jdk'/version/'linux-x86/bin/java' for version in ('jdk21','jdk25'))
    if shutil.which('java'):candidates.append(Path(shutil.which('java')))
    for candidate in candidates:
        if not candidate.is_file():continue
        result=subprocess.run([str(candidate),'-version'],capture_output=True,text=True)
        version=re.search(r'version "(\d+)',result.stdout+result.stderr)
        if not result.returncode and version and int(version.group(1))>=21:
            home=candidate.resolve().parents[1];env['JAVA_HOME']=str(home);env['PATH']=str(home/'bin')+os.pathsep+env.get('PATH','');break
    else:raise ValueError('LibrePods needs Java 21+: sync prebuilts/jdk/jdk21 or set JAVA_HOME')
    proxy=urllib.parse.urlparse(env.get('HTTPS_PROXY',''))
    if proxy.hostname and not proxy.username:
        opts=' '.join('-D'+scheme+'.proxyHost='+proxy.hostname+' -D'+scheme+'.proxyPort='+str(proxy.port or 80) for scheme in ('http','https'))
        trust=Path('/etc/ssl/certs/java/cacerts')
        if trust.is_file():opts+=' -Djavax.net.ssl.trustStore='+str(trust)
        env['GRADLE_OPTS']=env.get('GRADLE_OPTS','')+' '+opts
    return env

def patch_source(source):
    app=source/'android/app';main=app/'src/main/java/me/kavishdevar/librepods/MainActivity.kt'
    content=main.read_text();anchor='        super.onCreate(savedInstanceState)'
    if content.count(anchor)!=1:raise ValueError('Review LibrePods main activity lifecycle')
    content=content.replace(anchor,anchor+'\n        if (me.kavishdevar.librepods.hecker.HeckerHeadphonesActivity.route(this)) return')
    anchor='    override fun onStop() {'
    if content.count(anchor)!=1:raise ValueError('Review LibrePods stop lifecycle')
    content=content.replace(anchor,anchor+'\n        me.kavishdevar.librepods.hecker.HeckerHeadphonesActivity.stopWatching(this)')
    anchor='    override fun onDestroy() {'
    content=content.replace(anchor,'''    override fun onStart() {
        super.onStart()
        me.kavishdevar.librepods.hecker.HeckerHeadphonesActivity.watch(this)
    }

    override fun onResume() {
        super.onResume()
        me.kavishdevar.librepods.hecker.HeckerHeadphonesActivity.route(this)
    }

'''+anchor)
    content=content.replace('            unbindService(serviceConnection)','            if (::serviceConnection.isInitialized) unbindService(serviceConnection)')
    content=content.replace('            unregisterReceiver(connectionStatusReceiver)','            if (::connectionStatusReceiver.isInitialized) unregisterReceiver(connectionStatusReceiver)')
    main.write_text(content)
    gradle=app/'build.gradle.kts';content=gradle.read_text();anchor='        applicationId = "me.kavishdevar.librepods"'
    if content.count(anchor)!=1:raise ValueError('Review LibrePods application identity')
    content=content.replace(anchor,'        applicationId = "org.hecker.librepods"\n        ndk { abiFilters += "arm64-v8a" }')
    gradle.write_text(content.replace('versionName = appVersionName','versionName = appVersionName + "-hecker1"',1))
    manifest=app/'src/main/AndroidManifest.xml';content=manifest.read_text()
    content=content.replace('android:label="@string/app_name"','android:label="@string/hecker_librepods_app_name"',1)
    unused='''    <uses-permission
        android:name="android.permission.MODIFY_PHONE_STATE"
        tools:ignore="ProtectedPermissions" />'''
    if content.count(unused)!=1:raise ValueError('Review LibrePods privileged permission requests')
    content=content.replace(unused,'').replace('    </application>','''        <activity android:name="me.kavishdevar.librepods.hecker.HeckerHeadphonesActivity"
            android:exported="false" android:theme="@android:style/Theme.Material.NoActionBar" />
    </application>''')
    manifest.write_text(content)
    for path in (app/'src').rglob('*.kt'):
        content=path.read_text();changed=content.replace('"me.kavishdevar.librepods"','"org.hecker.librepods"')
        if changed!=content:path.write_text(changed)
    target=app/'src/main/java/me/kavishdevar/librepods/hecker';target.mkdir(parents=True,exist_ok=True)
    for path in (FOLDER/'hecker').glob('*.java'):shutil.copyfile(path,target/path.name)
    artwork=app/'src/main/java/org/hecker/earbuds/HeckerEarbudsView.java';artwork.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(ROOT/'framework/earbuds/HeckerEarbudsView.java',artwork)
    for directory,name in [('values','strings.xml'),('values-zh-rCN','strings-zh.xml')]:
        target=app/'src/main/res'/directory/'hecker_headphones.xml';target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(FOLDER/'hecker'/name,target)
    if (source/'android/local.properties').exists():raise ValueError('Private signing configuration must not enter the source build')

def build(offline=False,sdk_cache=None):
    recipe=recipe_id();cached=cached_apk(recipe)
    if cached:print('Hecker LibrePods source recipe and APK: verified');return cached
    if offline:raise ValueError('Hecker LibrePods needs rebuilding; run tools/prepare_librepods.py online')
    info=json.loads((FOLDER/'source.json').read_text());revision=info['source_commit']
    if not re.fullmatch('[0-9a-f]{40}',revision):raise ValueError('LibrePods source must be pinned')
    env=java_environment();upstream=ROOT/'out/librepods-upstream'
    def git(*args,**kwargs):return subprocess.run(['git','-C',str(upstream),*args],check=True,**kwargs)
    if not (upstream/'.git').exists():
        upstream.mkdir(parents=True,exist_ok=True);git('init','-q');git('remote','add','origin',info['repository'])
    remote=subprocess.check_output(['git','-C',str(upstream),'remote','get-url','origin'],text=True).strip()
    if remote.removesuffix('.git')!=info['repository'].removesuffix('.git'):raise ValueError('Preserving an unfamiliar LibrePods source cache')
    present=subprocess.run(['git','-C',str(upstream),'cat-file','-e',revision+'^{commit}'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode==0
    if not present:git('fetch','--depth=1','origin',revision)
    sdk=prepare_sdk(sdk_cache or env.get('ANDROID_HOME') or ROOT/'out/librepods-sdk')
    work=ROOT/'out/librepods-work'/recipe[:16]
    if not work.exists():
        work.parent.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='source-',dir=work.parent) as temporary:
            archive=Path(temporary)/'source.tar'
            with archive.open('wb') as output:git('archive',revision,stdout=output)
            extracted=Path(temporary)/'tree'
            with tarfile.open(archive) as source:source.extractall(extracted,filter='data')
            patch_source(extracted);shutil.move(str(extracted),str(work))
    env['ANDROID_HOME']=str(sdk);env['ANDROID_SDK_ROOT']=str(sdk);env['GRADLE_USER_HOME']=str(ROOT/'out/librepods-gradle')
    subprocess.run(['bash','./gradlew','--no-daemon','--console=plain','--max-workers=4',':app:assembleFossRelease'],cwd=work/'android',env=env,check=True)
    outputs=list((work/'android/app/build/outputs/apk/foss/release').glob('*.apk'))
    if len(outputs)!=1:raise ValueError('Expected one LibrePods APK')
    with zipfile.ZipFile(outputs[0]) as apk:
        if not {'AndroidManifest.xml','classes.dex','lib/arm64-v8a/libbluetooth_socket.so'}<=set(apk.namelist()):raise ValueError('Incomplete LibrePods APK')
    target=FOLDER/'prebuilt/HeckerLibrePods.apk';target.parent.mkdir(parents=True,exist_ok=True)
    temporary=target.with_suffix('.download');shutil.copyfile(outputs[0],temporary);temporary.replace(target)
    target.with_name('build.json').write_text(json.dumps({'recipe':recipe,'source_commit':revision,'package':info['package'],'size':target.stat().st_size,'sha256':hashlib.sha256(target.read_bytes()).hexdigest()},indent=2)+'\n')
    print('Hecker LibrePods built from pinned source; ROM packaging applies its platform signature');return target
