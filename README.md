# LegacyBTC - A Bitcoin Wallet with Digital Asset Inheritance

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

An explicit read-only Mainnet smoke run verified discovery/handshake with an empty wallet; its XML is preserved in `.verification/mainnet-smoke.xml`. This does not prove historical synchronization. Final live testing must verify complete Mainnet/Testnet replay, incoming confirmed coins, fees/change, authenticated submission and relay, independent beneficiary funding/export/import, early rejection, mature claim, owner cancellation, reorganization behavior, and process restart. Real BTC broadcasts require the user's explicit authorization. Secure physical-device credential cancellation/background handling and release signing remain manual checks. SPV trusts connected peers/miners for relevant transaction information and cannot independently prove the global unspent set like a full node.

API/rule references: [bitcoinj 0.17.1 PeerGroup](https://bitcoinj.org/javadoc/0.17.1/org/bitcoinj/core/PeerGroup.html), [BitcoinURI](https://bitcoinj.org/javadoc/0.17.1/org/bitcoinj/uri/BitcoinURI.html), [BIP-65](https://github.com/bitcoin/bips/blob/master/bip-0065.mediawiki), [BIP-113](https://github.com/bitcoin/bips/blob/master/bip-0113.mediawiki).
