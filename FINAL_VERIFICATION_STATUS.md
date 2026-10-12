# Final verification status - October 11, 2026

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

The release APK remains unsigned, as no release signing credential was provided. Both final lint reports contain 0 errors and 6 nonfatal SDK/dependency-version warnings. Existing compatible dependency versions were retained; deprecation warnings are recorded in Gradle output; passing lint does not imply an independent security audit.

## Actual automated XML evidence

The following table is generated from Gradle XML files, never from assumed success. `Skipped` is intentional for the opt-in Mainnet/Testnet live peer smoke tests in ordinary suites. Passed equals tests minus failures, errors and skips. Check the run ledger to distinguish the latest source run from earlier checks.

| Task/report | Tests | Passed | Failures | Errors | Skipped |
| --- | --- | --- | --- | --- | --- |
| testDebugUnitTest | 18 | 18 | 0 | 0 | 0 |
| testMainnetDebugUnitTest | 18 | 18 | 0 | 0 | 0 |
| testReleaseUnitTest | 18 | 18 | 0 | 0 | 0 |
| app\build\outputs\androidTest-results\connected\debug\TEST-Pixel_4a(AVD) - 17-_app-.xml | 11 | 9 | 0 | 0 | 2 |
| app\build\outputs\androidTest-results\connected\mainnetDebug\TEST-Pixel_4a(AVD) - 17-_app-.xml | 11 | 9 | 0 | 0 | 2 |
| .verification\mainnet-smoke.xml | 1 | 1 | 0 | 0 | 0 |

Unit reports: `app/build/reports/tests/`. Emulator reports: `app/build/reports/androidTests/connected/`. Lint reports: `app/build/reports/lint-results-debug.html` and `lint-results-release.html`. Offline inheritance tests verify cryptographic script validity, early locktime/final-sequence rejection, authorized owner cancellation, watched-output exclusion, strict MTP boundary and meaningful dynamic binding. Instrumented inheritance tests use the actual services, Android encryption, synthetic funding and a simulated chain clock to exercise update, immutable funding, export/import, locked/offline/mature states, signing, persistence, pending spend and duplicate-claim prevention. They do not claim full Bitcoin Core validation or mined-network recovery.

## Files changed and reviewed

