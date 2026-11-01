"""Run the production recovery script with fake Android properties; never touch hardware."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
SCRIPT=ROOT/'app/assets/proximity-recover.sh'
MOCK='''#!/usr/bin/env python3
import json,os,sys
from pathlib import Path
root=Path(os.environ['SENSOR_TEST_ROOT'])
config=json.loads((root/'config.json').read_text())
command=Path(sys.argv[0]).name
changed=(root/'changed').exists()
service=config.get('service','vendor.sensors-hal-2-0-multihal')
before='1111'
after='2222' if changed and config.get('changes_pid',True) else before
if command=='getprop':
 key=sys.argv[1]
 if key=='ro.product.device':print(config.get('device','coral'))
 elif key=='init.svc.'+service:print('running' if config.get('running',True) else 'stopped')
 elif key=='init.svc_debug_pid.'+service and config.get('debug_pid',True):print(after)
elif command=='id':print(config.get('uid',0))
elif command=='pidof':
 if config.get('fallback_pid',True):print(after)
elif command=='setprop':
 with (root/'requests').open('a') as out:out.write(' '.join(sys.argv[1:])+'\\n')
 if config.get('deny',False):sys.exit(1)
 (root/'changed').write_text('1')
'''


class SensorRecoveryTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name);self.bin=self.root/'bin';self.bin.mkdir()
        for name in ['getprop','id','pidof','setprop','sleep']:
            path=self.bin/name;path.write_text(MOCK);path.chmod(0o755)
        self.env=dict(os.environ,PATH=str(self.bin)+os.pathsep+os.environ['PATH'],
                      TMPDIR=str(self.root),SENSOR_TEST_ROOT=str(self.root))

    def run_script(self,mode='--recover',**config):
        (self.root/'config.json').write_text(json.dumps(config))
        return subprocess.run(['/bin/sh',str(SCRIPT),mode],env=self.env,
                              text=True,capture_output=True,timeout=10)

    def requests(self):
        path=self.root/'requests'
        return path.read_text().splitlines() if path.exists() else []

    def test_manual_restart_verifies_new_pid_and_cleans_lock(self):
        result=self.run_script()
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertIn('PID_BEFORE=1111',result.stdout)
        self.assertIn('PID_AFTER=2222',result.stdout)
        self.assertIn('RESULT=service_restarted',result.stdout)
        self.assertIn('CHECK=Uncover, cover and uncover',result.stdout)
        self.assertEqual(self.requests(),['ctl.restart vendor.sensors-hal-2-0-multihal'])
        self.assertFalse((self.root/'hecker-proximity-recover.lock').exists())

    def test_non_root_status_is_read_only(self):
        result=self.run_script('--status',uid=2000)
        self.assertEqual(result.returncode,0)
        self.assertIn('RESULT=status_only',result.stdout)
        self.assertEqual(self.requests(),[])

    def test_root_denial_never_requests_a_restart(self):
        result=self.run_script(uid=2000)
        self.assertEqual(result.returncode,2)
        self.assertIn('RESULT=root_required',result.stdout)
        self.assertEqual(self.requests(),[])

    def test_other_devices_are_rejected(self):
        result=self.run_script(device='raven')
        self.assertEqual(result.returncode,3)
        self.assertEqual(self.requests(),[])

    def test_unknown_or_stopped_service_is_not_started(self):
        result=self.run_script(running=False)
        self.assertEqual(result.returncode,4)
        self.assertEqual(self.requests(),[])

    def test_existing_lock_prevents_parallel_restarts(self):
        lock=self.root/'hecker-proximity-recover.lock';lock.mkdir()
        result=self.run_script()
        self.assertEqual(result.returncode,5)
        self.assertTrue(lock.exists())
        self.assertEqual(self.requests(),[])

    def test_property_denial_is_not_success_and_unlocks(self):
        result=self.run_script(deny=True)
        self.assertEqual(result.returncode,6)
        self.assertIn('RESULT=restart_denied',result.stdout)
        self.assertFalse((self.root/'hecker-proximity-recover.lock').exists())

    def test_same_pid_times_out_without_claiming_recovery(self):
        result=self.run_script(changes_pid=False)
        self.assertEqual(result.returncode,7)
        self.assertIn('RESULT=restart_not_verified',result.stdout)
        self.assertNotIn('RESULT=service_restarted',result.stdout)
        self.assertEqual(len(self.requests()),1)

    def test_pidof_fallback_is_used_when_debug_property_is_missing(self):
        result=self.run_script(debug_pid=False)
        self.assertEqual(result.returncode,0,result.stdout)
        self.assertIn('PID_AFTER=2222',result.stdout)

    def test_missing_pid_fails_before_changing_any_service(self):
        result=self.run_script(debug_pid=False,fallback_pid=False)
        self.assertEqual(result.returncode,8)
        self.assertEqual(self.requests(),[])

    def test_invalid_arguments_are_rejected(self):
        result=self.run_script('--reset-everything')
        self.assertEqual(result.returncode,64)
        self.assertEqual(self.requests(),[])

    def test_supported_alternate_service_is_selected_exactly(self):
        result=self.run_script(service='vendor.sensors-hal-2-1-multihal',device='flame')
        self.assertEqual(result.returncode,0)
        self.assertEqual(self.requests(),['ctl.restart vendor.sensors-hal-2-1-multihal'])


if __name__=='__main__':unittest.main()
