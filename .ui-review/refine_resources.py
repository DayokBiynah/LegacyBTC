"""One-time UI resource generation; wallet and Bitcoin code is deliberately excluded."""
from pathlib import Path
import xml.etree.ElementTree as E

ROOT = Path(__file__).resolve().parents[1] / 'app/src/main/res'
A = 'http://schemas.android.com/apk/res/android'
E.register_namespace('android', A)

def save(relative, text):
    path = ROOT / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.strip() + '\n', encoding='utf-8')

save('values/colors.xml', '''<resources>
    <color name="black">#000000</color>
    <color name="white">#FFFFFF</color>
    <color name="ui_background">#101419</color>
    <color name="ui_surface">#1A2028</color>
    <color name="ui_surface_high">#242C36</color>
    <color name="ui_border">#394553</color>
    <color name="ui_text">#F2F5F9</color>
    <color name="ui_secondary">#B3BECC</color>
    <color name="ui_accent">#F3B34C</color>
    <color name="ui_on_accent">#19140C</color>
    <color name="ui_success">#91D6AF</color>
    <color name="ui_warning">#F0C47B</color>
    <color name="ui_error">#FFB0AC</color>
    <color name="ui_warning_surface">#302719</color>
    <color name="ui_error_surface">#352324</color>
</resources>''')
save('values/dimens.xml', '''<resources>
    <dimen name="screen_padding">20dp</dimen>
    <dimen name="card_padding">18dp</dimen>
    <dimen name="space_small">8dp</dimen>
    <dimen name="space_medium">12dp</dimen>
    <dimen name="space_large">20dp</dimen>
    <dimen name="section_space">28dp</dimen>
    <dimen name="corner_radius">16dp</dimen>
    <dimen name="control_radius">12dp</dimen>
    <dimen name="control_height">56dp</dimen>
    <dimen name="text_title">28sp</dimen>
    <dimen name="text_section">19sp</dimen>
    <dimen name="text_body">16sp</dimen>
    <dimen name="text_caption">14sp</dimen>
</resources>''')
for name, enabled, disabled in [('button_primary', '@color/ui_accent', '@color/ui_surface_high'),
                                ('button_primary_text', '@color/ui_on_accent', '@color/ui_secondary')]:
    save(f'color/{name}.xml', f'''<selector xmlns:android="{A}">
    <item android:state_enabled="false" android:color="{disabled}" />
    <item android:color="{enabled}" />
</selector>''')
for name, fill, border, radius in [('bg_card', 'ui_surface', 'ui_border', 'corner_radius'),
                                  ('bg_address', 'ui_background', 'ui_border', 'control_radius'),
                                  ('bg_notice', 'ui_warning_surface', 'ui_warning_surface', 'control_radius'),
                                  ('bg_error', 'ui_error_surface', 'ui_error_surface', 'control_radius'),
                                  ('bg_feedback', 'ui_surface_high', 'ui_border', 'control_radius')]:
    save(f'drawable/{name}.xml', f'''<shape xmlns:android="{A}" android:shape="rectangle">
    <solid android:color="@color/{fill}" />
    <stroke android:width="1dp" android:color="@color/{border}" />
    <corners android:radius="@dimen/{radius}" />
</shape>''')
save('drawable/bg_input.xml', f'''<selector xmlns:android="{A}">
    <item android:state_focused="true"><shape><solid android:color="@color/ui_background" /><stroke android:width="2dp" android:color="@color/ui_accent" /><corners android:radius="@dimen/control_radius" /></shape></item>
    <item><shape><solid android:color="@color/ui_background" /><stroke android:width="1dp" android:color="@color/ui_border" /><corners android:radius="@dimen/control_radius" /></shape></item>
</selector>''')
save('drawable/bg_qr.xml', f'''<shape xmlns:android="{A}"><solid android:color="@color/white" /><corners android:radius="@dimen/corner_radius" /></shape>''')
save('values/styles.xml', '''<resources>
    <style name="UiText" parent="Widget.AppCompat.TextView">
        <item name="android:layout_width">match_parent</item><item name="android:layout_height">wrap_content</item>
        <item name="android:textColor">@color/ui_text</item><item name="android:textSize">@dimen/text_body</item>
        <item name="android:fontFamily">sans</item><item name="android:lineSpacingExtra">4dp</item>
        <item name="android:layout_marginBottom">@dimen/space_medium</item>
    </style>
    <style name="UiTitle" parent="UiText"><item name="android:textSize">@dimen/text_title</item><item name="android:fontFamily">sans-serif-medium</item><item name="android:layout_marginBottom">@dimen/space_small</item></style>
    <style name="UiSection" parent="UiText"><item name="android:textSize">@dimen/text_section</item><item name="android:fontFamily">sans-serif-medium</item><item name="android:layout_marginTop">@dimen/space_small</item></style>
    <style name="UiSupporting" parent="UiText"><item name="android:textColor">@color/ui_secondary</item></style>
    <style name="UiCaption" parent="UiSupporting"><item name="android:textSize">@dimen/text_caption</item></style>
    <style name="UiLabel" parent="UiText"><item name="android:textSize">@dimen/text_caption</item><item name="android:fontFamily">sans-serif-medium</item><item name="android:layout_marginTop">@dimen/space_medium</item><item name="android:layout_marginBottom">@dimen/space_small</item></style>
    <style name="UiNetwork" parent="UiCaption"><item name="android:textColor">@color/ui_warning</item><item name="android:layout_marginBottom">@dimen/space_large</item></style>
    <style name="UiAddress" parent="UiCaption"><item name="android:textColor">@color/ui_text</item><item name="android:fontFamily">monospace</item><item name="android:background">@drawable/bg_address</item><item name="android:padding">@dimen/space_medium</item><item name="android:textIsSelectable">true</item><item name="android:breakStrategy">simple</item><item name="android:hyphenationFrequency">none</item></style>
    <style name="UiFeedback" parent="UiCaption"><item name="android:background">@drawable/bg_feedback</item><item name="android:padding">@dimen/space_medium</item><item name="android:accessibilityLiveRegion">polite</item><item name="android:minHeight">0dp</item></style>
    <style name="UiNotice" parent="UiCaption"><item name="android:textColor">@color/ui_warning</item><item name="android:background">@drawable/bg_notice</item><item name="android:padding">@dimen/space_medium</item></style>
    <style name="UiCard"><item name="android:layout_width">match_parent</item><item name="android:layout_height">wrap_content</item><item name="android:orientation">vertical</item><item name="android:padding">@dimen/card_padding</item><item name="android:background">@drawable/bg_card</item><item name="android:layout_marginBottom">@dimen/space_large</item></style>
    <style name="UiInput" parent="Widget.AppCompat.EditText"><item name="android:layout_width">match_parent</item><item name="android:layout_height">wrap_content</item><item name="android:minHeight">@dimen/control_height</item><item name="android:background">@drawable/bg_input</item><item name="android:backgroundTint">@null</item><item name="android:padding">14dp</item><item name="android:textSize">@dimen/text_body</item><item name="android:textColor">@color/ui_text</item><item name="android:textColorHint">@color/ui_secondary</item><item name="android:layout_marginBottom">@dimen/space_medium</item><item name="android:importantForAutofill">no</item></style>
    <style name="UiButtonPrimary" parent="Widget.Material3.Button">
        <item name="android:layout_width">match_parent</item><item name="android:layout_height">wrap_content</item><item name="android:minHeight">@dimen/control_height</item><item name="android:layout_marginBottom">@dimen/space_small</item>
        <item name="android:insetTop">0dp</item><item name="android:insetBottom">0dp</item><item name="android:paddingTop">12dp</item><item name="android:paddingBottom">12dp</item><item name="android:paddingLeft">16dp</item><item name="android:paddingRight">16dp</item>
        <item name="android:textAllCaps">false</item><item name="android:textSize">@dimen/text_body</item><item name="android:fontFamily">sans-serif-medium</item><item name="cornerRadius">@dimen/control_radius</item><item name="backgroundTint">@color/button_primary</item><item name="android:textColor">@color/button_primary_text</item>
    </style>
    <style name="UiButtonSecondary" parent="UiButtonPrimary"><item name="backgroundTint">@color/ui_surface_high</item><item name="android:textColor">@color/ui_text</item><item name="strokeColor">@color/ui_border</item><item name="strokeWidth">1dp</item></style>
    <style name="UiButtonQuiet" parent="UiButtonSecondary"><item name="backgroundTint">@android:color/transparent</item><item name="strokeWidth">0dp</item><item name="android:textColor">@color/ui_secondary</item></style>
    <style name="UiButtonDestructive" parent="UiButtonQuiet"><item name="android:textColor">@color/ui_error</item></style>
    <style name="UiDialog" parent="ThemeOverlay.Material3.MaterialAlertDialog"><item name="colorSurface">@color/ui_surface</item><item name="colorOnSurface">@color/ui_text</item><item name="colorPrimary">@color/ui_accent</item><item name="android:colorBackground">@color/ui_surface</item></style>
</resources>''')
save('values/themes.xml', '''<resources>
    <!-- A fixed dark palette keeps wallet screens consistent with the phone's theme setting. -->
    <style name="Base.Theme.LegacyBTC" parent="Theme.Material3.Dark.NoActionBar">
        <item name="colorPrimary">@color/ui_accent</item><item name="colorOnPrimary">@color/ui_on_accent</item><item name="colorSecondary">@color/ui_accent</item><item name="colorOnSecondary">@color/ui_on_accent</item>
        <item name="colorSurface">@color/ui_surface</item><item name="colorOnSurface">@color/ui_text</item><item name="colorSurfaceVariant">@color/ui_surface_high</item><item name="colorOnSurfaceVariant">@color/ui_secondary</item><item name="colorOutline">@color/ui_border</item><item name="colorError">@color/ui_error</item>
        <item name="android:colorBackground">@color/ui_background</item><item name="android:windowBackground">@color/ui_background</item><item name="android:textColorPrimary">@color/ui_text</item><item name="android:textColorSecondary">@color/ui_secondary</item><item name="android:fontFamily">sans</item>
        <item name="colorPrimaryContainer">@color/ui_warning_surface</item><item name="colorOnPrimaryContainer">@color/ui_accent</item><item name="colorSecondaryContainer">@color/ui_surface_high</item><item name="colorOnSecondaryContainer">@color/ui_text</item>
        <item name="android:statusBarColor">@color/ui_background</item><item name="android:navigationBarColor">@color/ui_background</item><item name="android:windowLightStatusBar">false</item>
        <item name="materialButtonStyle">@style/UiButtonPrimary</item><item name="editTextStyle">@style/UiInput</item><item name="materialAlertDialogTheme">@style/UiDialog</item><item name="alertDialogTheme">@style/UiDialog</item>
    </style>
    <style name="Theme.LegacyBTC" parent="Base.Theme.LegacyBTC" />
</resources>''')
save('values-night/themes.xml', '''<resources>
    <!-- The same dark design is used in both system modes. -->
    <style name="Theme.LegacyBTC" parent="Base.Theme.LegacyBTC" />
</resources>''')

