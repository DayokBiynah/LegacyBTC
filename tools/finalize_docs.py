"""Write project documentation from reviewed requirements and actual Gradle test XML."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = 'app/src/main/java/com/example/legacybtc/'

README = r'''# LegacyBTC - A Bitcoin Wallet with Digital Asset Inheritance

Biynah Dayok | Fall 2026 COSI 455 | LeMoyne-Owen College | Dr. Wei Ding

I built LegacyBTC as a Java/XML Android Bitcoin wallet with a funded beneficiary recovery path. The approved 20-page project proposal is the specification. The production `release` build uses Bitcoin Mainnet; `mainnetDebug` allows installable Mainnet verification, while ordinary `debug` retains Bitcoin Testnet3 development support. A successful build or synthetic test does not establish successful funded network operation.

## Build and network selection

Use Android Studio's bundled JDK, SDK 36.1, the included Gradle wrapper, and Android API 26 or later. Dependencies include bitcoinj 0.17.1, AndroidX/AppCompat/Material, ZXing 3.5.3, and JUnit 4.13.2. Application source remains Java with XML layouts.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleRelease :app:assembleMainnetDebug :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:testMainnetDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:lintRelease
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:connectedMainnetDebugAndroidTest '-PwalletTestBuildType=mainnetDebug'
```

| Variant | Network | APK | Signing |
| --- | --- | --- | --- |
| release | Mainnet, port 8333 | `app/build/outputs/apk/release/app-release-unsigned.apk` | Configure your release key before distributing |
| mainnetDebug | Mainnet, port 8333 | `app/build/outputs/apk/mainnetDebug/app-mainnetDebug.apk` | Development signature |
| debug | Testnet3, port 18333 | `app/build/outputs/apk/debug/app-debug.apk` | Development signature |

Select the intended variant explicitly in Android Studio. Network is fixed at build time; there is no in-app switch that converts wallet data. Mainnet filenames begin `legacybtc_wallet_mainnet_`; existing Testnet filenames keep `legacybtc_wallet_` followed by an alphanumeric/hyphen identifier. The disjoint filename validators, loaded-wallet network check, peer preferences, per-wallet chain files, and recovery-package network field prevent reuse across networks. Mainnet and Testnet builds share the application ID and encryption-key alias so development installation preserves existing data; each build lists only its own network's wallets. Do not uninstall or clear application data to switch variants.

## Architecture and course concepts

| Class | Responsibility |
| --- | --- |
| `User`, `Owner`, `Beneficiary` | Abstract public identity, inherited validation, overridden role/management/recovery behavior |
| `BitcoinWallet` | Opened wallet filename, addresses, spendable balance, real history collection |
| `BitcoinTransaction` | Public history snapshot: type, wallet-relative amount, ID, time, confidence |
| `BitcoinService` | Single worker, creation/restoration, saving, input signing, network coordination |
| `WalletPolicy`, `BitcoinPayments`, `PaymentInput` | Network, exact satoshis, address/URI, fee, balance and unsigned payment validation |
| `BitcoinNetworkManager` | DNS discovery, PeerGroup, SPV headers, synchronization, broadcast, median time |
| `WalletStorageManager`, `WalletCipher` | Android Keystore AES-GCM and atomic encrypted files |
| `InheritancePlan`, `InheritanceScript` | Encapsulated terms/outpoint, P2SH CLTV script, authorized spend construction |
| `InheritanceService`, `InheritanceRepository` | Drafts, funding, status, owner cancellation, beneficiary recovery, public packages |
| `WalletActivity`, `SelectedWalletActivity`, `LegacyBtcApplication` | Authentication, lifecycle, async UI callbacks, foreground lock |

Private fields and validated constructors implement encapsulation. `Owner` and `Beneficiary` inherit shared identity behavior from abstract `User`. `InheritancePlan.userFor()` returns a parent-class reference; services invoke `canManage`, `canRecover`, and `describeRole` on that reference, and Java dispatches to the actual subclass. These decisions control real plan operations and eligibility. Selection rejects invalid addresses/amounts, branches on confidence and plan states, and checks authorization. Loops process UTXOs, transaction and plan ArrayLists, history searches, QR pixels, and eleven header timestamps. Reusable service methods separate application concerns. Source comments explain these data flows in my developer voice.

## Seven interfaces and wallet workflow

1. Create/Restore: the welcome screen opens the combined setup or direct restore path. Creation encrypts fresh deterministic P2WPKH keys, displays numbered mnemonic words, and requires backup confirmation before Continue. Restoration accepts validated 12/15/18/21/24-word BIP-39 information and the original optional passphrase. Changing the passphrase derives another wallet. External derivation schemes/accounts are not automatically imported.
2. Dashboard: available spendable balance, confirmed/unconfirmed owned UTXOs, current receiving address, recent real transactions, inheritance summary, peers/sync state, Send, Receive, Inheritance, History, Settings, retry and close. Watched inheritance outputs are excluded from ordinary payment balances. Displayed balances can be incomplete until replay finishes.
3. Send: enter an address or paste the `bitcoin:` URI decoded by an external QR reader. An embedded amount can fill an empty amount; a disagreement with manually entered BTC is rejected. Unknown required URI parameters fail. No camera scanner is built into the app. Enter the manual sat/vB fee and review network, recipient, amount, fee and total. Confirm and device authentication gate signing, commitment, saving and relay. Cancel does not submit. Automatic tests never relay real BTC.
4. Receive: address, `bitcoin:<address>` QR, Copy, Generate, Share, Back, and public beneficiary information. Fresh addresses are saved before display and older ones remain tracked. Incoming transactions and their confidence update through bitcoinj and the service listener.
5. Inheritance Setup: owner/beneficiary names, beneficiary compressed public key and matching SegWit address, BTC amount and hours, Add Beneficiary, Set Period, Save, Update, Cancel, and separate funding review.
6. Beneficiary/Recovery: saved/imported plans, amount, key/address, deadline, verified status, eligibility, Check Status, View Details, Claim/Recover, owner cancellation, package import/export, and rescan.
7. History: scrollable type, signed wallet net amount, ID, status and date/time. Pending self-created transactions have authenticated retry of the same signed transaction.

## bitcoinj connectivity and transaction lifecycle

`BitcoinNetwork.MAINNET`/`MainNetParams` select Mainnet genesis and parameters; Testnet uses its own values. `DnsDiscovery` asks the selected network's DNS seeds for candidate node addresses. `PeerGroup` establishes Bitcoin P2P sessions and retries peer connections, attaches the wallet, and downloads SPV block headers and filtered transaction information. This application requests full-history and BIP-37 bloom-filter services (service bits 1 and 4). Not every modern node serves bloom-filter clients; a verified compatible manual peer can be supplied in Settings when seed discovery cannot find one. A TCP port or connected peer alone does not establish complete wallet synchronization. Progress and disconnected states keep spending unavailable until ready.

`Wallet.completeTx` selects spendable UTXOs, computes fee and change, and supplies an unsigned request. BTC is converted to integer `Coin` satoshis without floating-point rounding. The app bounds the fee rate to 1-1000 sat/vB and the absolute fee to 0.01 BTC; a transaction exceeding the safety cap is rejected. Manual rates are not a live fee estimate. After explicit review and Android authentication, `Wallet.signTransaction` signs and each input is checked. The pending transaction is committed and encrypted before `PeerGroup.broadcastTransaction` relays it. Relay acknowledgement is not confirmation. A 45-second timeout retains the transaction for retry; history tracks mining/conflict/reorganization confidence. No development operation broadcasts automatically.

## Funded inheritance and beneficiary recovery

The beneficiary creates a separate wallet, provides only its public receiving address and compressed key, and keeps recovery words/private keys secret. The owner saves an encrypted draft with amount and period (one hour to ten years). The absolute deadline starts at draft save/update. Saving alone reserves no coins. Funding pays the exact covered amount to this P2SH redeem script:

```text
IF
    <owner public key> CHECKSIG
ELSE
    <absolute timestamp deadline> CHECKLOCKTIMEVERIFY DROP
    <beneficiary public key> CHECKSIG
ENDIF
```

This is absolute CLTV (BIP-65), not relative CSV. A beneficiary signature alone cannot bypass the script deadline: spending nLockTime must be at least that timestamp, have the timestamp type, and the input sequence must be non-final. The application uses sequence `0xfffffffe`. Script checks compare transaction fields; BIP-113 transaction finality additionally requires the preceding chain's median time past to be strictly greater than nLockTime. The service computes the median of eleven verified headers and rejects missing/offline/conflicting evidence.

Funding must be confirmed and the exact output unspent with no pending conflicting spend. Beneficiary recovery requires that wallet's actual private key. Recovery pays its current receiving address after deducting a reviewed conservative fee (`400 * sat/vB` satoshis) and requires at least 1,000 satoshis remaining. The exact signature commits to recipient and fee with SIGHASH_ALL. Eligibility and script validity are rechecked on submission, and pending-spend state prevents duplicate claims.

Export the funded public Recovery Package and give a copy to the beneficiary. It includes identities, amount, creation/deadline terms, funding bytes and output index, but no private keys. Beneficiary import validates network and exact funding script, saves the plan, watches from genesis to avoid trusting an imported creation clock, and replays chain history. This can be slow. A mnemonic restores keys but cannot reconstruct custom plan terms; both backups are needed.

The owner may cancel a confirmed funded output through its own signature branch even before or after the deadline. Updating a funded script requires cancellation and a newly funded replacement. Owner spending destroys that exact recovery UTXO; unrelated ordinary payments cannot select watched inheritance outputs. New wallet coins or change do not automatically become covered. Funding with a different transaction ID/output index needs a matching new recovery package. Reorganizations can remove confirmations, change median time, or invalidate a spend and must return eligibility to an unverified/locked/pending state. Both parties can race to spend after the deadline; only one spend can confirm. Network fee demand may delay claims, and low amounts may not cover fees. Bitcoin cannot detect death or inactivity and the application does not automatically transfer funds.

## Security, tests, and remaining verification

AES-256-GCM envelopes use Android Keystore, fresh IVs, authentication tags and AtomicFile replacement. Screens use FLAG_SECURE; seed fields do not use autofill or saved instance state. Cloud/device-transfer backups are disabled. A secured phone locks UI access after backgrounding. Spending, cancellation/claims, deletion, rebroadcast and stored-word disclosure require a device PIN, pattern or password. Private keys remain available in bitcoinj memory while open; the UI lock is not a claim of hardware-isolated transaction signing or heap erasure. No credentials are hardcoded or logged. Recovery packages expose public names/amounts and should be shared privately. Clearing app data loses the device key; keep independent offline recovery information.

Unit tests generate keys/public vectors and synthetic outputs, covering restore identity, wrong networks, BTC precision, fees, signing, balance categories, encryption/tampering, P2SH owner and beneficiary paths, CLTV/non-final sequence enforcement, roles, and QR/URI validation. Emulator tests exercise real Android Keystore/atomic storage, recreation, required controls/navigation, QR decoding, layout sizes, public packages, funding and recovery against a simulated chain clock. See `FINAL_VERIFICATION_STATUS.md` for actual counts and limitations, and `PROPOSAL_COMPLIANCE.md` for individual requirements. Historical `PROPOSAL_AUDIT.md` describes the older Testnet-only state and is superseded by these documents.

Final live testing must verify complete Mainnet/Testnet replay, incoming confirmed coins, fees/change, authenticated submission and relay, independent beneficiary funding/export/import, early rejection, mature claim, owner cancellation, reorganization behavior, and process restart. Real BTC broadcasts require the user's explicit authorization. Secure physical-device credential cancellation/background handling and release signing remain manual checks. SPV trusts connected peers/miners for relevant transaction information and cannot independently prove the global unspent set like a full node.

API/rule references: [bitcoinj 0.17.1 PeerGroup](https://bitcoinj.org/javadoc/0.17.1/org/bitcoinj/core/PeerGroup.html), [BitcoinURI](https://bitcoinj.org/javadoc/0.17.1/org/bitcoinj/uri/BitcoinURI.html), [BIP-65](https://github.com/bitcoin/bips/blob/master/bip-0065.mediawiki), [BIP-113](https://github.com/bitcoin/bips/blob/master/bip-0113.mediawiki).
'''

rows = []
def add(page, requirement, implementation, source, evidence='Source review; build and automated checks listed in FINAL_VERIFICATION_STATUS.md', limitation='Live operation remains subject to final device/network checks', status='Implemented'):
    rows.append((page, requirement, implementation, source, status, evidence, limitation))

add('1-2', 'Course, author, title and self-custodied asset inheritance purpose', 'Java Android wallet with a beneficiary recovery path; README identifies course/author', 'README.md; MainActivity; InheritanceService')
for domain in ['Blockchain technology', 'Bitcoin and cryptocurrency', 'FinTech', 'Digital asset management', 'Digital inheritance', 'Mobile application development']:
    add('2', domain, 'Wallet and funded on-chain inheritance within the Android application', 'BitcoinService; InheritanceService; AndroidManifest.xml')
for req, impl, src in [('Java application logic/OOP/wallet/transactions/validation/inheritance/UI/network', 'All application source is Java', JAVA), ('XML buttons, fields, menus, lists and visual components', 'Shared XML layouts and resources', 'app/src/main/res/'), ('Android Studio development/debug/testing/packaging', 'Existing Android project and wrapper', 'app/build.gradle.kts; README.md'), ('bitcoinj creation/recovery/keys/addresses/transactions/signing/network', 'Installed bitcoinj 0.17.1 APIs', 'app/build.gradle.kts; WalletPolicy; BitcoinService; BitcoinNetworkManager'), ('Gradle configuration/dependency management', 'Version catalog and wrapper', 'gradle/libs.versions.toml; settings.gradle.kts')]:
    add('3-4', req, impl, src)

screens = [
 ('4', 'Create/Restore', 'CreateWalletActivity.onCreate/showWallet; RestoreWalletActivity.onCreate', 'activity_create_wallet.xml; activity_restore_wallet.xml', ['First-use entry', 'Create new wallet', 'Restore existing wallet', 'Enter recovery information', 'Continue to dashboard', 'Create Wallet button', 'Restore Wallet button', 'Recovery input fields', 'Continue button', 'Back button']),
 ('5', 'Dashboard', 'WalletDashboardActivity.onCreate/refreshWallet/link', 'activity_dashboard.xml', ['Wallet balance', 'Wallet address', 'Recent transactions', 'Inheritance status', 'Send button', 'Receive button', 'Inheritance button', 'Transaction History button', 'Settings button']),
 ('5-6', 'Send', 'SendBitcoinActivity.reviewPayment; BitcoinPayments.prepare; BitcoinService.sendBitcoin', 'activity_send.xml', ['Recipient address input', 'BTC amount input', 'Send button', 'Cancel button', 'Transaction confirmation dialog', 'Address validation', 'Amount validation', 'Available balance and required information validation']),
 ('6', 'Receive', 'ReceiveBitcoinActivity.onCreate/refresh; BitcoinService.generateAddress', 'activity_receive.xml', ['Receiving address display', 'QR representing address', 'Copy Address button', 'Generate Address button', 'Share button', 'Back button', 'Other users can scan/copy destination']),
 ('6-7', 'Inheritance Setup', 'InheritanceSetupActivity.onCreate/savePlan/fundPlan; InheritanceService', 'activity_inheritance_setup.xml', ['Beneficiary public key/address input', 'Covered BTC amount input', 'Inheritance period input', 'Beneficiary information display', 'Inheritance period display', 'Current inheritance status', 'Associated Bitcoin display', 'Add Beneficiary button', 'Set Inheritance Period button', 'Save Plan button', 'Update Plan button', 'Cancel Plan button']),
 ('7', 'Beneficiary/Recovery', 'BeneficiaryRecoveryActivity.refresh/reviewRecovery; InheritanceService.prepareRecovery/recoverInheritance', 'activity_recovery.xml', ['Plan status', 'Amount display', 'Timelock status', 'Beneficiary information', 'Condition satisfied display', 'Recovery after conditions satisfied', 'Check Status button', 'Claim/Recover button', 'View Details button']),
 ('8', 'History', 'TransactionHistoryActivity.refresh/showTransaction; BitcoinTransaction', 'activity_history.xml; item_transaction.xml', ['Transaction type', 'BTC amount', 'Transaction ID', 'Status', 'Date/time', 'Scrollable transaction list']),
]
for page, screen, methods, layout, requirements in screens:
    for requirement in requirements:
        add(page, screen + ': ' + requirement, 'Java listener/service/display and XML control', methods + '; ' + layout, 'WalletIntegrationTest; UiRefinementTest; relevant unit/integration tests')
for req in ['Welcome to Create/Restore to Dashboard', 'Dashboard to Send/Receive/Inheritance/History', 'Beneficiary setup to plan to status to recovery', 'Buttons', 'Text fields', 'Text labels', 'Lists', 'Menus/navigation', 'Confirmation dialogs', 'QR codes', 'Back/Cancel navigation']:
    add('8-9', req, 'XML and Activity navigation/controls', 'MainActivity; WalletDashboardActivity.link; InheritanceSetupActivity.openRecovery; app/src/main/res/layout/', 'UiRefinementTest.screensRenderAndNavigationKeepsExistingDestinations')

for req, src in [('Recovery information', 'WalletPolicy.restoreWallet'), ('Recipient addresses', 'WalletPolicy.validateAddress; PaymentInput.parse'), ('Transaction amounts', 'WalletPolicy.parseAmount'), ('Beneficiary public key/address', 'User.parsePublicKey; InheritanceService.addBeneficiary'), ('Inheritance period', 'InheritanceService.setInheritancePeriod'), ('User confirmations', 'WalletActivity.authenticate; SendBitcoinActivity.reviewPayment'), ('QR-code information', 'PaymentInput.parse; ReceiveBitcoinActivity.refresh'), ('Bitcoin network information', 'BitcoinNetworkManager; SelectedWalletActivity')]:
    add('10', 'Input: ' + req, 'Validated values from interface or bitcoinj peer callbacks', src, limitation='QR payload can be pasted from an external reader; no built-in camera scanner' if req == 'QR-code information' else 'Physical-device and live-data checks remain')
for req, src in [('Addresses', 'BitcoinWallet.getAddress; BitcoinService.generateAddress'), ('Balance', 'WalletPolicy.getBalance/getOutputBalance'), ('QR codes', 'ReceiveBitcoinActivity.refresh'), ('History', 'BitcoinWallet.getTransactions'), ('Confirmations', 'BitcoinTransaction'), ('Transaction IDs', 'BitcoinService.sendBitcoin'), ('Transaction status', 'BitcoinTransaction'), ('Plan information', 'InheritanceSetupActivity.displayPlan'), ('Beneficiary information', 'BeneficiaryRecoveryActivity.refresh'), ('Inheritance status', 'InheritanceService.checkInheritanceStatus'), ('Timelock status', 'InheritancePlan.checkInheritanceStatus'), ('Recovery status', 'BeneficiaryRecoveryActivity.refresh'), ('Validation messages', 'WalletActivity.showError'), ('Error messages', 'WalletActivity.work/feedback')]:
    add('10-11', 'Output: ' + req, 'Actual public wallet/plan/confidence data', src)
for req, src in [('Wallet creation and recovery algorithm', 'WalletPolicy.createWallet/restoreWallet'), ('Address generation algorithm', 'BitcoinService.generateAddress'), ('Transaction construction: recipient/amount/available BTC/required information', 'BitcoinPayments.prepare'), ('Transaction signing before network submission', 'BitcoinService.signReviewed/sendBitcoin/broadcast'), ('Invalid address / nonpositive amount / insufficient balance / else continue', 'WalletPolicy; BitcoinPayments.prepare'), ('Retrieve plan / status / timelock / locked else available', 'InheritanceService.checkInheritanceStatus; InheritancePlan.checkInheritanceStatus')]:
    add('11-12', req, 'Reusable Java/bitcoinj operations and guarded selection', src)
for req, src in [('User parent with Owner and Beneficiary subclasses', 'User.java; Owner.java; Beneficiary.java'), ('BitcoinWallet with addresses and transactions', 'BitcoinWallet.java'), ('InheritancePlan owner/beneficiary/amount/period/status', 'InheritancePlan.java'), ('BitcoinTransaction class', 'BitcoinTransaction.java'), ('BitcoinService class', 'BitcoinService.java'), ('ArrayList transaction collections', 'BitcoinWallet.getTransactions'), ('Loop to display/process transaction collection', 'TransactionHistoryActivity.refresh; WalletDashboardActivity.refreshWallet')]:
    add('12-13', req, 'Working domain objects used by screens and services', src)
for req, src in [('Selection if/else-if/else', 'WalletPolicy.parseAmount; BitcoinTransaction; InheritancePlan.checkInheritanceStatus'), ('Selection for address/balance/transaction/inheritance/recovery', 'BitcoinPayments; InheritanceService'), ('Loops for history/search/wallet information/display', 'BitcoinWallet.getTransactions; BitcoinService.findTransaction; OpenWalletActivity; WalletUi'), ('Reusable createWallet/restoreWallet/generateAddress/getBalance/sendBitcoin/validateAddress/getTransactions', 'BitcoinService; WalletPolicy'), ('Reusable addBeneficiary/createInheritancePlan/checkInheritanceStatus/recoverInheritance', 'InheritanceService')]:
    add('13-15', req, 'Meaningful methods and loops on application data', src)
for req, src in [('Private wallet and inheritance state / controlled access', 'BitcoinWallet; InheritancePlan; User'), ('Validated getters/updates and status encapsulation', 'InheritancePlan constructors/attachFunding/cancelDraft; InheritanceService.updatePlan'), ('Shared parent properties and methods', 'User constructor/getters'), ('Owner-specific plan management', 'Owner.canManage; InheritanceService'), ('Beneficiary-specific recovery checks', 'Beneficiary.canRecover; BeneficiaryRecoveryActivity'), ('Common behavior overridden by both roles', 'describeRole/canManage/canRecover in Owner and Beneficiary'), ('Parent-reference runtime method dispatch', 'InheritancePlan.userFor; InheritanceService.prepareRecovery/updatePlan; BeneficiaryRecoveryActivity.refresh')]:
    add('15-18', req, 'Encapsulation, abstraction, inheritance and dynamic binding in actual permissions', src, 'InheritanceScriptTest.statusAndDynamicBindingEnforceRecoveryConditions')

for req, src in [('Testing network before final Mainnet compatibility', 'debug versus release/mainnetDebug; NetworkIsolationTest'), ('Secure wallet information', 'WalletStorageManager; WalletCipher; WalletActivity; backup rules'), ('Connect Bitcoin network', 'BitcoinNetworkManager.start'), ('Receive/QR/balance/sign/send/history', 'BitcoinService; relevant Activities'), ('Beneficiary configuration/timelock/status/recovery', 'InheritanceService; InheritanceScript'), ('Integrate wallet and inheritance', 'BitcoinService.openWallet; InheritanceService'), ('Test creation/restoration/transactions/conditions/recovery', 'unit tests; WalletIntegrationTest; InheritanceIntegrationTest'), ('Improve validation/errors/fix bugs', 'WalletPolicy; PaymentInput; WalletActivity; network/storage guards'), ('Complete available testing/security/UI/documentation', 'FINAL_VERIFICATION_STATUS.md; README.md; source/layouts')]:
    add('18-20', 'Timeline deliverable: ' + req, 'Implemented; milestone order is planning context, not runtime behavior', src)
add('20', 'Prepare project demonstration', 'Reproducible presentation/demo guide with honest synthetic versus live evidence', 'DEMONSTRATION.md', limitation='Student performs final live demonstration')
add('20', 'Prepare final project presentation', 'Presentation content and speaker/demo sequence provided', 'PRESENTATION_OUTLINE.md', status='Prepared content', limitation='Final slide formatting and classroom delivery remain with student')
add('20', 'Weekly instructor meeting Thursday 1:00 PM', 'Recorded course commitment', 'DEMONSTRATION.md', evidence='Proposal page 20', status='Documented', limitation='Attendance is a student scheduling responsibility')

table = '# Proposal compliance - October 11, 2026\n\nI read all 20 pages of the approved proposal before source edits. Each row maps a requirement to working source or a documented course deliverable. Implemented means source and available checks exist; it does not claim live funded Mainnet verification. Source names below resolve under `' + JAVA + '` unless a full path or document is shown.\n\n| Proposal page | Requirement | Implementation | Source / method | Status | Verification evidence | Remaining limitation |\n| --- | --- | --- | --- | --- | --- | --- |\n'
for row in rows:
    table += '| ' + ' | '.join(str(cell).replace('|', '/') for cell in row) + ' |\n'
table += '\nThe proposal deliberately does not specify CLTV versus CSV. This implementation uses genuine funded P2SH absolute CLTV. Its source/tests and limitations are detailed in README and FINAL_VERIFICATION_STATUS. The timeline establishes planned milestones, not proof of historical attendance or live execution.\n'

def reports():
    result = []
    for variant in ['debug', 'mainnetDebug', 'release']:
        task = 'test' + variant[0].upper() + variant[1:] + 'UnitTest'
        files = list((ROOT / 'app/build/test-results' / task).glob('TEST-*.xml'))
        if files:
            totals = [0, 0, 0, 0]
            for file in files:
                suite = ET.parse(file).getroot()
                for i, key in enumerate(['tests', 'failures', 'errors', 'skipped']):
                    totals[i] += int(suite.get(key, '0'))
            result.append((task, *totals))
    for file in list((ROOT / 'app/build/outputs/androidTest-results/connected').rglob('TEST-*.xml')) + list((ROOT / '.verification').glob('mainnet-smoke.xml')):
        suite = ET.parse(file).getroot()
        totals = [int(suite.get(key, '0')) for key in ['tests', 'failures', 'errors', 'skipped']]
        result.append((str(file.relative_to(ROOT)), *totals))
    return result

status = '''# Final verification status - October 11, 2026

I implemented production Mainnet selection while preserving Testnet and existing wallet data. The app is ready for Android Studio/device validation within the evidence below; funded live operation is still unverified. No real BTC was broadcast or spent during development.

## Completed and corrected

- Mainnet release and installable mainnetDebug; Testnet debug retained. Correct genesis/parameters, DNS/peer sessions, separate preference namespaces and per-wallet chain files.
- Disjoint wallet filename validation and loaded-network validation; legacy Testnet envelopes remain readable. Public recovery packages identify and enforce the selected network.
- Correct network labels in review/share/copy/export and Mainnet resources. Mainnet saved-wallet names use their UUID fragment rather than a repeated network prefix.
- Spendable, confirmed and unconfirmed owned-output balances. Watched inheritance outputs remain outside ordinary payment selection.
- Actual wallet history, exact satoshi amounts, fee/change/UTXO selection, signing/input checks, explicit review and credential authentication, pending save before relay, timeout and same-ID retry.
- Authenticated pending rebroadcast, selected-wallet saved/self-created transaction relay guard, absolute 0.01 BTC fee cap plus rate bounds.
- Genuine funded P2SH CLTV owner/beneficiary branches, immutable funded terms, status/chain-MTP checks, authenticated cancellation/claim, checked scripts and pending-spend duplicate prevention.
- Recovery import checks duplicate outpoint/script consistency and watches from genesis instead of trusting an imported creation clock.
- Externally decoded Bitcoin QR URI input, exact embedded amounts and mismatch rejection; built-in camera scanning is not required/provided.
- Expanded first-person class/method JavaDoc and technical block explanations across Java sources; detailed Bitcoin/API data flow comments in policy, scripts, services and Android workflows.
- README, requirement-by-requirement compliance, this evidence report, demo guide and presentation outline.

## Builds and command outcomes

The initial Debug/Testnet APK, unsigned Release/Mainnet APK, Debug unit tests, Debug lint and test APK assembly succeeded. The first two Mainnet test attempts failed at task selection because AGP only enabled unit tests for the instrumented build type. Setting `android.onlyEnableUnitTestForTheTestedBuildType=false` created MainnetDebug and Release unit-test tasks; their subsequent build/test/lint run succeeded. A later combined run built all three variants, passed all three unit suites and both lint tasks, and completed Testnet emulator tests without failures; live smoke testing was skipped by design. Final commands after the last source changes are recorded in the appended run ledger.

The release APK remains unsigned, as no release signing credential was provided. Nonfatal lint/deprecation warnings are recorded in the generated reports; passing lint does not imply an independent security audit.

## Actual automated XML evidence

The following table is generated from Gradle XML files, never from assumed success. `Skipped` is intentional for the opt-in live peer smoke test. Passed equals tests minus failures, errors and skips. Check the run ledger to distinguish the latest source run from earlier checks.

| Task/report | Tests | Passed | Failures | Errors | Skipped |
| --- | --- | --- | --- | --- | --- |
'''
for task, tests, failures, errors, skipped in reports():
    status += f'| {task} | {tests} | {tests-failures-errors-skipped} | {failures} | {errors} | {skipped} |\n'
status += '''
Unit reports: `app/build/reports/tests/`. Emulator reports: `app/build/reports/androidTests/connected/`. Lint reports: `app/build/reports/lint-results-debug.html` and `lint-results-release.html`. Offline inheritance tests verify cryptographic script validity, early locktime/final-sequence rejection, authorized owner cancellation, watched-output exclusion, strict MTP boundary and meaningful dynamic binding. Instrumented inheritance tests use the actual services, Android encryption, synthetic funding and a simulated chain clock to exercise update, immutable funding, export/import, locked/offline/mature states, signing, persistence, pending spend and duplicate-claim prevention. They do not claim full Bitcoin Core validation or mined-network recovery.

## Files changed and reviewed

All application Java files under `app/src/main/java/com/example/legacybtc/` received reviewed class/operation explanations. Functional changes concentrate in WalletPolicy, WalletStorageManager, BitcoinNetworkManager, BitcoinService, BitcoinPayments, PaymentInput, InheritanceService/Repository, network-dependent Activities, WalletUi, and the dashboard balance layout. `app/build.gradle.kts`, `gradle.properties`, and Mainnet string resources select/configure isolated variants. Tests changed or added: WalletPolicyTest, NetworkSettingsTest, NetworkIsolationTest, BitcoinPaymentsTest, PaymentInputTest, TestnetNetworkTest, InheritanceIntegrationTest. `tools/document_source.py` contains reviewed comment text; `tools/finalize_docs.py` regenerates documentation and evidence tables. Existing uncommitted work was retained.

## Security improvements and practical limits

Wallet/plan AES-GCM, Android Keystore, atomic replacement, backup exclusions, FLAG_SECURE, seed saved-state/autofill exclusions and private-key-free packages remain enforced. Network separation, relay ownership checks, authentication for rebroadcast, fee cap, import script/outpoint checks and genesis replay were strengthened. No private credentials were printed or hardcoded. Existing wallet data was not deleted or migrated; tests clean up only disposable fixtures.

SPV/bloom-filter peers provide relevant history and confidence; the app is not a full node and cannot prove the global UTXO set independently. Connected peers do not prove complete historical replay. Seed restoration supports this application's bitcoinj SegWit account, not arbitrary external derivation paths. UI locking does not erase bitcoinj's in-memory keys. Manual fees may be uneconomic or insufficient at the time of relay. The owner retains a competing branch after the deadline.

## Remaining live/device checks

1. On a secure physical phone, verify PIN/pattern/password success/cancel, background unlock, process death during review/save/relay, restore/backup, clipboard/share/document pickers, accessibility/night/landscape and network interruptions. Emulator control/layout checks cover available paths but do not replace these checks.
2. Establish a real Mainnet bloom-compatible peer session and complete historical wallet replay. No current run claims live Mainnet discovery, tip synchronization, or incoming mined funds.
3. With separately authorized funds, confirm a receive, ordinary send, fees/change, pending retry, mining/history and restart/restore. Real BTC sending needs explicit user authorization; no real financial operation was performed here.
4. With independent owner and beneficiary wallets, fund, confirm, export/import, reject an early claim, wait for actual chain MTP, submit an authorized claim, confirm it and reject another claim. Separately verify owner cancellation and reorganization/conflict behavior. Synthetic tests cannot prove funded relay/mining.
5. Provide a release signing key and perform the final classroom demonstration/presentation. The proposal's instructor meeting remains Thursday at 1:00 PM.

No required runtime feature is knowingly represented by a timer-only or mock implementation. Remaining evidence limits are stated explicitly rather than marking funded live Bitcoin operation complete.
'''

DEMO = '''# LegacyBTC demonstration guide

Biynah Dayok - Fall 2026 COSI 455 - Dr. Wei Ding - LeMoyne-Owen College.
Instructor meeting: Thursday, 1:00 PM, as specified on proposal page 20.

1. Select Debug/Testnet for a classroom demonstration unless real BTC use has separately been authorized. Explain the build labels and Mainnet release compatibility.
2. Create a disposable wallet, privately record recovery words, confirm backup and Continue. Explain deterministic keys and encrypted storage; never project actual recovery words for a funded wallet.
3. Show Dashboard balance categories, peer synchronization, receiving address, QR, Copy/Generate/Share and actual History. Label any synthetic fixtures clearly.
4. Demonstrate invalid address/amount rejection and a payment review. Cancel the dialog. A funded send/relay is a separate authorized live step.
5. Create an independent beneficiary wallet and copy only its public key/address. Configure amount and period, save/update a draft, and show that saving does not lock coins.
6. Explain the two P2SH CLTV branches, absolute deadline, non-final sequence, chain MTP, owner cancellation and absence of death detection. Show the offline script tests as evidence of early rejection, not as a mined transaction.
7. Show public package export/import and locked status. A mature funded recovery must use independent wallets, actual confirmations and separately authorized test funds; never change the phone clock to claim maturity.
8. Demonstrate User parent-reference dynamic binding in actual permission checks and ArrayList/loop history rendering.
9. Present FINAL_VERIFICATION_STATUS and PROPOSAL_COMPLIANCE. Distinguish source/build/cryptographic/emulator evidence from unperformed live funded checks. End with backup and security limitations.
'''
PRESENTATION = '''# LegacyBTC final presentation content

1. Title: LegacyBTC - A Bitcoin Wallet with Digital Asset Inheritance. Biynah Dayok, Fall 2026 COSI 455, LeMoyne-Owen College, Dr. Wei Ding.
2. Problem: self-custodied Bitcoin can become inaccessible when keys are lost; designated recovery needs enforceable on-chain authorization.
3. Technologies: Java, XML, Android Studio/Gradle, bitcoinj 0.17.1, Android Keystore, ZXing.
4. Seven interfaces: show setup, dashboard, send, receive, inheritance setup, beneficiary recovery and history. Use only disposable public screenshots.
5. Architecture: Activities to one BitcoinService worker; wallet/network/storage modules; inheritance domain/service/repository/script.
6. Wallet lifecycle: deterministic mnemonic creation/restoration, network separation, encrypted atomic storage, public address derivation and SPV synchronization.
7. Transactions: exact satoshis, UTXOs, fee/change, unsigned review, authentication, signing, pending save, peer relay and mining confidence.
8. Inheritance: owner/beneficiary public keys, actual P2SH funded output, absolute CLTV deadline, beneficiary signature/non-final sequence/MTP, owner cancellation. No death detector.
9. Recovery package: exact public funding outpoint and script terms, independent private key, import/replay, fee deduction and duplicate-spend prevention.
10. OOP: encapsulation/private fields; abstract User; inherited identity; overridden role methods; runtime dispatch through User references; useful collections, loops and reusable methods.
11. Verification: quote current XML counts from FINAL_VERIFICATION_STATUS. Explain generated fixtures, script checks and emulator evidence without claiming mined live Mainnet tests.
12. Limitations/final demonstration: SPV assumptions, fee demand, reorganizations, owner/beneficiary race, required backups, physical-device security and separately authorized live funding. Follow DEMONSTRATION.md.

This outline provides final presentation content; classroom slide styling/delivery remain separate student activities.
'''

for name, content in [('README.md', README), ('PROPOSAL_COMPLIANCE.md', table), ('FINAL_VERIFICATION_STATUS.md', status), ('DEMONSTRATION.md', DEMO), ('PRESENTATION_OUTLINE.md', PRESENTATION)]:
    if name == 'FINAL_VERIFICATION_STATUS.md':
        content = content.replace('No current run claims live Mainnet discovery, tip synchronization, or incoming mined funds.',
            'The empty-wallet Mainnet discovery/handshake smoke test passed and is preserved in .verification/mainnet-smoke.xml. Full tip synchronization and incoming mined funds remain unverified.')
        content = content.replace('Establish a real Mainnet bloom-compatible peer session and complete historical wallet replay.',
            'Complete historical Mainnet wallet replay after the verified peer handshake.')
        content = content.replace('`Skipped` is intentional for the opt-in live peer smoke test.',
            '`Skipped` is intentional for the opt-in Mainnet/Testnet live peer smoke tests in ordinary suites.')
        content = content.replace('Tests changed or added:', 'The read-only live MainnetNetworkTest is additionally available. Tests changed or added:')
        content = content.replace('and Mainnet string resources select/configure isolated variants.',
            '`gradle/libs.versions.toml`, and Mainnet string resources select/configure isolated variants.')
        content = content.replace('Nonfatal lint/deprecation warnings are recorded in the generated reports;',
            'Both final lint reports contain 0 errors and 6 nonfatal SDK/dependency-version warnings. Existing compatible dependency versions were retained; deprecation warnings are recorded in Gradle output;')
        content += '\n## Observed run ledger\n\n' + (ROOT / '.verification/RUN_LOG.md').read_text(encoding='utf-8')
    if name == 'README.md':
        content = content.replace('Final live testing must verify complete Mainnet/Testnet replay,',
            'An explicit read-only Mainnet smoke run verified discovery/handshake with an empty wallet; its XML is preserved in `.verification/mainnet-smoke.xml`. This does not prove historical synchronization. Final live testing must verify complete Mainnet/Testnet replay,')
    (ROOT / name).write_text(content, encoding='utf-8')
print(f'Wrote five documents with {len(rows)} individual proposal requirement rows and actual XML evidence.')
