# SPDX-License-Identifier: Apache-2.0
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
from check_built_output import check_vault

class VaultOutputTests(unittest.TestCase):
    def test_rejects_missing_apk_and_each_runtime_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);source=root/'vendor/hecker/floral/suite';out=root/'output'
            check_vault(root,out)  # Older source has no Vault module.
            manifest=source/'vault/AndroidManifest.xml';manifest.parent.mkdir(parents=True);manifest.write_text('<manifest/>')
            with self.assertRaisesRegex(ValueError,'missing HeckerVault.apk'):check_vault(root,out)
            apk=out/'system_ext/priv-app/HeckerVault/HeckerVault.apk';apk.parent.mkdir(parents=True);apk.write_bytes(b'bad apk')
            with self.assertRaisesRegex(ValueError,'APK is invalid'):check_vault(root,out)
            with zipfile.ZipFile(apk,'w') as archive:
                archive.writestr('AndroidManifest.xml',b'binary manifest fixture')
                archive.writestr('classes.dex',b'dex\n035\x00fixture')
            for folder,name in [('permissions','privapp-permissions-hecker-vault.xml'),('default-permissions','default-permissions-hecker-vault.xml'),('sysconfig','hecker-vault-transport.xml')]:
                original=source/'configs'/name;original.parent.mkdir(parents=True,exist_ok=True);original.write_text('<fixture/>')
                with self.assertRaisesRegex(ValueError,name):check_vault(root,out)
                installed=out/'system_ext/etc'/folder/name;installed.parent.mkdir(parents=True,exist_ok=True);installed.write_bytes(original.read_bytes())
            check_vault(root,out)
            installed.write_text('<stale/>')
            with self.assertRaisesRegex(ValueError,'runtime file'):check_vault(root,out)

if __name__=='__main__':unittest.main()