def el(tag, id=None, style=None, **attrs):
    node = E.Element(tag)
    if id: node.set(f'{{{A}}}id', '@+id/' + id)
    if style: node.set('style', '@style/' + style)
    for k,v in attrs.items(): node.set(f'{{{A}}}{k}', str(v))
    return node
def text(key, id=None, style='UiText', **attrs): return el('TextView', id, style, text='@string/' + key, **attrs)
def btn(key, id, kind='Secondary', **attrs): return el('Button', id, 'UiButton' + kind, text='@string/' + key, **attrs)
def card(*children, id=None, **attrs):
    n = el('LinearLayout', id, 'UiCard', **attrs); n.extend(children); return n
def column(id=None): return el('LinearLayout', id, layout_width='match_parent', layout_height='wrap_content', orientation='vertical')
def field(key, id, inputType, sensitive=False, value=None, **attrs):
    label = text(key, style='UiLabel', labelFor='@id/' + id)
    control = el('EditText', id, 'UiInput', inputType=inputType, importantForAutofill='no', **attrs)
    if sensitive: control.set(f'{{{A}}}saveEnabled', 'false')
    if value: control.set(f'{{{A}}}text', '@string/' + value)
    if inputType.startswith('textMultiLine'): control.set(f'{{{A}}}minLines','3'); control.set(f'{{{A}}}gravity','top|start')
    return [label,control]