All application Java files under `app/src/main/java/com/example/legacybtc/` received reviewed class/operation explanations. Functional changes concentrate in WalletPolicy, WalletStorageManager, BitcoinNetworkManager, BitcoinService, BitcoinPayments, PaymentInput, InheritanceService/Repository, network-dependent Activities, WalletUi, and the dashboard balance layout. `app/build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, and Mainnet string resources select/configure isolated variants. The read-only live MainnetNetworkTest is additionally available. Tests changed or added: WalletPolicyTest, NetworkSettingsTest, NetworkIsolationTest, BitcoinPaymentsTest, PaymentInputTest, TestnetNetworkTest, InheritanceIntegrationTest. `tools/document_source.py` contains reviewed comment text; `tools/finalize_docs.py` regenerates documentation and evidence tables. Existing uncommitted work was retained.

## Security improvements and practical limits

Wallet/plan AES-GCM, Android Keystore, atomic replacement, backup exclusions, FLAG_SECURE, seed saved-state/autofill exclusions and private-key-free packages remain enforced. Network separation, relay ownership checks, authentication for rebroadcast, fee cap, import script/outpoint checks and genesis replay were strengthened. No private credentials were printed or hardcoded. Existing wallet data was not deleted or migrated; tests clean up only disposable fixtures.

SPV/bloom-filter peers provide relevant history and confidence; the app is not a full node and cannot prove the global UTXO set independently. Connected peers do not prove complete historical replay. Seed restoration supports this application's bitcoinj SegWit account, not arbitrary external derivation paths. UI locking does not erase bitcoinj's in-memory keys. Manual fees may be uneconomic or insufficient at the time of relay. The owner retains a competing branch after the deadline.

## Remaining live/device checks

1. On a secure physical phone, verify PIN/pattern/password success/cancel, background unlock, process death during review/save/relay, restore/backup, clipboard/share/document pickers, accessibility/night/landscape and network interruptions. Emulator control/layout checks cover available paths but do not replace these checks.
2. Complete historical Mainnet wallet replay after the verified peer handshake. The empty-wallet Mainnet discovery/handshake smoke test passed and is preserved in .verification/mainnet-smoke.xml. Full tip synchronization and incoming mined funds remain unverified.
3. With separately authorized funds, confirm a receive, ordinary send, fees/change, pending retry, mining/history and restart/restore. Real BTC sending needs explicit user authorization; no real financial operation was performed here.
4. With independent owner and beneficiary wallets, fund, confirm, export/import, reject an early claim, wait for actual chain MTP, submit an authorized claim, confirm it and reject another claim. Separately verify owner cancellation and reorganization/conflict behavior. Synthetic tests cannot prove funded relay/mining.
5. Provide a release signing key and perform the final classroom demonstration/presentation. The proposal's instructor meeting remains Thursday at 1:00 PM.

No required runtime feature is knowingly represented by a timer-only or mock implementation. Remaining evidence limits are stated explicitly rather than marking funded live Bitcoin operation complete.

## Observed run ledger

# Verification run ledger - October 11, 2026

These outcomes were observed from the actual tool/Gradle output. No real BTC transaction was broadcast.

| Run | Command/tasks | Actual result |
| --- | --- | --- |
| Initial | assembleDebug, assembleRelease, testDebugUnitTest, lintDebug, assembleDebugAndroidTest | BUILD SUCCESSFUL, 1m 19s |
| Mainnet task discovery 1 | assembleMainnetDebug, testMainnetDebugUnitTest, testReleaseUnitTest, lintRelease | Failed before task execution: MainnetDebug unit-test task did not exist |
| Mainnet task discovery 2 | assembleMainnetDebug, testReleaseUnitTest, lintRelease | Failed before task execution: Release unit-test task did not exist |
| Corrected Mainnet host-test configuration | assembleMainnetDebug, testMainnetDebugUnitTest, testReleaseUnitTest, lintRelease | BUILD SUCCESSFUL, 22s after enabling host tests for all build types |
| Combined verification | all three APK builds and unit suites; Debug/Release lint; connectedDebugAndroidTest | BUILD SUCCESSFUL, 4m 51s; 15 unit tests per variant; emulator XML 10 tests, 9 passed, 1 intentional live smoke skip |
| Mainnet emulator suite | connectedMainnetDebugAndroidTest -PwalletTestBuildType=mainnetDebug | BUILD SUCCESSFUL, 2m 22s; emulator XML 10 tests, 9 passed, 1 intentional Testnet smoke skip |
| Payment-URI verification | all three APK builds/unit suites; Debug/Release lint; connectedDebugAndroidTest | BUILD SUCCESSFUL, 2m 46s; latest unit counts read from XML in FINAL_VERIFICATION_STATUS |
| Premature smoke launch | Mainnet-only smoke task | Cancelled before device execution so emulator jobs ran sequentially |
| Read-only live Mainnet smoke | connectedMainnetDebugAndroidTest -PwalletTestBuildType=mainnetDebug -Pandroid.testInstrumentationRunnerArguments.class=com.example.legacybtc.MainnetNetworkTest -Pandroid.testInstrumentationRunnerArguments.mainnetSmoke=true | BUILD SUCCESSFUL, 2m 39s; one passing test, zero failures/errors/skips; preserved in mainnet-smoke.xml |
| Final post-review Mainnet run | all three APK builds/unit suites; Debug/Release lint; connectedMainnetDebugAndroidTest -PwalletTestBuildType=mainnetDebug | BUILD SUCCESSFUL, 4m 3s; emulator XML 11 tests, 9 passed, 2 intentional smoke skips; both lint reports 0 errors, 6 version/SDK warnings |
| Final fee/chain-time regression run | all three APK builds/unit suites; Debug/Release lint; connectedDebugAndroidTest | BUILD SUCCESSFUL, 4m 16s; 18 unit tests passed per variant; 11 Testnet emulator tests, 9 passed, 2 intentional smoke skips; includes chain-time regression rejection before submission |
| Final Mainnet regression suite | connectedMainnetDebugAndroidTest -PwalletTestBuildType=mainnetDebug | BUILD SUCCESSFUL, 4m 50s; 11 emulator tests, 9 passed, 2 intentional smoke skips; updated chain-time regression integration passed |

All final post-review runs above completed. Real incoming confirmations, full historical replay, payment relay/mining, funded inheritance relay and secure physical-device authentication remain live checks. `git diff --check` passed (only Git's Windows line-ending notices). Disposable UI images were copied to `.ui-review/final`; all seven final Mainnet screen images under `.ui-review/final/LegacyBTC-ui-review/` were visually inspected with no clipping/overlap found in the reviewed states. No recovery-word images were captured.
