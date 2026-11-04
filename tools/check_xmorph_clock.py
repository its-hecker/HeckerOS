#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compile the vendored native XMorph library against locked Android inputs."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile
from compile_sdk import fetch
from check_launcher import COMPILER

ROOT = Path(__file__).resolve().parents[1]
LIBRARY = ROOT/'third_party/xmorphclock'


def build(sdk, cache, output):
    output.mkdir(parents=True, exist_ok=True)
    lock = json.loads((ROOT/'tests/compose-inputs.lock.json').read_text())
    compiler = [fetch(cache, name, lock[name]) for name in sorted(COMPILER)]
    extra = json.loads((LIBRARY/'sdk-inputs.lock.json').read_text())
    for name, item in extra.items():
        path = fetch(cache, name, item)
        with zipfile.ZipFile(path) as archive:
            path.with_suffix('.jar').write_bytes(archive.read('classes.jar'))
    core = sdk/'core.jar'
    if not core.exists():
        with zipfile.ZipFile(sdk/'core.aar') as archive:
            core.write_bytes(archive.read('classes.jar'))
    def run(*args): subprocess.run([str(x) for x in args], check=True)
    resources = output/'resources.zip'
    run(sdk/'aapt2', 'compile', '--dir', LIBRARY/'res', '-o', resources)
    run(sdk/'aapt2', 'link', '-I', sdk/'android.jar', '--manifest', LIBRARY/'AndroidManifest.xml',
        '--java', output/'java', '-A', LIBRARY/'assets', '--auto-add-overlay',
        '-o', output/'library.apk', resources)
    with zipfile.ZipFile(output/'library.apk') as archive:
        names = archive.namelist()
        assert sum(name.startswith('assets/clock_faces/') for name in names) == 86
        assert sum(name.startswith('res/font/') for name in names) == 47
        assert 'assets/xmorph_license/GPL-3.0.txt' in names
    classes = output/'classes'
    run('java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', classes,
        *sorted((output/'java').rglob('*.java')))
    classpath = os.pathsep.join(map(str, [classes, sdk/'android.jar', core,
        cache/'core-ktx-1.13.1.jar', cache/'annotation-jvm.jar', cache/'kotlin-stdlib.jar']))
    # Annotation declarations are provided by the same checksum-locked Compose inputs.
    fetch(cache, 'annotation-jvm.jar', lock['annotation-jvm.jar'])
    run('java', '-cp', os.pathsep.join(map(str, compiler)),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
        '-jvm-target', '17', '-classpath', classpath, '-d', classes,
        *sorted((LIBRARY/'src').rglob('*.kt')))
    return classes, resources


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk-cache', type=Path, required=True)
    parser.add_argument('--cache', type=Path, default=ROOT/'generated/compose-check')
    parser.add_argument('--output', type=Path, default=ROOT/'out/xmorph-check')
    parser.add_argument('--verify-models', action='store_true')
    args = parser.parse_args()
    build(args.sdk_cache.resolve(), args.cache.resolve(), args.output.resolve())
    if args.verify_models:
        verify_models(args.cache.resolve(), args.output.resolve())
    print('XMorphClock native Kotlin renderer, models, catalog, 86 assets and 47 font resources: PASS')


def verify_models(cache, output):
    """Run the production catalog/parser on the JVM, replacing only Android asset I/O."""
    lock = json.loads((ROOT/'tests/xmorph-inputs.lock.json').read_text())
    json_jar = fetch(cache, 'xmorph-json-20240303.jar', lock['xmorph-json-20240303.jar'])
    host = output/'host'; host.mkdir(exist_ok=True)
    files = {
        'android/content/Context.java': '''package android.content;
public class Context { private final android.content.res.AssetManager assets;
 public Context(String root){assets=new android.content.res.AssetManager(root);}
 public android.content.res.AssetManager getAssets(){return assets;} }''',
        'android/content/res/AssetManager.java': '''package android.content.res;
public class AssetManager { private final java.io.File root;public AssetManager(String r){root=new java.io.File(r);}
 public String[] list(String path){return new java.io.File(root,path).list();}
 public java.io.InputStream open(String path)throws java.io.IOException{return new java.io.FileInputStream(new java.io.File(root,path));} }''',
    }
    sources = []
    for name, text in files.items():
        path = host/name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text(text); sources.append(path)
    subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(host), *map(str, sources)], check=True)
    probe = host/'XMorphCatalogProbe.kt'
    probe.write_text('''import android.content.Context
import com.drdisagree.xmorphclock.nativehost.XMorphNative
import com.drdisagree.xmorphclock.data.mapper.ClockFaceJsonMapper
import com.drdisagree.xmorphclock.domain.model.*
fun main(args: Array<String>) {
 val context=Context(args[0]);val entries=XMorphNative.entries(context)
 check(entries.size==86 && entries.map{it.id}.toSet().size==86)
 var states=0
 entries.forEach { entry ->
  val face=entry.face;check(face.name.isNotBlank() && face.layers.isNotEmpty())
  check(face.layers.map{it.id}.toSet().size==face.layers.size)
  check(ClockFaceJsonMapper.fromJson(ClockFaceJsonMapper.toJson(face))==face)
  val ids=face.layers.map{it.id}.toSet()
  fun checkNode(node:FaceNode) {
   check(node.placement.x.isFinite() && node.placement.y.isFinite())
   when(node) {
    is LayerNode -> check(node.id in ids && node.size.isFinite() && node.size>0)
    is RowNode -> node.children.forEach(::checkNode)
    is ColumnNode -> node.children.forEach(::checkNode)
    is StackNode -> node.children.forEach(::checkNode)
   }
  }
  listOf(face.large,face.small,face.aodLarge?:face.large,face.aodSmall?:face.small).forEach { checkNode(it);states++ }
  check(XMorphNative.selected(context,entry.id)==entry)
 }
 check(XMorphNative.selected(context,"../../private.json")==entries.first())
 check(XMorphNative.selected(context,null)==entries.first())
 check(ClockFaceJsonMapper.fromJson("{")==null)
 println("86 packaged native faces / "+states+" large, small and AOD states: parser round trips, layer references and stable selection PASS")
}
''')
    compiler = [cache/name for name in sorted(COMPILER)]
    cp = os.pathsep.join(map(str, [host, output/'classes', json_jar, cache/'kotlin-stdlib.jar']))
    subprocess.run(['java', '-cp', os.pathsep.join(map(str, compiler)), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                    '-no-stdlib', '-no-reflect', '-jvm-target', '17', '-classpath', cp,
                    '-d', str(host), str(probe)], check=True)
    subprocess.run(['java', '-cp', cp, 'XMorphCatalogProbeKt', str(LIBRARY/'assets')], check=True)


if __name__ == '__main__': main()