def feedback(): return text('empty','errorText','UiFeedback')
def network(): return text('testnet_only','networkStatusText','UiNetwork')
def page(name, title, children, titleid='title', show_network=True):
    scroll = el('ScrollView','screenScroll',layout_width='match_parent',layout_height='match_parent',fillViewport='true',clipToPadding='false')
    body = el('LinearLayout',layout_width='match_parent',layout_height='wrap_content',orientation='vertical',padding='@dimen/screen_padding')
    body.append(text(title,titleid,'UiTitle'))
    if show_network: body.append(network())
    body.extend(children); scroll.append(body)
    E.indent(scroll, space='    ')
    save('layout/' + name + '.xml', '<?xml version="1.0" encoding="utf-8"?>\n' + E.tostring(scroll,encoding='unicode'))

page('activity_main','app_name',[
    text('welcome_subtitle','subtitleText','UiSupporting'),
    card(text('welcome_heading',style='UiSection'),text('welcome_description',style='UiSupporting'),btn('create_wallet','createWalletButton','Primary'),btn('restore_wallet','restoreWalletButton')),
    card(text('saved_wallets_heading',style='UiSection'),text('open_description',style='UiCaption'),btn('open_wallet','openWalletButton')),
    text('testnet_safety',style='UiNotice')],titleid='titleText')

