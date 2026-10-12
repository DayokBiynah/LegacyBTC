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
