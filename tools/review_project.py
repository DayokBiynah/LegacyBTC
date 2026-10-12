"""Perform structural final checks without reading wallet files or contacting peers."""
from pathlib import Path
import xml.etree.ElementTree as ET
import re
root = Path(__file__).resolve().parents[1]
java = root / 'app/src/main/java/com/example/legacybtc'
android = '{http://schemas.android.com/apk/res/android}'
files = list((root / 'app/src/main/res').rglob('*.xml')) + list((root / 'app/src/mainnet/res').rglob('*.xml'))
for path in files:
    tree = ET.parse(path)
    ids = [node.get(android + 'id') for node in tree.iter() if node.get(android + 'id', '').startswith('@+id/')]
    assert len(ids) == len(set(ids)), f'Duplicate XML IDs in {path}'
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml')
registered = {node.get(android + 'name') for node in manifest.iter('activity')}
for name in ['CreateWalletActivity', 'WalletDashboardActivity', 'SendBitcoinActivity', 'ReceiveBitcoinActivity',
             'InheritanceSetupActivity', 'BeneficiaryRecoveryActivity', 'TransactionHistoryActivity']:
    assert '.' + name in registered, f'Missing required Activity {name}'
    assert (java / (name + '.java')).exists()
for path in java.glob('*.java'):
    source = path.read_text(encoding='utf-8')
    assert '/**' in source, f'Missing source JavaDoc {path}'
    assert not re.search(r'\bTODO\b|\bFIXME\b|UnsupportedOperationException', source), f'Unfinished source {path}'
    assert 'requireTestnet' not in source, f'Obsolete network guard {path}'
for name in ['User', 'Owner', 'Beneficiary', 'BitcoinWallet', 'BitcoinTransaction', 'BitcoinService', 'InheritancePlan']:
    assert (java / (name + '.java')).exists()
assert 'extends User' in (java / 'Owner.java').read_text()
assert 'extends User' in (java / 'Beneficiary.java').read_text()
for name in ['README.md', 'PROPOSAL_COMPLIANCE.md', 'FINAL_VERIFICATION_STATUS.md']:
    assert (root / name).exists()
print(f'Checked {len(files)} XML files, seven required registered Activities, all Java class documentation, domain classes and required documents.')