restore_fields = lambda: [*field('phrase_hint','recoveryPhraseInput','textMultiLine|textNoSuggestions',True),*field('passphrase_hint','passphraseInput','textPassword',True)]
page('activity_create_wallet','setup_title',[
    text('setup_description','description','UiSupporting'),
    card(text('create_new_heading',style='UiSection'),text('create_description',style='UiCaption'),btn('create_wallet','generateWalletButton','Primary'),id='createPath'),
    card(text('restore_existing_heading',style='UiSection'),text('restore_description',style='UiCaption'),*restore_fields(),btn('restore_wallet','restoreWalletButton'),id='restorePath'),
    card(text('backup_heading',style='UiSection'),text('backup_description',style='UiSupporting'),text('empty','recoveryPhraseText','UiAddress',saveEnabled='false'),text('phrase_security_warning',style='UiNotice'),el('CheckBox','backupConfirmed',layout_width='match_parent',layout_height='wrap_content',minHeight='56dp',text='@string/backup_confirmed',textColor='@color/ui_text',textSize='16sp',visibility='gone'),id='backupSection',visibility='gone'),
    card(text('wallet_ready_heading',style='UiSection'),text('restore_saved','walletReadyDescription','UiCaption'),text('receiving_address_label',style='UiLabel'),text('address_placeholder','walletAddressText','UiAddress'),id='walletReadySection',visibility='gone'),
    feedback(),btn('continue_label','continueButton','Primary',visibility='gone'),btn('back','backButton','Quiet')])
page('activity_restore_wallet','restore_title',[
    text('restore_description','description','UiSupporting'),card(*restore_fields(),btn('restore_wallet','restoreWalletButton','Primary'),id='restorePath'),
    text('empty','restoredAddressText','UiFeedback'),feedback(),btn('continue_label','continueButton','Primary',visibility='gone'),btn('back','backButton','Quiet')])
