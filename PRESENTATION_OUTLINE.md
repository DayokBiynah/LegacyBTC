# LegacyBTC final presentation content

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
