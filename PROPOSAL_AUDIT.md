# LegacyBTC proposal audit

> Historical Testnet-only audit. The current Mainnet implementation and evidence are documented in PROPOSAL_COMPLIANCE.md, README.md, and FINAL_VERIFICATION_STATUS.md. Statements below describe the earlier project state and are not current release limitations.

Audit date: October 1, 2026. This audit uses the approved proposal requirements supplied in the conversation. No separate proposal attachment was available for comparison. The existing Java/XML project, original wallet storage format, wallet listing, deletion, and navigation were extended rather than replaced.

## Seven interfaces

| Proposal interface | Implementation and required information/controls | Verification |
| --- | --- | --- |
| Create/Restore | `CreateWalletActivity`: Create Wallet, Restore Wallet, recovery phrase and optional original BIP-39 passphrase, Continue, Back, generated words/address, backup confirmation. The existing dedicated `RestoreWalletActivity` and welcome routes remain available. | Both activities launch with required controls; wallet recovery, checksum validation, stable identity, duplicate detection, and encrypted saving are tested. |
| Dashboard | `WalletDashboardActivity`: spendable BTC balance, receiving address, recent transactions, inheritance summary, network status, Send, Receive, Inheritance, History, Settings, connection retry, Close Wallet. | Activity/control test; balance and transaction records tested with synthetic funds. |
| Send | `SendBitcoinActivity`: recipient, BTC amount, explicit fee rate, Send, Cancel, reviewed recipient/amount/fee/total, device authentication, transaction ID and relay outcome. | Address/network/amount/fee/balance/dust validation and signed input verification are tested. Live funded relay is pending. |
| Receive | `ReceiveBitcoinActivity`: receiving address, Bitcoin URI QR code, Copy Address, Generate Address, Share, Back; beneficiary public key/address. | Activity/control test and decoding of the actual displayed QR; persisted address generation is tested. Android share-picker interaction remains a manual check. |
| Inheritance Setup | `InheritanceSetupActivity`: owner/beneficiary names, beneficiary public key and matching Testnet address, covered BTC amount, period in hours, saved plan/deadline/status, Add Beneficiary, Set Inheritance Period, Save Plan, Update Plan, Cancel Plan, funding review, Status/Recovery. | Plan validation, draft update, immutable funded plans, funding construction/signatures, and encrypted persistence are tested. |
| Beneficiary/Recovery | `BeneficiaryRecoveryActivity`: plan list/status, amount, beneficiary information, deadline, timelock/recovery eligibility, funding ID, Check Status, Claim/Recover, View Details, owner cancellation, package import/export, historical rescan. | Claims initially disabled; recreation tested. Integration verifies pre-deadline rejection, post-deadline claim, pending-spend and spent states, wrong/offline eligibility, and duplicate-claim prevention. |
| Transaction History | `TransactionHistoryActivity`: scrollable records with type, BTC wallet net amount, ID, status, date/time, details, pending transaction rebroadcast, Back. | Record generation/status tests and activity/control test. Live confirmed-history demonstration remains pending. |

## Inputs, outputs, algorithms, and navigation

| Requirement | Evidence |
| --- | --- |
| Recovery information | bitcoinj deterministic wallet recovery with mnemonic checksum/word-count validation and optional passphrase; same original account derivation. Arbitrary external derivation paths are not supported. |
| Valid addresses and network information | bitcoinj Testnet address parsing and fresh SegWit addresses. Mainnet addresses are rejected. Network/sync status is shown; Settings supports an optional Testnet3 peer host/port. |
| Payments | bitcoinj UTXO selection, unsigned review, fee/change construction, private-key signing, script/input verification, commit and encrypted save before broadcast. Uncertain relay results retain the same pending transaction for retry. |
| Receiving, balances, QR, and history | Addresses remain tracked after rotation; QR contains `bitcoin:<address>`. Spendable balance excludes watched inheritance coins. History derives from the real bitcoinj wallet instead of a separate mock database. |
| Inheritance recovery path | A funded P2SH output enforces owner-signature cancellation or beneficiary signature plus absolute `CHECKLOCKTIMEVERIFY`. Beneficiary spends use a non-final sequence and the deadline as transaction locktime. |
| Condition checking | Claim requires confirmed funding, unspent output, no pending conflicting spend, matching beneficiary private key, synchronized peers, and blockchain median time strictly greater than the deadline. The conditions are checked again at submission. |
| Navigation | Existing welcome Create/Restore/Open routes lead to Dashboard; Dashboard links Send/Receive/Inheritance/History/Settings. Inheritance proceeds through beneficiary configuration, saved draft, reviewed funding, status, and recovery. Back/Cancel controls preserve the selected wallet. |
| Confirmations and errors | Explicit transaction reviews, credential confirmation, recovery-word backup confirmation, deletion warning, validation messages, connection/storage errors, pending transaction IDs, and relay outcomes. |

The inheritance period starts when a draft is saved or updated. It is a calendar timelock, not a death detector or inactivity counter. A draft does not lock coins until funded. Funded terms cannot be edited; the owner must cancel on-chain and fund a replacement. After the deadline the owner retains the cancellation branch, so an owner and beneficiary could race to spend the same output. These limitations are explained in the app and README.