page('activity_open_wallet','saved_wallets_heading',[
    text('open_description','openWalletDescription','UiSupporting'),el('LinearLayout','savedWalletsContainer',layout_width='match_parent',layout_height='wrap_content',orientation='vertical'),feedback(),btn('back','backButton','Quiet')],titleid='openWalletTitle')
# Keep the original named scrolling control used by the saved-wallet screen.
p = ROOT/'layout/activity_open_wallet.xml'; d=E.parse(p); d.getroot().set(f'{{{A}}}id','@+id/savedWalletsScrollView'); E.indent(d,space='    '); d.write(p,encoding='utf-8',xml_declaration=True)
page('activity_dashboard','app_name',[
    text('wallet_identity_placeholder','walletIdentityText','UiCaption'),
    card(text('available_balance_label',style='UiCaption'),text('balance_placeholder','balanceText','UiTitle',textSize='34sp'),text('balance_helper',style='UiCaption'),btn('send','sendButton','Primary'),btn('receive','receiveButton')),
    card(text('receiving_address_label',style='UiSection'),text('address_placeholder','walletAddressText','UiAddress'),btn('copy_address','copyAddressButton','Quiet')),
    btn('transaction_history','historyButton'),btn('inheritance','inheritanceButton'),btn('settings','settingsButton'),
    card(text('recent_transactions','recentTitle','UiSection'),text('no_transactions','recentTransactionsText','UiSupporting')),
    card(text('inheritance_plan_heading',style='UiSection'),text('no_plan','inheritanceStatusText','UiSupporting')),
    feedback(),btn('retry_connection','connectButton','Quiet'),btn('close_wallet','backButton','Quiet')])
page('activity_send','send',[
    card(text('available_balance_label',style='UiCaption'),text('balance_placeholder','balanceText','UiSection')),
    card(*field('recipient_hint','recipientInput','textNoSuggestions'),*field('amount_hint','amountInput','numberDecimal'),*field('fee_hint','feeInput','number',value='default_fee'),text('fee_description','feeDescription','UiCaption')),
    feedback(),btn('review_send','sendButton','Primary'),text('empty','confirmationText','UiFeedback'),btn('cancel','cancelButton','Quiet')])
page('activity_receive','receive',[
    text('receive_description','description','UiSupporting'),
    card(el('ImageView','qrImage',layout_width='match_parent',layout_height='240dp',adjustViewBounds='true',scaleType='fitCenter',padding='16dp',background='@drawable/bg_qr',contentDescription='@string/qr_description',layout_marginBottom='20dp'),text('receiving_address_label',style='UiLabel'),text('address_placeholder','walletAddressText','UiAddress'),btn('copy_address','copyAddressButton','Primary'),btn('generate_address','generateAddressButton'),btn('share_address','shareButton')),
    card(text('beneficiary_information_heading',style='UiSection'),text('beneficiary_info_description',style='UiCaption'),text('public_key_label',style='UiLabel'),text('empty','beneficiaryPublicKeyText','UiAddress'),btn('copy_beneficiary_info','copyBeneficiaryButton','Quiet')),
    feedback(),btn('back','backButton','Quiet')])
page('activity_history','transaction_history',[
    text('history_hint','hint','UiSupporting'),column('transactionsContainer'),feedback(),btn('back','backButton','Quiet')])
