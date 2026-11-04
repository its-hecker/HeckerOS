"""Gitiles archive recompression must not weaken Bison content authentication."""
import gzip,hashlib,io,sys,tarfile,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from check_vpn_policy import verified_bison_files

def archive(name,content,mtime):
    raw=io.BytesIO()
    with tarfile.open(fileobj=raw,mode='w') as output:
        member=tarfile.TarInfo(name);member.size=len(content);output.addfile(member,io.BytesIO(content))
    return gzip.compress(raw.getvalue(),mtime=mtime)
class VpnPolicyDownloadTests(unittest.TestCase):
    def test_recompression_preserves_authenticated_content(self):
        content=b'Pinned Bison skeleton\n';pins={'skeletons/yacc.c':hashlib.sha256(content).hexdigest()}
        first=archive('skeletons/yacc.c',content,1);second=archive('skeletons/yacc.c',content,2)
        self.assertNotEqual(hashlib.sha256(first).digest(),hashlib.sha256(second).digest())
        self.assertEqual(verified_bison_files(first,pins),verified_bison_files(second,pins))
    def test_modified_or_missing_file_is_rejected(self):
        pins={'skeletons/yacc.c':hashlib.sha256(b'expected').hexdigest()}
        with self.assertRaises(ValueError):verified_bison_files(archive('skeletons/yacc.c',b'changed',1),pins)
        with self.assertRaises(ValueError):verified_bison_files(archive('different',b'expected',1),pins)
    def test_traversal_is_rejected(self):
        with self.assertRaises(ValueError):verified_bison_files(archive('../yacc.c',b'expected',1),{'../yacc.c':hashlib.sha256(b'expected').hexdigest()})
