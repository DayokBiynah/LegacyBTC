"""Preserve actual read-only Mainnet smoke evidence before a full suite replaces its report."""
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
files = list((root / 'app/build/outputs/androidTest-results/connected/mainnetDebug').rglob('TEST-*.xml'))
assert len(files) == 1, 'Expected one Mainnet smoke XML report'
suite = ET.parse(files[0]).getroot()
assert int(suite.get('tests', '0')) == 1
assert all(int(suite.get(key, '0')) == 0 for key in ['failures', 'errors', 'skipped'])
assert any(case.get('classname', '').endswith('MainnetNetworkTest') for case in suite.iter('testcase'))
target = root / '.verification'
target.mkdir(exist_ok=True)
shutil.copyfile(files[0], target / 'mainnet-smoke.xml')
print('Archived one passing live Mainnet discovery/handshake test; no funded operation was tested.')