page('activity_inheritance_setup','inheritance_setup',[
    text('inheritance_description','description','UiSupporting'),
    card(text('beneficiary_section',style='UiSection'),text('beneficiary_setup_helper',style='UiCaption'),*field('owner_name','ownerNameInput','textPersonName',value='default_owner'),*field('beneficiary_name','beneficiaryNameInput','textPersonName'),*field('beneficiary_address','beneficiaryAddressInput','textNoSuggestions'),*field('beneficiary_key','beneficiaryKeyInput','textNoSuggestions'),btn('add_beneficiary','addBeneficiaryButton')),
    card(text('inheritance_details_heading',style='UiSection'),*field('inheritance_amount','inheritanceAmountInput','numberDecimal'),*field('inheritance_hours','periodInput','number'),text('period_helper',style='UiCaption'),btn('set_period','setPeriodButton'),btn('save_plan','savePlanButton','Primary'),btn('update_plan','updatePlanButton')),
    card(text('plan_status_heading',style='UiSection'),text('no_plan','planSummaryText','UiSupporting'),*field('fee_hint','feeInput','number',value='default_fee'),btn('fund_plan','fundPlanButton','Primary'),btn('inheritance_status','recoveryButton'),btn('cancel_plan','cancelPlanButton','Destructive')),
    text('timelock_warning',style='UiNotice'),text('saved_plans','plansTitle','UiSection'),column('plansContainer'),feedback(),btn('back','backButton','Quiet')])
page('activity_recovery','beneficiary_recovery',[
    text('recovery_description','description','UiSupporting'),
    card(text('recovery_locked_heading','recoveryStateText','UiSection'),text('recovery_locked_helper','recoveryConditionText','UiCaption'),text('no_plan_import','planStatusText','UiSupporting'),btn('check_status','checkStatusButton')),
    card(text('inheritance_details_heading',style='UiSection'),text('empty','recoveryAmountText','UiTitle'),text('empty','recoveryPeriodText','UiCaption'),text('empty','recoveryDeadlineText','UiSupporting'),text('empty','recoveryEligibilityText','UiSupporting'),id='recoveryDetailsSection',visibility='gone'),
    card(text('beneficiary_information_heading',style='UiSection'),text('empty','recoveryBeneficiaryText','UiSection'),text('receiving_address_label',style='UiLabel'),text('empty','recoveryAddressText','UiAddress'),text('public_key_label',style='UiLabel'),text('empty','recoveryPublicKeyText','UiAddress'),text('transaction_id_label',style='UiLabel'),text('empty','recoveryFundingText','UiAddress'),id='recoveryBeneficiarySection',visibility='gone'),
    card(text('recovery_actions_heading',style='UiSection'),*field('fee_hint','feeInput','number',value='default_fee'),btn('claim_recover','claimButton','Primary',enabled='false'),btn('view_details','viewDetailsButton'),btn('cancel_funded_plan','cancelFundedButton','Destructive',enabled='false'),text('empty','confirmationText','UiFeedback')),
    card(text('recovery_package_heading',style='UiSection'),text('package_helper',style='UiCaption'),btn('import_plan','importPlanButton'),btn('export_plan','exportPlanButton'),btn('rescan_history','rescanButton','Quiet')),
    text('saved_plans','plansTitle','UiSection'),column('plansContainer'),feedback(),btn('back','backButton','Quiet')])
page('activity_settings','settings',[
    card(text('security_heading',style='UiSection'),text('security_description','description','UiSupporting'),text('device_security_label',style='UiLabel'),text('device_security_helper',style='UiCaption'),btn('reveal_recovery','revealRecoveryButton')),
    card(text('backup_heading',style='UiSection'),text('settings_backup_helper',style='UiSupporting')),
    card(text('network_heading',style='UiSection'),text('peer_description',style='UiCaption'),*field('peer_host','peerHostInput','textUri'),*field('peer_port','peerPortInput','number'),btn('save_network','saveNetworkButton')),
    feedback(),btn('back','backButton','Quiet')])

page('unused','empty',[],show_network=False) # Removed below; page helper is also used for row resources.
(ROOT/'layout/unused.xml').unlink()
def row(name, content):
    E.indent(content, space='    '); save('layout/'+name+'.xml','<?xml version="1.0" encoding="utf-8"?>\n'+E.tostring(content,encoding='unicode'))
