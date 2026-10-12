# LegacyBTC UI refinement

This pass refines the existing Java/XML application. The proposal's seven interfaces remain present, alongside the welcome, saved-wallet, dedicated restore, and Settings screens.

## Visual resources

- Shared charcoal background/surfaces, amber primary accent, readable neutral text, and explicit success/warning/error colors in `colors.xml`.
- Shared spacing, typography, button sizes, corner radii, input fields, cards, address components, feedback, and dark dialog styling in `dimens.xml`, `styles.xml`, themes, color selectors, and drawable backgrounds.
- Primary, secondary, quiet, and destructive button styles. The Claim button's disabled state is muted; the existing recovery rules still control enablement.
- A Bitcoin-style launcher mark replaces the stock Android icon. The dark theme is consistent in both system theme modes.

## Screen changes

| Screen | Refinement |
| --- | --- |
| Welcome | Clear create/restore choices, separate saved-wallet section, concise Testnet notice. |
| Create/Restore | Separate paths and cards. Creating switches to a numbered Recovery Phrase backup step; restoring does not redisplay the supplied phrase. Continue appears after success. The required backup confirmation remains enforced. |
| Dedicated Restore | Persistent field labels, concise instructions, success state, hidden completed form. |
| Saved Wallets | Identifier/address cards with separate Open and secondary destructive Delete actions; full addresses wrap inside their own component. |
| Dashboard | Balance and Send/Receive form the primary card. Receiving address, navigation, recent activity, and inheritance information are separated. Public-address copying is available directly. |
| Send | Labeled recipient/amount/fee fields, shared form styling, clear primary action, adjacent validation feedback, secondary Cancel. Existing transaction review values and signing/broadcast calls remain unchanged. |
| Receive | High-contrast QR presentation, dedicated full address, grouped copy/generate/share actions, separate public beneficiary information. |
| History | Transaction cards prioritize type/amount, then status/date and the full selectable ID. Explicit View Details retains pending-transaction retry. Clean empty state. |
| Inheritance Setup | Beneficiary, inheritance details, and plan status/funding sections. Saved plans use reusable cards. Calendar-timelock limitations remain visible. |
| Recovery | Status/eligibility first, separate amount/deadline and beneficiary/address/key/funding information, grouped actions and Recovery Package tools. No recovery condition was bypassed. |
| Settings | Security, backup, and network sections with concise explanations. Android still supplies the PIN/pattern/password authentication UI. |

## Java changes and preserved behavior

Java changes are limited to UI Activities and the new `WalletUi` presentation helper: inflating cards, formatting status information, setup-state visibility, hiding empty feedback, public-address copying, and clearer authentication prompt text. Existing event destinations, service calls, and controls are retained. No wallet derivation, encryption, key handling, networking, signing, broadcasting, timelock, or domain/OOP implementation was rewritten.

Before/after SHA-256 comparison found **zero changes to existing backend/domain Java files**. Comparing every original layout ID found **zero removed IDs**. No dependency or Gradle configuration change was needed for the UI pass. Mainnet remains disabled.

## Verification and limits

Final verification passed: debug and unsigned release builds, 13 unit tests, and all 10 emulator tests (zero failures and zero skips, including the opt-in live Testnet peer test). Lint reports zero errors and eight nonfatal SDK/dependency/version-catalog recommendations. The existing dependencies were intentionally retained. Reports are in `app/build/reports/`.

The emulator UI tests exercise creation/backup/recreation, restoration without phrase redisclosure, welcome and Dashboard destinations, disabled recovery, contrast, touch targets, and layout width bounds. Actual Android views are rendered to fixture screenshots for visual review. A separate layout test measures all eleven screens at a compact 320 × 480 dp size with 150% and 200% font scaling. UI fixtures use a test-only offline network; the separate opt-in Testnet connectivity test exercises real peers. No real Bitcoin or personal seed was used.

The final screenshot set is under `.ui-review/screenshots-final/`, with a contact sheet at `.ui-review/contact-sheet-final.png`. All eleven default screen renders, lower content sections, and the compact large-font examples were visually inspected. These local review artifacts show disposable Testnet fixtures and omit recovery words; they are not product image assets. Android tests also publish these fixture images to the emulator's `Pictures/LegacyBTC-ui-review` folder so they survive test APK cleanup.

Manual verification still needed: your physical phone and its keyboard/cutouts, actual landscape Activity transitions, TalkBack, Android's credential dialog and biometric/device-lock variations, share/document pickers, and funded transaction/recovery confirmation dialogs. Layout measurements and default emulator screenshots do not establish those behaviors. Live funded Testnet transaction and inheritance verification remains outstanding from the prior proposal audit.
