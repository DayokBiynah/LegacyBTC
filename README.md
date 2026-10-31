# LegacyBTC

LegacyBTC is an Android-based Bitcoin wallet developed in Java, with a focus on secure Bitcoin transactions and time-locked inheritance. The project explores how Bitcoin's scripting capabilities can be used to create inheritance arrangements that allow a designated beneficiary to access funds after a specified period.

The application combines standard wallet functionality with an inheritance mechanism designed to give owners control over their funds while providing a recovery path for beneficiaries.

## Features

- **Bitcoin Wallet:** Create wallets, generate receiving addresses, and send and receive Bitcoin.
- **Bitcoin Network Support:** Supports Bitcoin Mainnet and Testnet.
- **Electrum Integration:** Communicates with Electrum servers to retrieve transaction history, monitor balances, and broadcast transactions.
- **On-Chain Inheritance:** Uses Bitcoin scripts and time-lock conditions to control when inherited funds become spendable.
- **Beneficiary Recovery:** Allows beneficiaries to import inheritance information and claim eligible funds after the required time-lock conditions are satisfied.
- **Owner Cancellation:** Provides a mechanism for owners to cancel funded inheritance arrangements and recover funds.
- **Wallet Security:** Uses wallet encryption and key-management mechanisms to protect wallet information.
- **Transaction Monitoring:** Tracks transaction confirmations and network synchronization status.

## How Bitcoin Inheritance Works

The inheritance system uses Bitcoin's native scripting and time-lock functionality rather than relying solely on an application-level timer.

1. **Create an inheritance plan:** The wallet owner specifies the beneficiary and the time-lock conditions.
2. **Fund the inheritance:** Bitcoin is committed to an output governed by the inheritance script.
3. **Export inheritance information:** The owner generates the information required for the beneficiary to identify and claim the inheritance.
4. **Import and verify:** The beneficiary imports the inheritance information into their wallet.
5. **Claim funds:** Once the Bitcoin network satisfies the required time-lock conditions, the beneficiary can construct and broadcast a claim transaction.
6. **Cancel inheritance:** The owner can use the supported cancellation mechanism to recover funds when the applicable spending conditions permit it.

The time-lock is enforced through Bitcoin transaction validation rules. The Android application provides the interface for creating, monitoring, and spending the relevant outputs.

## Technical Implementation

**Language:** Java  
**Platform:** Android  
**Development Environment:** Android Studio  
**Build System:** Gradle  
**Bitcoin Library:** bitcoinj  
**Network Communication:** Electrum protocol over TCP/TLS

The application uses bitcoinj for Bitcoin wallet operations, transaction construction, key management, and script-related functionality.

Electrum servers provide access to blockchain information, including transaction status, address activity, and transaction broadcasting, without requiring the application to operate a full Bitcoin node.

The inheritance implementation combines Bitcoin script conditions, transaction signing, and beneficiary recovery information to support time-dependent spending.

## Project Structure

- `app/src/main/java/` — Android application logic, wallet operations, networking, and inheritance functionality.
- `app/src/main/res/` — Application layouts, images, and other Android resources.
- `app/src/test/` — Unit tests for wallet functionality and related components.
- `app/src/androidTest/` — Android instrumentation and integration tests.
- `gradle/` — Gradle wrapper configuration.

## Building the Application

1. Clone the repository.
2. Open the project in Android Studio.
3. Allow Gradle to synchronize the dependencies.
4. Configure an Android emulator or connect a physical Android device.
5. Build and run the application.

```bash
git clone https://github.com/DayokBiynah/LegacyBTC.git
```

## Testing and Limitations

LegacyBTC includes tests covering wallet operations, transaction safety, network interactions, and inheritance-related functionality.

Bitcoin inheritance transactions depend on blockchain confirmation and time-lock validation. A time-lock becoming eligible does not automatically transfer funds; the beneficiary must construct and broadcast a valid spending transaction.

The application is an academic software development project and should be treated as experimental. Inheritance and recovery operations require additional security testing and validation before use with significant real Bitcoin holdings.

## Author

**Biynah Dayok**  
Computer Science — LeMoyne-Owen College

## Disclaimer

LegacyBTC is developed for educational and research purposes. It is not intended to serve as a production-ready cryptocurrency custody or inheritance solution.