row('item_saved_wallet',card(text('wallet_identity_placeholder','walletNameText','UiSection'),text('testnet_only',style='UiNetwork'),text('address_placeholder','savedAddressText','UiAddress'),btn('open_wallet_action','openSavedWalletButton','Primary'),btn('delete','deleteSavedWalletButton','Destructive')))
row('item_transaction',card(text('empty','transactionTypeText','UiSection'),text('empty','transactionAmountText','UiTitle'),text('empty','transactionStatusText','UiCaption'),text('empty','transactionDateText','UiCaption'),text('transaction_id_label',style='UiLabel'),text('empty','transactionIdText','UiAddress'),btn('view_details','transactionDetailsButton','Quiet')))
row('item_plan',card(text('empty','planNameText','UiSection'),text('empty','planAmountText','UiSupporting'),btn('select_plan','selectPlanButton')))
row('item_empty_state',card(text('empty_state_heading','emptyTitle','UiSection'),text('empty','emptyMessage','UiSupporting')))

# Wording changes affect presentation only; technical meanings are retained.
strings = E.parse(ROOT/'values/strings.xml'); resources = strings.getroot()
changes = {
'testnet_only':'Bitcoin Testnet · Test coins only', 'create_description':'Start with a new Wallet and save its Recovery Phrase before continuing.',
'restore_description':'Enter the original 12–24 recovery words in order. Use the original BIP-39 passphrase, if any. A different passphrase opens a different Wallet.',
'phrase_hint':'Recovery Phrase', 'passphrase_hint':'Original BIP-39 passphrase (optional)', 'backup_confirmed':'I saved my Recovery Phrase securely.',
'welcome_subtitle':'Your Bitcoin. Your legacy.', 'no_wallets':'Create or restore a Wallet to see it here.', 'dashboard_title':'Wallet', 'balance_value':'%1$s BTC',
'no_plan':'No Inheritance Plan yet.', 'review_send':'Send Bitcoin', 'confirm_transaction':'Review transaction', 'confirm_send':'Confirm and sign',
'receive_description':'Use this QR code or address to receive Bitcoin Testnet coins. Never send real Bitcoin here.',
'fee_description':'Manual fee: 1–1000 sat/vB. Review the fee and total before signing.',
'security_description':'Wallet data is encrypted on this device. Screenshots and automatic cloud backups are blocked.',
'reveal_recovery':'View Recovery Phrase', 'recovery_words':'Recovery Phrase · Keep private',
'inheritance_description':'Choose who receives the Bitcoin, how much is covered, and when the Timelock ends.',
'inheritance_amount':'Covered amount (BTC)', 'inheritance_hours':'Inheritance period (hours)', 'beneficiary_address':'Beneficiary Bitcoin Testnet address',
'beneficiary_key':'Beneficiary public key (compressed)', 'save_plan':'Save Inheritance Plan', 'inheritance_status':'View status and recovery',
'recovery_description':'Select a plan or import a Recovery Package. Claiming requires the beneficiary Wallet and verified Timelock conditions.',
'beneficiary_info_description':'Give the owner this receiving address and public key. Never share your Recovery Phrase.',
'saved_plans':'Saved Inheritance Plans', 'beneficiary_recovery':'Inheritance Recovery', 'cancel_funded_plan':'Cancel funded plan · Owner',
'peer_description':'Bitcoin Testnet3 only. Leave the host empty for automatic discovery. A custom peer must support bloom filters.',
'peer_host':'Testnet peer host (optional)', 'peer_port':'Testnet peer port', 'save_network':'Save and reconnect', 'setup_title':'Set up your Wallet',
'restore_saved':'Wallet ready. Continue to the Dashboard to synchronize.', 'copy_beneficiary_info':'Copy public information',
'history_hint':'Review activity and confirmations. Open details to retry a saved pending transaction.', 'delete_wallet':'Delete Wallet?',
'balance_placeholder':'0 BTC', 'empty_state_heading':'Nothing here yet', 'open_wallet':'Open Existing Wallet',
'inheritance_summary':'%1$s',
}
new = {
'welcome_heading':'Start your Wallet', 'welcome_description':'Create a new Wallet or restore one with your Recovery Phrase.',
'saved_wallets_heading':'Saved Wallets', 'testnet_safety':'Development network. Use Bitcoin Testnet coins only.',
'setup_description':'Choose a new Wallet or restore an existing one.', 'create_new_heading':'Create a new Wallet', 'restore_existing_heading':'Restore an existing Wallet',
'backup_heading':'Keep your backup safe', 'backup_description':'Write these words down in order and store them offline. They restore your Wallet if this device is lost.',
'phrase_security_warning':'Anyone with this Recovery Phrase can access your Bitcoin. Do not share it or store it in a screenshot.',
'wallet_ready_heading':'Wallet ready', 'receiving_address_label':'Receiving address', 'available_balance_label':'Available balance',
'balance_helper':'Available for ordinary payments. Coins locked in an Inheritance Plan are shown separately.',
'wallet_identity_placeholder':'Your Wallet', 'wallet_identifier':'Wallet · %1$s', 'open_wallet_action':'Open Wallet',
'beneficiary_information_heading':'For inheritance setup', 'public_key_label':'Public key', 'transaction_id_label':'Transaction ID',
'beneficiary_section':'1. Beneficiary', 'beneficiary_setup_helper':'Use the public address and key from the beneficiary’s Receive screen.',
'inheritance_details_heading':'2. Inheritance details', 'plan_status_heading':'3. Plan status', 'period_helper':'1–87840 hours. The calendar deadline starts when you save or update the draft.',
'timelock_warning':'A saved draft does not lock Bitcoin until funded. This is a calendar Timelock, not proof of death or inactivity. The owner may cancel even after the deadline until the beneficiary spends the funds.',
'inheritance_plan_heading':'Inheritance Plan', 'recovery_locked_heading':'Recovery unavailable',
'recovery_locked_helper':'Check Status to verify the plan. Claim remains disabled until all recovery conditions are satisfied.',
'recovery_available_heading':'Ready to recover', 'recovery_available_helper':'The verified conditions allow this beneficiary Wallet to claim. Review the fee before confirming.',
'recovery_actions_heading':'Recovery actions', 'recovery_package_heading':'Recovery Package',
'package_helper':'Keep the funded plan’s Recovery Package with your backup. It contains public plan data, not private keys.',
'security_heading':'Security', 'device_security_label':'Device PIN, pattern or password', 'device_security_helper':'Required to spend, delete a Wallet or view a stored Recovery Phrase. Android handles authentication.',
'settings_backup_helper':'Keep your Recovery Phrase and Recovery Packages offline. Clearing app data removes the device encryption key.',
'network_heading':'Testnet connection', 'select_plan':'Select plan', 'history_empty':'Received and sent transactions will appear here.',
'plan_empty':'Save an Inheritance Plan or import a Recovery Package to get started.',
'security_prompt_title':'Unlock LegacyBTC', 'security_prompt_description':'Use your device PIN, pattern or password to confirm.',
'plan_summary_ui':'Beneficiary: %1$s\nCovered amount: %2$s BTC\nPeriod: %3$s hours\nTimelock deadline: %4$s\nStatus: %5$s',
'btc_amount':'%1$s BTC', 'recovery_period_value':'Period in hours: %1$s', 'recovery_deadline_value':'Timelock deadline: %1$s',
'recovery_eligibility_value':'Timelock satisfied: %1$s\nThis Wallet can claim: %2$s', 'role_status_value':'%1$s\nStatus: %2$s',
'feedback_error':'Please review\n%1$s', 'inheritance_details_recovery':'Inheritance details', 'beneficiary_heading':'Beneficiary',
}
for node in resources:
    key=node.get('name')
    if key in changes: node.text=changes[key]
existing={n.get('name') for n in resources}
for key,val in {**changes,**new}.items():
    if key not in existing: E.SubElement(resources,'string',name=key).text=val
E.indent(strings,space='    '); strings.write(ROOT/'values/strings.xml',encoding='utf-8',xml_declaration=True)
print('Generated shared dark design resources and 11 Activity layouts; preserved control IDs.')
