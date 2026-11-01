"""Ensure audited custom UI resources have complete Russian packaging and safe format arguments."""
import json,re,unittest
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]

def nodes(files):
    result={}
    for file in files:
        for node in ET.parse(file).getroot():
            if node.tag in ('string','string-array','plurals') and node.get('translatable')!='false':
                result[node.tag,node.get('name')]=node
    return result

class RussianResourcesTests(unittest.TestCase):
    def test_complete_resource_and_format_coverage(self):
        groups=[]
        for module in ['app','aosp-launcher','launcher','netlab','settings']:
            res=ROOT/module/'res'
            groups.append((module,nodes(sorted((res/'values').glob('*.xml'))),nodes(sorted((res/'values-ru').glob('*.xml')))))
        for module in ['qs','faceisland','headphones','ims','motionisland']:
            res=ROOT/'framework'/module
            groups.append(('framework/'+module,nodes([res/'strings.xml']),nodes([res/'strings-ru.xml'])))
        pattern=r'%%|%(?:\d+\$)?[-+#\d.]*[a-zA-Z]'
        count=0
        for module,default,russian in groups:
            self.assertFalse(set(default)-set(russian),(module,set(default)-set(russian)))
            for key,original in default.items():
                translated=russian[key];count+=1
                self.assertEqual(len(original),len(translated),(module,key))
                self.assertEqual(sorted(re.findall(pattern,''.join(original.itertext()))),
                                 sorted(re.findall(pattern,''.join(translated.itertext()))),(module,key))
                self.assertTrue(''.join(translated.itertext()).strip(),(module,key))
        self.assertGreaterEqual(count,1120)
        print('Russian: %d custom resources across %d groups have translations and matching format/array structure.'%(count,len(groups)))

    def test_platform_locales_are_in_native_copy_plan(self):
        copies=json.loads((ROOT/'framework/customization-copies.json').read_text())
        for module in ['qs','faceisland','headphones','ims','motionisland']:
            source='framework/'+module+'/strings-ru.xml'
            targets=[target for target,value in copies.items() if value==source]
            self.assertEqual(len(targets),1,source)
            self.assertIn('/SystemUI/res/values-ru/',targets[0])
        self.assertEqual(copies['packages/apps/Settings/res/values-ru/hecker_ru.xml'],'settings/res/values-ru/hecker_ru.xml')

    def test_recovery_localization_has_no_root_requirement(self):
        for language in ['values','values-ru','values-zh-rCN']:
            values=nodes((ROOT/'app/res'/language).glob('*.xml'))
            intro=''.join(values['string','sensor_recovery_intro'].itertext())
            self.assertTrue(intro)
            self.assertIn(('string','sensor_recovery_unavailable'),values)
            self.assertNotIn(('string','sensor_recovery_root'),values)

if __name__=='__main__':unittest.main()
