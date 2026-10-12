"""Final presentation adjustments and removal of obsolete UI-only wording."""
from pathlib import Path
import xml.etree.ElementTree as E
ROOT = Path(__file__).resolve().parents[1] / 'app/src/main/res'
A = 'http://schemas.android.com/apk/res/android'
E.register_namespace('android', A)
def write(path, doc):
    E.indent(doc, space='    ')
    doc.write(path, encoding='utf-8', xml_declaration=True)
for path in (ROOT/'layout').glob('*.xml'):
    doc = E.parse(path)
    for node in doc.iter():
        if node.get(f'{{{A}}}id') == '@+id/recoveryPhraseText': node.set(f'{{{A}}}textIsSelectable', 'false')
    if path.name == 'activity_recovery.xml':
        body = doc.getroot()[0]
        for node in doc.iter():
            if node.get(f'{{{A}}}text') == '@string/inheritance_details_heading': node.set(f'{{{A}}}text','@string/inheritance_details_recovery')
            if node.get(f'{{{A}}}text') == '@string/beneficiary_information_heading': node.set(f'{{{A}}}text','@string/beneficiary_heading')
        beneficiary = next(n for n in body if n.get(f'{{{A}}}id') == '@+id/recoveryBeneficiarySection')
        action = next(n for n in body if any(x.get(f'{{{A}}}id') == '@+id/claimButton' for x in n.iter()))
        body.remove(action); body.insert(list(body).index(beneficiary), action)
    write(path, doc)
stringspath=ROOT/'values/strings.xml'; doc=E.parse(stringspath); resources=doc.getroot()
obsolete={'create_title','generate_wallet','phrase_placeholder','dashboard_title','transaction_row','plan_row','plan_summary','recovery_status_summary'}
for node in list(resources):
    if node.get('name') in obsolete: resources.remove(node)
    if node.get('name') == 'plan_draft': node.text='Draft · Save complete; fund to lock Bitcoin.'
    if node.get('name') == 'plan_funded': node.text='Funded · Check verified conditions in Status / Recovery.'
    if node.text and '\n' in node.text: node.text=node.text.replace('\n', '\\n')
write(stringspath,doc)
for file, names in [('colors.xml',{'black'}),('dimens.xml',{'section_space'})]:
    path=ROOT/'values'/file; doc=E.parse(path)
    for node in list(doc.getroot()):
        if node.get('name') in names: doc.getroot().remove(node)
    write(path,doc)
# Decorative cards stay subtle; interactive field/button borders have stronger contrast.
path=ROOT/'values/colors.xml'; doc=E.parse(path)
if not any(n.get('name') == 'ui_control_border' for n in doc.getroot()):
    E.SubElement(doc.getroot(), 'color', name='ui_control_border').text='#647386'
write(path,doc)
path=ROOT/'drawable/bg_input.xml'; doc=E.parse(path)
for node in doc.iter():
    if node.get(f'{{{A}}}color') == '@color/ui_border': node.set(f'{{{A}}}color','@color/ui_control_border')
write(path,doc)
path=ROOT/'values/styles.xml'; doc=E.parse(path)
for node in doc.iter():
    if node.get('name') == 'strokeColor': node.text='@color/ui_control_border'
write(path,doc)
(ROOT/'drawable/ic_launcher_background.xml').write_text(f'''<shape xmlns:android="{A}"><solid android:color="@color/ui_background" /></shape>\n''',encoding='utf-8')
(ROOT/'drawable/ic_launcher_foreground.xml').write_text(f'''<vector xmlns:android="{A}" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="@color/ui_accent" android:pathData="M39,34H57C68,34 73,39 73,45C73,50 70,53 66,54C72,56 75,60 75,65C75,72 69,77 57,77H39V69H44V42H39ZM53,42V51H57C62,51 64,49 64,46C64,43 62,42 57,42ZM53,59V69H58C63,69 66,67 66,64C66,61 63,59 58,59Z" />
    <path android:fillColor="@color/ui_accent" android:pathData="M49,28H54V35H49ZM58,28H63V35H58ZM49,76H54V83H49ZM58,76H63V83H58Z" />
</vector>\n''',encoding='utf-8')
print('Formatted XML, separated recovery actions, and removed obsolete presentation resources.')