## Required data structures and course concepts

| Requirement | Working implementation |
| --- | --- |
| User, Owner, Beneficiary | Abstract `User` is the actual parent of `Owner` and `Beneficiary`. They override shared role/management/recovery behavior. Their public keys and names participate in real saved plans. |
| BitcoinWallet and BitcoinTransaction | `BitcoinWallet` wraps the existing bitcoinj wallet and supplies addresses, spendable balance, and `ArrayList<BitcoinTransaction>` history. Transaction records expose public details and defensive date copies. |
| InheritancePlan | Private owner, beneficiary, amount, period, deadline, funding reference, and cancellation state; controlled constructors/methods validate changes. |
| BitcoinService | Reusable wallet creation, restoration, address generation, balance, transaction lookup, preparation/signing/sending, persistence, networking, and rescan methods. `InheritanceService` supplies beneficiary/plan/status/recovery methods. |
| Encapsulation | Private domain fields, validated updates, encrypted repositories, and public operations control access to sensitive/domain data. No private keys appear in exported recovery packages. |
| Inheritance and dynamic binding | `InheritancePlan.userFor()` returns a `User` reference. Services and Recovery invoke `canManage()`, `canRecover()`, and role descriptions through that reference; actual subclass behavior determines permission and UI state. |
| Selection | Address/amount/fee/balance decisions, transaction confidence switch, inheritance status transitions, owner/beneficiary permission and timelock decisions. |
| Loops and collections | Saved-wallet listing/duplicate lookup; transaction conversion and record search; plan lists; spending-input searches; median header times; QR pixel rendering. Transactions/plans use Java `ArrayList`. |
| Reusable methods and comments | Required operations are separated across the wallet policy, Bitcoin service, inheritance service, scripts, and repositories. Javadocs and short comments explain signing, saving before relay, script branches, chain-time checks, and dynamic binding. |
| Required stack | Application source is Java, screens are XML, builds use Android Studio/Gradle, Bitcoin logic uses bitcoinj 0.17.1. ZXing renders/decodes QR codes. No Kotlin or Compose application was introduced. |

## Security and test evidence

The verification run passed 13 unit tests and 7 emulator tests, with no failures or skips when the live Testnet test was enabled. Debug and unsigned release APK builds passed. Android lint reported 0 errors and 13 nonfatal warnings concerning dependency/SDK/resource recommendations. Reports are under `app/build/reports/tests/testDebugUnitTest`, `app/build/reports/androidTests/connected`, and `app/build/reports/lint-results-debug.html`. A subsequent run encountered an emulator API-query timeout before tests could start; the emulator recovered and the checks were rerun.

- AES-256-GCM authenticated encryption uses the existing Android Keystore alias/envelope. Atomic files protect wallet/plan replacement. Tests check encryption round trips, corruption, wrong keys, legacy-envelope compatibility, updated-wallet identity, and duplicate detection.
- App data is excluded from cloud/device-transfer backups. Sensitive screens use `FLAG_SECURE`; seed input is excluded from saved state and autofill. Device credential approval is required for spending, wallet deletion, and stored-word disclosure. Secured devices lock wallet screens when the app backgrounds.
- Unit tests use generated wallets/public mnemonic vectors and synthetic Bitcoin transactions. Instrumented tests exercise actual Android Keystore/files, screen lifecycles/controls, QR decoding, and the real services with a simulated chain clock.
- A separate opt-in emulator test has connected to live Bitcoin Testnet peers. This establishes peer connectivity; it does not establish full historical synchronization or successful funded transaction relay/mining.
- Debug/release builds, unit tests, Android lint, and emulator tests are run after feature groups. Final command and report locations are documented in README. The release APK is unsigned.

## Remaining work and verification order

1. On a development device with a PIN, verify foreground/background unlocking, cancelling authentication, seed disclosure, deletion, and payment/funding/claim confirmations. Test rotation during asynchronous setup and submission, process death, accessibility/large fonts, landscape, and night mode. Screen/control tests do not replace this visual/device audit.
2. Complete historical Testnet synchronization. With disposable generated wallets and faucet **Testnet coins only**, demonstrate receive, balance/history confirmation, ordinary send, saved-pending retry, app restart, and restored-wallet replay. No faucet funds were obtained or spent in this session.
3. Run a live funded inheritance scenario with independent owner and beneficiary wallets: fund, export/import, confirm funding, reject early claim, wait for chain median time, claim, confirm spend, reject a second claim. Separately fund and confirm owner cancellation. Current inheritance integration evidence uses synthetic transactions and simulated chain time, not mined Testnet spends.
4. Review the script and SPV security assumptions independently before release. SPV is not a full-node proof of every transaction, the owner retains a competing spend branch, and recovery packages must be backed up separately from wallet seeds. This is not an audited production wallet.
5. Address remaining nonfatal lint warnings as appropriate, sign a course/demo release, and perform final physical-device UI checks. Mainnet compatibility/release remains deferred: this build deliberately has no mainnet switch. Enabling mainnet is not part of the completed development scope and requires the user's later authorization after Testnet and security verification.

All requested feature paths have implementations. Live funded operation, physical-device security/UI verification, and a mainnet release remain incomplete; the project should not be described as fully verified or production ready.
