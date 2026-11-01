"""Production broker/CLI tests without root, a phone, init or network access."""
import json,os,re,subprocess,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SCRIPT=ROOT/'app/assets/proximity-recover.sh'
MOCK='''#!/usr/bin/env python3
import json,os,sys
from pathlib import Path
root=Path(os.environ['SENSOR_TEST_ROOT']);c=json.loads((root/'config').read_text())
name=Path(sys.argv[0]).name
if name=='su':raise AssertionError('Root must never be invoked')
if name=='cmd':
 with (root/'requests').open('a') as f:f.write(' '.join(sys.argv[1:])+'\\n')
 if sys.argv[1:]==['-l']:
  if c.get('available',True):print('Currently running services:\\n    hecker_sensor_recovery')
 elif sys.argv[1:]==['hecker_sensor_recovery','recover']:
  print('RESULT='+c.get('request','running'))
  if c.get('request','running')!='running':sys.exit(1)
 elif sys.argv[1:]==['hecker_sensor_recovery','status']:
  print('RESULT='+c.get('status','service_restarted'));print('REQUEST=1')
 else:sys.exit(2)
'''
class SensorRecoveryTests(unittest.TestCase):
 def run_cli(self,mode='--recover',**config):
  with tempfile.TemporaryDirectory() as temporary:
   root=Path(temporary);(root/'config').write_text(json.dumps(config));bindir=root/'bin';bindir.mkdir()
   for name in ['cmd','sleep','su']:
    p=bindir/name;p.write_text(MOCK);p.chmod(0o755)
   env=dict(os.environ,PATH=str(bindir)+os.pathsep+os.environ['PATH'],SENSOR_TEST_ROOT=str(root))
   result=subprocess.run(['/bin/sh',str(SCRIPT),mode],env=env,text=True,capture_output=True,timeout=15)
   log=(root/'requests').read_text() if (root/'requests').exists() else ''
   self.assertNotIn('su',log)
   return result,log
 def test_root_free_restart_then_status(self):
  result,log=self.run_cli();self.assertEqual(result.returncode,0,result.stdout+result.stderr)
  self.assertIn('RESULT=service_restarted',result.stdout)
  self.assertEqual(log.splitlines(),['-l','hecker_sensor_recovery recover','hecker_sensor_recovery status'])
 def test_status_is_read_only(self):
  result,log=self.run_cli('--status',status='idle');self.assertEqual(result.returncode,0)
  self.assertNotIn(' recover',log)
 def test_old_rom_is_rejected_without_root_fallback(self):
  result,log=self.run_cli(available=False);self.assertEqual(result.returncode,4)
  self.assertIn('RESULT=unavailable',result.stdout);self.assertEqual(log.splitlines(),['-l'])
 def test_server_refusal_is_preserved(self):
  result,log=self.run_cli(request='blocked');self.assertEqual(result.returncode,1)
  self.assertIn('RESULT=blocked',result.stdout);self.assertNotIn(' status',log)
 def test_polling_is_bounded(self):
  result,log=self.run_cli(status='running');self.assertEqual(result.returncode,7)
  self.assertEqual(log.count('hecker_sensor_recovery recover'),1)
  self.assertEqual(log.count('hecker_sensor_recovery status'),25)
 def test_invalid_arguments_never_call_broker(self):
  result,log=self.run_cli('--anything');self.assertEqual(result.returncode,64);self.assertEqual(log,'')
 def test_production_engine_and_access_checks(self):
  with tempfile.TemporaryDirectory() as temporary:
   sources=[ROOT/'framework/sensors/SensorRecoveryEngine.java',ROOT/'framework/sensors/SensorRecoveryAccess.java',ROOT/'tests/SensorRecoveryEngineTest.java']
   subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',temporary,*map(str,sources)],check=True)
   subprocess.run(['java','-cp',temporary,'SensorRecoveryEngineTest'],check=True)
 def test_init_receipt_and_property_boundaries(self):
  policy=(ROOT/'rom/sepolicy/private/hecker_sensor_recovery.te').read_text()
  self.assertNotRegex(policy,r'get_prop\(system_server,\s*init_svc_debug_prop\)')
  self.assertIn('neverallow { domain -init } hecker_sensor_recovery_status_prop:property_service set;',policy)
  self.assertNotRegex(policy,r'set_prop\((platform_app|shell),')
  actions={};current=None
  for line in (ROOT/'configs/hecker-sensor-recovery.rc').read_text().splitlines():
   if line.startswith('on '):current=line[3:];actions[current]=[]
   elif line.strip() and not line.lstrip().startswith('#'):actions[current].append(line.strip())
  self.assertEqual(len(actions),7)
  for action,service in [('multihal20','vendor.sensors-hal-2-0-multihal'),('multihal21','vendor.sensors-hal-2-1-multihal'),('hal20','vendor.sensors-hal-2-0')]:
   request=actions['property:sys.hecker.sensor_recovery.request='+action]
   self.assertEqual([x for x in request if x.startswith('restart ')],['restart '+service])
   self.assertIn('setprop sys.hecker.sensor_recovery.before ${init.svc_debug_pid.'+service+'}',request)
   self.assertIn('setprop sys.hecker.sensor_recovery.ack_token ${sys.hecker.sensor_recovery.token}',request)
   response=actions['property:init.svc.'+service+'=running && property:sys.hecker.sensor_recovery.pending='+action]
   self.assertEqual(response,['setprop sys.hecker.sensor_recovery.after ${init.svc_debug_pid.'+service+'}'])
   # Leaving pending armed is necessary: an early queued running event can still contain the old PID.
   self.assertFalse(any('pending idle' in x for x in response))
  for commands in actions.values():
   for command in commands:self.assertTrue(command.startswith(('setprop sys.hecker.sensor_recovery.','restart vendor.sensors-hal-')))
 def test_ui_has_no_root_or_shell_execution(self):
  for file in ['SensorRecoveryActivity.java','SensorRecoveryClient.java','SensorRecoveryTileService.java']:
   text=(ROOT/'app/src/org/hecker/suite'/file).read_text()
   self.assertNotIn('ProcessBuilder(',text);self.assertNotIn('Runtime.getRuntime()',text)
  service=(ROOT/'framework/sensors/HeckerSensorRecoveryService.java').read_text()
  self.assertIn('enforceSuite()',service);self.assertIn('SensorRecoveryAccess.shell(Binder.getCallingUid())',service)
  self.assertIn('telecom.isInCall()',service);self.assertIn('keyguard.isKeyguardLocked()',service)
if __name__=='__main__':unittest.main()
