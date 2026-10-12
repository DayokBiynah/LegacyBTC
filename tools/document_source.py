"""Apply reviewed explanations to LegacyBTC's Java sources; never reads wallet data."""
from pathlib import Path
import re
import textwrap

ROOT = Path(__file__).resolve().parents[1]
CLASSES = {
    'BitcoinService': 'I coordinate the opened wallet, encrypted persistence, transaction signing, and peer session. A single ScheduledThreadPoolExecutor serializes mutations so two screens cannot spend or save concurrently. Activities submit operations and display their CompletableFuture results on the Android main thread. bitcoinj owns the keys, UTXOs, and transaction objects; I keep application storage and lifecycle handling here.',
    'BitcoinNetworkManager': 'I manage one wallet\'s SPV connection to the selected Bitcoin network. bitcoinj verifies block headers and discovers relevant transactions using peers and bloom filters; it is not a full node. Network parameters choose genesis, consensus configuration, default port, and DNS seeds. Each wallet has a separate chain file so opening another wallet cannot skip its history. Mainnet and Testnet use distinct configuration and filenames.',
    'InheritanceService': 'I connect the owner and beneficiary interfaces to real funded Bitcoin scripts. Saving a draft records public terms but reserves no coins. Funding creates the P2SH output; recovery and cancellation spend that exact outpoint with the appropriate key. I use User references for runtime dispatch of Owner and Beneficiary permissions, then check chain confidence and median time again before committing a reviewed spend.',
    'InheritanceRepository': 'I store each wallet\'s plans in an encrypted JSON array and export only public recovery information. The package includes the exact funding transaction, output index, identities, amount, and deadline needed to reconstruct the redeem script. A wallet mnemonic does not recreate these custom terms. I validate the package network and funding output before accepting it; imported information is not proof of blockchain confirmation.',
    'InheritancePlan': 'I encapsulate one inheritance arrangement with private identities, satoshi amount, creation time, period, absolute deadline, and funding outpoint. Constructors reject unsafe terms. Once funded, the output script is immutable and updates require an on-chain owner cancellation followed by a replacement. Status is derived from current verification evidence rather than a phone countdown. No field contains a private key.',
    'WalletStorageManager': 'I preserve multiple bitcoinj wallets as authenticated ciphertext in Android private storage. Android Keystore supplies the device-bound AES key and AtomicFile retains the previous file if replacement fails. Mainnet uses a distinct filename prefix; the existing Testnet format remains readable without converting any wallet. Plain serialized bytes are cleared after loading or saving, although Java and bitcoinj may retain their own in-memory copies.',
    'WalletCipher': 'I use Java\'s standard AES/GCM/NoPadding implementation to protect serialized wallet and plan bytes. The envelope stores a twelve-byte IV followed by ciphertext and the authentication tag. Encryption generates a fresh IV through the provider. Decryption rejects tampering or a wrong key instead of returning untrusted plaintext. I preserve the existing format and do not implement custom cryptography.',
    'User': 'I define the shared public identity and abstract permissions for an owner or beneficiary. The constructor validates the name, compressed secp256k1 public key, network address, and their P2WPKH relationship. Private fields prevent arbitrary replacement of identity data. The abstract methods provide the interface through which services use dynamic binding; authorization still requires the actual wallet signing key.',
    'Owner': 'I specialize User for the person configuring and cancelling an inheritance plan. The inherited constructor validates shared identity data. My overridden canManage compares the owner public key with the plan; canRecover denies the beneficiary operation. When a service holds this object through a User reference, Java selects these overrides at runtime.',
    'Beneficiary': 'I specialize User for the designated recipient of a funded inheritance output. My overridden canRecover accepts only an AVAILABLE plan with the matching beneficiary public key. Owner management is denied. The service also requires the wallet private key and verified funding before signing, so public identity data alone cannot authorize a claim.',
    'BitcoinWallet': 'I associate an encrypted storage filename with the opened bitcoinj Wallet and expose public address, spendable balance, and transaction history. The constructor enforces filename and network isolation. I loop over bitcoinj\'s real transactions to create ArrayList history records rather than inventing a separate payment database. Sensitive mutations remain in BitcoinService.',
    'BitcoinTransaction': 'I convert a bitcoinj Transaction into a public history record for the XML list. The wallet-relative Coin value is negative for money sent and positive for money received; it may include fees and internal transfers. TransactionConfidence supplies pending, confirmed, conflicting, or invalidated status. The transaction ID identifies the serialized transaction, not proof of relay or mining. Date getters return defensive copies.',
    'WalletActivity': 'I provide the common Android screen behavior: FLAG_SECURE, device-credential authentication, system/keyboard insets, errors, navigation, and asynchronous operations. Financial actions run only after their reviewed callback passes Android authentication. I retain no private keys in Intent or Bundle data. Background results return to the main thread and are ignored after this Activity finishes.',
    'SelectedWalletActivity': 'I reopen the selected encrypted wallet from a filename in the Intent after process recreation. A Handler refreshes public screen information while resumed and stops callbacks while paused. Child screens override refresh for their own content. The shared service serializes wallet changes; connection readiness controls spending and verified recovery.',
    'LegacyBtcApplication': 'I track visible Activities to distinguish internal navigation from leaving the app. When the last screen stops outside configuration changes or credential authentication, I clear the unlocked flag and request encrypted persistence. Returning to a secured device requires authentication again. This lock hides UI access; it does not claim to erase all bitcoinj keys from the Java heap.',
    'CreateWalletActivity': 'I implement the combined Create/Restore screen using Java and XML. Buttons capture recovery Strings or request a fresh wallet on the service worker. New wallets display numbered recovery words and require a backup checkbox before Continue. Restored wallets clear inputs instead of redisplaying the phrase. Saved instance state contains a filename and public workflow flags, never mnemonic words.',
    'RestoreWalletActivity': 'I restore this application\'s deterministic SegWit account using the ordered recovery phrase and optional original BIP-39 passphrase. The worker validates and encrypts the wallet, while callbacks clear sensitive fields and enable Continue. Duplicate detection reuses a saved account instead of overwriting its history. Network replay occurs after opening the dashboard.',
    'WalletDashboardActivity': 'I display the opened wallet\'s spendable, confirmed, and unconfirmed balances, public receiving address, recent transactions, inheritance summary, and peer status. Navigation passes only the wallet filename. A resumed Handler refreshes public data; signing, synchronization startup, and encrypted storage run through the service worker. Status text does not claim confirmation merely because a transaction was relayed.',
    'SendBitcoinActivity': 'I collect the recipient address, exact decimal BTC amount, and manual fee rate. The service prepares an unsigned transaction and I display its destination, amount, fee, total, and network for review. Confirm invokes Android credential authentication before signing and saving. A submitted transaction stays disabled after an uncertain relay result so retry uses its existing ID rather than paying twice.',
    'ReceiveBitcoinActivity': 'I show a public receiving address and a QR code containing the standard bitcoin: URI. ZXing encodes the URI on the worker and a nested pixel loop produces an Android Bitmap for the ImageView. Copy and Share expose only public information. Generating an address persists new keys before display; previously generated addresses remain in the same wallet.',
    'InheritanceSetupActivity': 'I collect the owner name, beneficiary public key and matching address, covered BTC amount, and period in hours. Add Beneficiary and Set Period validate fields; Save and Update persist draft terms. Funding is a distinct reviewed and authenticated Bitcoin payment. Funded scripts cannot be edited locally. Cancel routes funded plans to an owner-signed on-chain cancellation.',
    'BeneficiaryRecoveryActivity': 'I display funded plan terms and derive eligibility from the verified chain and the opened wallet role. Import and Export use Android document pickers for public JSON recovery packages. Claims start disabled and remain unavailable before the deadline, while offline, or without the beneficiary key. A reviewed claim or cancellation requires device authentication before commitment and relay.',
    'TransactionHistoryActivity': 'I render real wallet transactions in a scrollable XML container with type, amount, ID, date, and confidence status. A rendering signature avoids rebuilding unchanged rows. Details offer authenticated rebroadcast only for a locally created pending transaction, preserving its original ID and signed contents instead of constructing a second payment.',
    'SettingsActivity': 'I show security information and let the user configure an optional peer hostname and port for this build\'s network. Empty host selects bitcoinj DNS discovery. Peer preferences are isolated by network and validated before restarting synchronization. Recovery-word disclosure requires Android credential authentication and a screenshot-protected dialog.',
    'OpenWalletActivity': 'I list only encrypted wallets belonging to this build\'s network, decrypt public receiving addresses on the worker, and show unreadable entries without destroying them. Opening passes the selected filename to the dashboard. Local deletion requires a warning and device authentication; deleting a file does not cancel a Bitcoin inheritance output.',
    'MainActivity': 'I provide the public welcome navigation to Create/Restore and Open Existing Wallet. These buttons create Android Intents to the appropriate Activities and do not load private wallet information. The common WalletActivity handles screenshot protection and subsequent secured screens.',
    'NetworkSettings': 'I validate public peer configuration as a hostname or IPv4 address and an integer TCP port. URLs, spaces, malformed names, and the other Bitcoin network\'s default port are rejected. The port alone does not establish network identity; bitcoinj\'s selected parameters also determine the handshake and chain rules.',
    'WalletReplay': 'I rebuild confirmed transaction history when newly watched inheritance outputs may predate the saved chain. I retain pending transactions in an ArrayList, call Wallet.reset to reset chain-derived history, then reinsert those pending objects. Deterministic keys remain available. The service persists the replay state before restarting synchronization.',
    'WalletUi': 'I centralize XML row inflation and formatting so lists share a consistent design. Helpers receive public wallet, transaction, or plan data and return Views to their parent containers. Action Runnables link rows to existing Activity operations. Formatting and color communicate state without deciding signing permission.',
    'Hex': 'I convert public keys and serialized transactions between byte arrays and hexadecimal Strings for recovery packages. Each byte occupies two hex digits. Decoding validates even length and hexadecimal characters before parsing pairs; malformed input becomes a validation error rather than partially decoded data.',
}

METHODS = {
 'submit': 'I receive an Operation callback, queue it on the single worker, and propagate the bitcoinj Context onto that thread. The CompletableFuture carries its returned value to the Activity or completes exceptionally when the operation fails. This keeps disk access and signing off the Android main thread.',
 'openWallet': 'I receive the selected encrypted filename from navigation. I close and save a different opened wallet first, load this wallet, restore watched inheritance addresses, and recover locally saved funding when necessary. A wallet change listener schedules future encrypted saves. The returned BitcoinWallet becomes the shared selection; loading errors leave the screen unable to spend.',
 'closeWallet': 'I stop peers before detaching the wallet listener and persisting the current wallet. Only a successful save clears the selected reference, so a storage failure is not silently discarded. Calling this with no selection has no effect.',
 'connect': 'I pass the opened wallet and its filename to the network manager. Network startup, header synchronization, and peer discovery run on the service worker. A startup exception reaches the interface; financial actions remain unavailable until synchronization and a peer connection are verified.',
 'preparePayment': 'I check storage and synchronization readiness, then pass recipient, BTC amount, and fee-rate Strings to BitcoinPayments.prepare. The returned unsigned SendRequest contains selected inputs, change, and the fee shown to the user. I persist newly derived change keys before displaying the review. No transaction is committed here.',
 'signReviewed': 'I receive the exact SendRequest retained by the confirmation dialog. I recheck readiness, fee bounds, and every connected input\'s availability because balances may change during authentication. I enable signing, call Wallet.signTransaction, and verify each input. A failed check stops commitment; the returned signed Transaction still needs persistence before relay.',
 'persist': 'I serialize the currently selected bitcoinj wallet through WalletStorageManager.updateWallet. Success clears the storage error; failure records a visible error and propagates the exception so further financial operations stop rather than broadcasting an unsaved transaction.',
 'scheduleSave': 'I coalesce wallet change events into one delayed encrypted save on the worker. The synchronized scheduling flag prevents redundant queued writes. bitcoinj Context is propagated before storage operations. Failure remains recorded in storageError for wallet screens and requireReady to report.',
 'generateAddress': 'I ask Wallet.freshReceiveAddress for the next deterministic receiving address, then persist the advanced keychain before returning its public String to the Receive screen. Earlier addresses remain tracked. A save failure prevents displaying a newly generated address as safely persisted.',
 'start': 'I receive an Android Context, a wallet, and optionally its chain filename. I enforce the build network, stop the previous session, reconcile the wallet\'s saved height with its SPV header store, and apply bundled checkpoints only before the earliest known keys. DnsDiscovery obtains candidate peers from network DNS seeds unless a manual host is configured. PeerGroup starts TCP Bitcoin sessions, attaches the wallet for relevant transaction updates, and downloads headers and filtered blocks. Progress callbacks update visible state; exceptions close resources and propagate to the caller.',
 'stop': 'I clear readiness before asking PeerGroup.stopAsync to stop its network threads. I wait with a finite timeout before closing the SPVBlockStore, because active peers may still use it. A stopped session cannot authorize a spend. Shutdown exceptions propagate rather than pretending the file is safely closed.',
 'broadcast': 'I receive a signed, saved Transaction from an authenticated submission or retry. The service checks readiness and selected-wallet ownership; PeerGroup.broadcastTransaction sends it to connected peers and returns an asynchronous relay result. Relay is not mining confirmation. Timeout or peer failure leaves the same pending transaction available in History.',
 'medianTimePast': 'I retrieve the best verified header and loop backward through eleven SPV headers. After sorting their epoch-second timestamps, I return the middle value required by BIP-113. Missing headers or an unsynchronized session prevent eligibility. I do not substitute the phone clock for blockchain finality.',
 'rescan': 'I stop peers and rebuild the per-wallet chain history so a newly watched inheritance address can discover older funding and spends. The service resets chain-derived wallet data while preserving keys and pending transactions and persists that reset. Recovery remains unavailable until the resulting synchronization completes.',
 'requireReady': 'I reject a missing selected wallet, recorded storage failure, or an unsynchronized/disconnected peer session. Signing and recovery call this reusable guard before mutating transactions. A successful check provides readiness at that moment; later operations still recheck eligibility where required.',
 'addBeneficiary': 'I receive name, compressed public-key hex, and receiving-address Strings from the setup fields and construct a Beneficiary. The User constructor verifies key validity, network, and address/key correspondence. The returned public identity is used in draft creation and contains no signing secret.',
 'setInheritancePeriod': 'I parse whole hours from the setup field into a long, enforce one hour through ten years, and convert to seconds for the plan constructor. Invalid or excessive values produce a user-facing error. The eventual deadline starts when the draft is saved or updated, not when a block confirms.',
 'createInheritancePlan': 'I read the opened wallet\'s current receiving public key for the Owner identity, combine it with the validated Beneficiary, exact Coin amount, and period, and save an encrypted draft. The returned plan supplies the UI summary. This step creates no funding output and reserves no wallet balance.',
 'updatePlan': 'I load the selected plan and invoke canManage through a User reference, letting Java dispatch to the actual role. Only an active unfunded owner draft can be replaced with newly validated terms and a fresh deadline. Funded terms are immutable; their owner must spend the output and create a replacement.',
 'prepareFunding': 'I require synchronized peers and an active owner-controlled unfunded plan whose calendar deadline has not passed. I watch its P2SH address and prepare an unsigned ordinary payment to that script for the exact covered amount. The returned SendRequest provides the fee and total for a separate funding review.',
 'fundPlan': 'I recheck owner permission, draft state, deadline, and the exact reviewed script/value output after authentication. The wallet signs the reviewed request. I attach its immutable funding transaction and output index to the encrypted plan before committing and saving the pending wallet transaction. The UI then explicitly requests relay; repository or storage errors propagate.',
 'checkInheritanceStatus': 'I derive status from current evidence. A draft has no Bitcoin funding. Funded plans require synchronized peers, a known non-conflicting funding transaction, confirmation depth, unspent output, and no pending spend of that outpoint. I compare median chain time strictly greater than the deadline for beneficiary availability. Offline or conflicting evidence cannot grant recovery.',
 'prepareRecovery': 'I receive a plan ID, fee-rate String, and cancellation boolean. The service checks the opened wallet\'s dynamically dispatched role and verified status, finds the required private ECKey, and passes the exact funding output to InheritanceScript.spend. The returned signed transaction is only a review candidate; no commitment or relay occurs here.',
 'recoverInheritance': 'I recheck plan eligibility at submission after the review and authentication delay. I require the reviewed input outpoint, locktime, non-final sequence, recipient ownership, output count, and bounded fee to match the plan. Script verification binds the beneficiary or owner signature to that exact spend. I commit and encrypt it before the screen requests relay.',
 'cancelDraft': 'I permit only the matching owner to mark an unfunded draft cancelled and save that state. If the plan is funded, the domain method rejects local cancellation because deleting or changing metadata cannot spend a Bitcoin output.',
 'importPlan': 'I receive public JSON text from Android\'s document picker, bound its size, and decode its network and exact funding terms. I require this wallet to match an owner or beneficiary identity. A duplicate ID must match existing recovery data. I save the imported plan, watch its script, and replay history; import alone never establishes confirmation.',
 'exportPlan': 'I retrieve the selected plan and encode its public terms and funding data as formatted JSON for the document picker or details dialog. The result has no private keys or mnemonic. It must still be backed up because seed recovery alone cannot reconstruct this custom script.',
 'attachFunding': 'I receive a Transaction and integer output index and compare that output\'s amount and locking script with the plan. I store its serialized public bytes and index only once. This binds recovery to one exact UTXO; a different funding output is rejected.',
 'userFor': 'I check whether the opened Wallet contains the owner or beneficiary public key and return that subclass through the abstract User type. Subsequent canManage and canRecover calls use runtime method dispatch. An unrelated wallet is rejected; the signing service additionally requires a private key.',
 'getKey': 'I open AndroidKeyStore and retrieve the application AES key. When creating a new envelope, a missing alias generates AES-256 with GCM through the platform KeyGenerator. Loading never creates a replacement for a missing key because it could not decrypt old files; the caller must restore from recovery information.',
 'validateFileName': 'I validate a filename against this build\'s network-specific wallet pattern before any file operation. This prevents directory traversal and opening the other network\'s namespace. Existing Testnet names retain their format; Mainnet uses its own prefix.',
 'findWallet': 'I compute the deterministic account identity and loop over saved files from this network. Matching identity reuses the existing wallet rather than overwriting it during restoration. A damaged entry does not stop the search through other entries. No match returns null.',
 'saveWallet': 'I reuse an existing matching account when found; otherwise I create a UUID filename within this network\'s namespace and encrypt the wallet. The returned String is the navigation/storage identifier, not private key data.',
 'updateWallet': 'I validate network and filename, serialize Wallet.saveToFileStream into memory, and pass those bytes to encrypted atomic storage. The plaintext byte array is cleared in a finally block on success or failure. Provider or filesystem exceptions prevent the caller from claiming persistence succeeded.',
 'writeEncrypted': 'I pass serialized bytes and the Android Keystore key to WalletCipher.encrypt. AtomicFile.startWrite writes a replacement; finishWrite publishes it only after success. If writing fails, failWrite restores the previous file and the exception reaches the service.',
 'readEncrypted': 'I reject an oversized file, read the AtomicFile envelope, and ask WalletCipher.decrypt to authenticate and decrypt it with the existing device key. The returned byte array is private serialized data that the caller must clear after parsing. Corruption and missing-key errors propagate.',
 'loadWallet': 'I validate the network-specific filename, decrypt its envelope, and pass its bytes to Wallet.loadFromFileStream. I check the loaded wallet network again, because a filename alone is not proof of its contents. A finally block clears the plaintext array; the returned Wallet retains bitcoinj\'s necessary in-memory keys.',
 'getSavedWalletFiles': 'I list only filenames matching this build\'s wallet namespace and sort the resulting String array. A missing directory listing returns an empty array. Mainnet files are not converted or exposed in Testnet, and legacy Testnet files remain untouched.',
 'deleteWallet': 'I receive a validated filename from the authenticated deletion flow. The service closes and saves a selected wallet before the storage helper removes its local AtomicFile. The boolean result reports file removal. This does not remove an inheritance UTXO from the blockchain; the warning requires a separate backup.',
 'encrypt': 'I initialize Java Cipher in AES-GCM encryption mode with the supplied SecretKey. The provider generates an IV and doFinal produces authenticated ciphertext. I assemble the existing IV-length/IV/ciphertext envelope and return bytes for atomic storage. Cryptographic failures propagate without a partially accepted wallet.',
 'decrypt': 'I validate the envelope length and twelve-byte IV, initialize Java Cipher with a 128-bit GCM authentication tag, and call doFinal on the ciphertext. Wrong keys, truncation, or tampering raise GeneralSecurityException. Only authenticated plaintext is returned to wallet or plan parsing.',
 'parsePublicKey': 'I receive compressed public-key hexadecimal text from beneficiary setup or a recovery package. I require 33 bytes beginning with 02 or 03, then use ECKey.fromPublicOnly and validate the secp256k1 point. The returned ECKey contains public information only. Invalid points cannot proceed to script construction.',
 'onCreate': 'Android calls this lifecycle method when creating or recreating the component. I initialize the parent behavior, connect Java listeners to the XML controls or register application callbacks, and restore only the workflow state explicitly handled below. Button callbacks capture input on the UI thread and delegate expensive wallet work to the service. Errors are displayed instead of silently continuing.',
 'onResume': 'Android calls this when the screen returns to the foreground. I invoke parent lifecycle handling and start the public-data refresh or wallet-list behavior shown below. Secured screens enforce the application unlock state. The paired pause method stops periodic callbacks so background screens do not keep refreshing.',
 'onPause': 'Android calls this as the screen leaves the foreground. I stop this screen\'s refresh callbacks or conceal sensitive displayed data as implemented below, then call the parent lifecycle method. Wallet mutations remain serialized by the shared service rather than being discarded with the Activity.',
 'onSaveInstanceState': 'I receive the Bundle supplied by Android before recreation. I save only explicit workflow identifiers and flags, such as a wallet filename, selected plan ID, or submitted transaction ID. Mnemonic words and signing keys are not placed in this Bundle. New Activity instances reconstruct their screen through the existing service.',
 'refresh': 'I read the selected wallet\'s public data and update this screen\'s displayed address, balance, plan status, or history. The conditions below avoid redundant or overlapping work. Expensive repository/script operations use the worker, and their results return to the main thread. Refresh does not authorize a payment or claim.',
 'refreshWallet': 'I retrieve current balances, address, peer status, recent wallet transactions, and inheritance summaries for the dashboard. The loop limits recent records to three, while plan lookups run through the worker. Missing wallet state is tolerated during asynchronous opening, so the interface waits rather than accessing an unavailable selection.',
 'reviewPayment': 'I capture recipient, BTC amount, and fee text after checking that this screen has not already submitted. The worker prepares an unsigned request. I display its exact output and fee in an AlertDialog; Cancel releases the review guard, while Confirm requests Android authentication before signing and encrypted saving. Relay uses the saved transaction ID.',
 'reviewRecovery': 'I capture the selected plan and fee, then ask the worker to construct a claim or cancellation candidate. I display destination, received amount, deducted fee, and network before requesting authentication. Submission rechecks eligibility and saves the transaction before relay. Cancellation of review or authentication clears the guard.',
 'fundPlan': 'I retain the selected plan ID while the worker prepares funding. I show beneficiary, amount, fee, total, deadline, and network in a confirmation dialog. Authentication gates commitment; failures leave a visible error. Success opens status/recovery so the public package can be exported while confirmation is pending.',
 'authenticate': 'I receive the Runnable representing a reviewed sensitive action. Android KeyguardManager verifies a device PIN, pattern, or password through an Activity Result launcher. An unsecured device is rejected for financial actions. I run the retained callback only for RESULT_OK; cancellation or recreation requires a fresh review rather than reconstructing authorization.',
 'work': 'I receive a service Operation and a success Consumer, submit the operation to the wallet worker, and return its completion to the Android main thread. Finished or destroyed screens ignore results. I unwrap exception causes for a visible error and call the success callback only after successful completion.',
 'showWallet': 'I reload the encrypted wallet before displaying its public address. For a newly created wallet I number the mnemonic words and show the backup confirmation; a restored wallet does not redisclose them. I hide setup input sections and reveal Continue only after persistence succeeds.',
 'numberedWords': 'I loop through the ordered mnemonic List and append a one-based number and newline for each word. The returned String preserves backup order on narrow screens and large fonts; it is displayed only in the protected creation view and is never logged.',
 'savePlan': 'I validate the latest beneficiary, period, and amount fields at the time of saving rather than relying on earlier validation buttons. The update boolean chooses an existing owner draft versus a new draft. The worker saves the terms and the main-thread callback selects and displays the resulting plan.',
 'displayPlan': 'I receive a validated plan and format its beneficiary, BTC amount, hours, deadline, and local draft/funded state into the setup summary. This is descriptive metadata; verified recovery eligibility is checked on the recovery screen against current chain evidence.',
 'showTransaction': 'I find the real wallet transaction by its ID and show public details. Only a pending self-created transaction offers Retry Broadcast. That explicit action requires Android authentication and relays the existing signed transaction, so uncertain relay does not create a second payment.',
 'displaySavedWallets': 'I loop over this network\'s encrypted files on the worker, loading each public address or retaining an unreadable entry. On the main thread I inflate rows and attach Open and authenticated Delete listeners. The empty-state view explains when no wallets exist.',
 'loadWallet': 'I use the wallet filename passed in the Activity Intent to reopen the same encrypted selection after recreation. Success sets walletReady, updates public content, and starts a connection when needed. Errors leave financial controls guarded by the unset readiness flag.',
 'resetForReplay': 'I collect pending transactions before Wallet.reset rebuilds chain-derived history, then loop over that collection and call maybeCommitTx to retain uncertain submissions. This avoids turning a relay retry into a new payment. Deterministic keys are kept; replay will rediscover confirmed transactions.',
}

METHODS.update({
 'createWallet': 'I create a deterministic P2WPKH wallet with bitcoinj and immediately pass it to encrypted storage. The service returns the saved filename for navigation; the policy returns the Wallet for persistence. This operation generates keys without connecting to peers or moving coins. A storage failure prevents the setup screen from claiming the wallet is saved.',
 'restoreWallet': 'I receive the recovery phrase and original optional BIP-39 passphrase as Strings from the restore interface. WalletPolicy validates the ordered words and checksum and reconstructs the deterministic SegWit account. The service encrypts it or reuses an existing matching account, returning the filename for Continue. Failed mnemonic or storage checks stop the workflow.',
 'getTransactions': 'I retrieve the opened wallet\'s actual bitcoinj transactions and expose an ArrayList of public BitcoinTransaction records. The wallet wrapper loops through recent transactions, including confidence changes; the service delegates to that wrapper. The History and Dashboard screens use these records rather than simulated balances or a second database.',
 'getBalance': 'I return the amount bitcoinj currently selects for ordinary payments as an exact Coin value. Watched inheritance outputs are excluded from payment candidates. Confirmed and unconfirmed output totals are displayed separately because confirmation category and spendability are different questions. The interface formats this result as BTC and preparation checks it before selecting inputs.',
 'findTransaction': 'I receive a transaction ID String from a history row and loop through the actual public history records until that ID matches. The returned BitcoinTransaction supplies details; null means the transaction is no longer present and the screen stops rather than accessing a missing record.',
 'requireWallet': 'I snapshot the selected BitcoinWallet reference and reject a missing selection with IllegalStateException. The returned wrapper supplies the same wallet and filename to subsequent operations. This central guard prevents screens from operating on stale or unopened wallet data.',
 'sendBitcoin': 'I receive the exact reviewed SendRequest after the user confirms and authenticates. signReviewed rechecks inputs and signs through bitcoinj. I mark its source SELF, commit it to the wallet pending pool, and persist encrypted data before returning the Transaction to the UI for explicit relay. A failure propagates; a saved pending transaction is retried by ID instead of recreated.',
 'parseFeeRate': 'I parse a whole-number satoshi-per-virtual-byte String supplied by the fee input and enforce 1 through 1000. The returned long drives ordinary fee calculation or the conservative inheritance spend estimate. Non-numeric, zero, negative, and excessive rates raise an input error before signing.',
 'requireNetwork': 'I compare Wallet.network with this build\'s fixed BitcoinNetwork. The application never converts wallet data between networks. A mismatch raises IllegalArgumentException before addresses, balances, storage, or signing use that wallet.',
 'walletIdentity': 'I serialize the wallet\'s account watching key for the selected network. This public account identity stays stable when receiving addresses rotate, so restoration can detect a previously saved wallet without comparing its latest address. I enforce the network before returning the String.',
 'canManage': 'I implement the role\'s plan-management decision through the abstract User contract. Owner compares its public key with the plan owner; Beneficiary denies management. Services call this through User references, demonstrating dynamic binding. This boolean is a role check, while actual spending additionally requires the private key and verified output.',
 'canRecover': 'I implement the role\'s beneficiary-recovery decision. Beneficiary requires the AVAILABLE verified status and its matching plan public key; Owner denies beneficiary recovery. The returned boolean controls UI availability and service permission, with private-key signing and chain checks still required to submit.',
 'describeRole': 'I return a public description of this user\'s actual role for the status screen. Calling through a User reference executes the Owner or Beneficiary override at runtime, so the interface and permissions use the same role object.',
 'getPlans': 'I load this wallet\'s encrypted JSON plan collection through the repository and decode each entry into a validated InheritancePlan. An absent file returns an empty ArrayList. Corrupt or wrong-network data raises an error rather than displaying invented recovery eligibility.',
 'getPlan': 'I receive the selected plan ID from the interface and ask the repository to find it within this wallet\'s encrypted plan collection. A missing ID raises an input error. The returned validated plan is used for funding, status, export, or recovery checks.',
 'find': 'I loop over the wallet\'s decoded plans and compare each ID with the selected String. A match returns the InheritancePlan; no match raises IllegalArgumentException so callers cannot accidentally use another plan.',
 'save': 'I load the encrypted collection, loop for an existing plan ID, and replace that entry or append a new plan. I encode the complete collection as JSON and pass its UTF-8 bytes to atomic encrypted storage. Success preserves all other plans; failure propagates without reporting a saved draft.',
 'encodeUser': 'I serialize only the User name, public key, and network address into a JSONObject for the recovery package. The common User type lets both subclasses share the representation. No ECKey private material or recovery phrase is included.',
 'outputScript': 'I hash the validated redeem script with bitcoinj ScriptBuilder.createP2SHOutputScript and return the locking Script. Funding pays this script hash; recovery must reveal the original redeem script and satisfy its signature and timelock branch.',
 'address': 'I convert the inheritance P2SH output script into its public address using this build\'s BitcoinNetwork. The owner funds this destination and the beneficiary wallet watches it. An address is public routing information; it does not authorize a spend.',
 'cancelDraft': 'I mark an unfunded draft cancelled through its controlled domain method and repository persistence when called through the service. A funded plan is rejected because changing local metadata cannot remove an on-chain lock. The owner must use the signed cancellation branch for funded Bitcoin.',
 'fundingTransaction': 'I require attached funding bytes and pass decoded hexadecimal data to Transaction.read with a ByteBuffer. The returned public Transaction recreates the funding ID and output used by recovery. It does not establish that peers confirmed the transaction; the service checks wallet confidence separately.',
 'input': 'I read the EditText identified by the supplied Android resource ID and return its current String. Callers capture this value before submitting worker operations, then run domain validation. Reading text alone does not establish a valid amount, address, or period.',
 'readBeneficiary': 'I gather the latest name, compressed-key hex, and address Strings from setup fields and pass them to InheritanceService.addBeneficiary. Constructor validation prevents saving a mismatched address/key identity. The returned Beneficiary contains only public information.',
 'openRecovery': 'I receive the selected plan ID and a boolean describing the requested cancellation navigation. I pass the wallet filename and plan ID in an Android Intent to BeneficiaryRecoveryActivity. No signing data is included, and the recovery screen still requires verified eligibility and authenticated submission.',
 'navigate': 'I receive an Activity class from a button listener, require the opened wallet to be ready, and start that screen with only the selected wallet filename. The destination reloads through the shared service after process recreation.',
 'dashboard': 'I open the saved wallet on the service worker before launching WalletDashboardActivity with its filename. Success finishes setup; loading failure displays an error without pretending the wallet is available.',
 'showRestoredState': 'I hide recovery input controls and reveal Continue after the wallet has been encrypted successfully. The public confirmation text replaces the sensitive phrase; failures do not enter this state.',
 'broadcastSaved': 'I show the saved transaction ID and request relay through the service. A successful result reports relay while still awaiting mining. A failure preserves the pending ID and disables another payment, directing the user to authenticated History retry.',
 'paymentError': 'I unwrap asynchronous exception causes and translate InsufficientMoneyException into an amount-plus-fee message. Other errors use their available message or a generic retry instruction. This returned String is displayed on the Send screen, never a fabricated success.',
 'rootMessage': 'I unwrap nested asynchronous exceptions to their underlying cause and return a user-facing message, with a fallback when the cause has no text. The interface displays this String and leaves the operation incomplete.',
 'displayPlan': 'I format public plan terms into the setup summary using BTC text, integer hours, and an Android locale date/time. The local draft/funded label describes saved terms; chain-verified claim eligibility remains on the recovery screen.',
 'showError': 'I pass a failed-operation String to the shared feedback helper, which displays error styling and readable text. This keeps validation, library, and storage failures visible in the current XML screen.',
 'showMessage': 'I pass public progress or completion text to the feedback helper using informational styling. Transaction relay messages distinguish pending confirmation from a mined success.',
 'feedback': 'I update the XML error/feedback TextView with the supplied message and boolean error flag. Styling and visibility make new messages readable; if no feedback view exists, Android Toast displays the text. This helper performs no wallet mutation.',
 'watchFeedback': 'I attach a TextWatcher to an optional feedback view so empty text hides its card and nonempty text restores visibility. Android text callbacks change only presentation; wallet decisions remain in the service.',
 'link': 'I connect a dashboard button resource ID to its destination Activity class. The listener waits for asynchronous wallet opening and passes the selected filename through an Intent. The destination still performs its own selected-wallet loading and security checks.',
 'onActivityStopped': 'I decrement the visible-activity counter without allowing it below zero. When the app has no visible screen, outside configuration changes or authentication, I clear the unlocked flag and submit persistence. A missing selected wallet is harmless; existing wallet storage failures remain tracked by the service.',
 'onActivityStarted': 'I increment the visible-activity counter supplied by Android lifecycle callbacks. This shared count lets the Application distinguish moving between internal screens from leaving the app entirely.',
 'setContentView': 'I load the XML layout through the parent Activity, then attach system-bar and keyboard insets to its root while preserving the original padding. I register shared feedback listeners after views exist. This keeps controls accessible under Android edge-to-edge layouts.',
 'walletName': 'I strip the correct network-specific prefix and extension from a public storage filename and show a short identifier using the Android String resource. This avoids showing the same mainnet prefix for every Mainnet wallet.',
 'empty': 'I inflate the shared empty-state XML row and place the requested Android String resource into its message field. The returned View is added to the wallet, plan, or transaction container without creating artificial data.',
 'transaction': 'I inflate one XML transaction row from a public BitcoinTransaction, display its type, signed amount, confirmation status, date, and ID, and connect the details Runnable. The returned View goes into History; amount sign and text communicate direction in addition to color.',
 'plan': 'I inflate one XML plan row with the beneficiary name and covered BTC amount, then attach the selected-plan Runnable. The returned View goes into the setup or recovery list. Displaying a row does not grant recovery permission.',
 'status': 'I map the InheritancePlan.Status enum to public text for the dashboard and recovery screen. Selection distinguishes draft, unverified, locked, available, pending spend, spent, and cancelled states. The underlying service, not this formatter, determines eligibility.',
})

# Explanations with the same method name sometimes have class-specific semantics.
SPECIAL = {
 ('Hex', 'encode'): 'I loop through bytes, format each unsigned value as two hexadecimal digits using Locale.ROOT, and return the concatenated public String. Recovery packages use it for keys and serialized transactions; encoding does not encrypt data.',
 ('Hex', 'decode'): 'I reject null, odd-length, or non-hexadecimal text, allocate one byte per character pair, and loop to parse each pair in base sixteen. The resulting byte array is passed to public-key or transaction parsing. Malformed text raises an input error.',
 ('InheritanceRepository', 'encode'): 'I create a JSONObject containing version, build network, plan ID, public identities, exact satoshis, timestamps, funding bytes, output index, and draft cancellation state. The result is encrypted locally or exported as public recovery information; it contains no mnemonic or private key.',
 ('InheritanceRepository', 'decode'): 'I receive a JSONObject from encrypted local data or an imported package. I validate version and network before constructing the public identities and plan, then parse bounded funding bytes and attach only an exact matching output. A malformed script, amount, key, or timelock raises an error. Confirmation still requires independent chain evidence.',
 ('InheritanceService', 'fundPlan'): METHODS['fundPlan'].replace('I retain the selected plan ID while the worker prepares funding. I show beneficiary, amount, fee, total, deadline, and network in a confirmation dialog. Authentication gates commitment; failures leave a visible error. Success opens status/recovery so the public package can be exported while confirmation is pending.', 'I recheck ownership, draft state, deadline, and the exact funding output against the reviewed SendRequest. After signing, I attach and encrypt public recovery data before committing and encrypting the pending wallet transaction. Failure propagates; broadcasting remains a separate explicit UI action.'),
 ('WalletStorageManager', 'loadWallet'): 'I validate the network-specific filename, decrypt the file, and parse its bytes with bitcoinj Wallet.loadFromFileStream. I check the internal wallet network before returning it and clear the plaintext array in finally. Corrupt files and absent Keystore keys raise errors instead of silently creating a replacement wallet.',
}

pattern = re.compile(r'(?m)^(    )((?:@Override\s+)?(?:(?:public|protected|private|static|final|synchronized)\s+)+(?:<[^\n]+?>\s+)?[\w<>?,.\[\] ]+\s+(\w+)\(([^)]*)\)(?:\s+throws [^{;]+)?\s*\{)')
for path in (ROOT / 'app/src/main/java/com/example/legacybtc').glob('*.java'):
    source = path.read_text(encoding='utf-8')
    name = path.stem
    if name in CLASSES:
        # Replace only the class-level JavaDoc preceding the top-level declaration.
        source = re.sub(r'/\*\*.*?\*/(?=\s*public (?:abstract |final )?class ' + name + r'\b)',
                        '/**\n * ' + '\n * '.join(textwrap.wrap(CLASSES[name], 92)) + '\n */', source, count=1, flags=re.S)
    source = source.replace('Public Testnet peer configuration; Bitcoin network selection remains fixed to Testnet.', 'I validate public peer configuration for this build\'s network.')
    source = source.replace('Enter a Testnet peer port.', 'Enter a Bitcoin peer port.')
    edits = []
    for match in pattern.finditer(source):
        method = match.group(3)
        description = SPECIAL.get((name, method), METHODS.get(method))
        if not description:
            continue
        before = source[:match.start()].rstrip()
        # Preserve hand-written detailed JavaDoc; expand shorter comments instead.
        start = match.start()
        if before.endswith('*/'):
            docstart = before.rfind('/**')
            if docstart >= 0 and len(before[docstart:]) > 450:
                continue
            if docstart >= 0:
                start = source.rfind('\n', 0, docstart) + 1
        lines = ['    /**'] + ['     * ' + line for line in textwrap.wrap(description, 88)]
        for part in match.group(4).split(','):
            part = part.strip()
            if not part or ' ' not in part:
                continue
            typ, param = part.rsplit(' ', 1)
            if re.fullmatch(r'\w+', param):
                lines.append('     * @param ' + param + ' ' + typ + ' supplied by the calling workflow as described above')
        if re.search(r'\)\s+throws\b', match.group(2)):
            lines.append('     * @throws Exception if the validation, library, or storage operation described above fails')
        lines.append('     */\n')
        edits.append((start, match.start(), '\n'.join(lines)))
    for start, end, comment in reversed(edits):
        source = source[:start] + comment + source[end:]
    # Add return tags to documented non-void operations without changing their explanation.
    tags = []
    for match in pattern.finditer(source):
        signature = match.group(2).split('(')[0]
        before = source[:match.start()].rstrip()
        if not before.endswith('*/') or re.search(r'\bvoid\s+' + match.group(3) + r'$', signature):
            continue
        docstart = before.rfind('/**')
        if docstart < 0 or '@return' in before[docstart:]:
            continue
        end = before.rfind('*/')
        tags.append((end, ' * @return the value or object described above for the calling workflow\n     '))
    for position, tag in reversed(tags):
        source = source[:position] + tag + source[position:]
    source = source.replace('      * @return', '     * @return')
    returns = {
        'submit': 'a CompletableFuture completed with the operation result or its exception',
        'openWallet': 'the selected BitcoinWallet wrapper after encrypted loading succeeds',
        'createWallet': 'the new Wallet at policy level or its encrypted filename at service level',
        'restoreWallet': 'the reconstructed Wallet or its existing/new encrypted filename',
        'preparePayment': 'the unsigned SendRequest whose outputs and fee appear in the review',
        'sendBitcoin': 'the signed pending Transaction saved before explicit relay',
        'generateAddress': 'the newly generated public receiving address after persistence',
        'broadcast': 'a future for peer relay acknowledgement, not mining confirmation',
        'medianTimePast': 'the median epoch-second timestamp of the last eleven verified headers',
        'getBalance': 'the spendable ordinary-payment amount as an exact Coin',
        'getTransactions': 'an ArrayList of actual wallet transaction history records',
        'findTransaction': 'the matching public history record, or null when no ID matches',
        'requireWallet': 'the currently selected wallet wrapper, never null',
        'addBeneficiary': 'a validated public Beneficiary identity without a private key',
        'setInheritancePeriod': 'the validated inheritance period in seconds',
        'createInheritancePlan': 'the encrypted saved draft, without a funded Bitcoin output',
        'updatePlan': 'the replacement unfunded draft with the same ID and new deadline',
        'prepareFunding': 'the unsigned request paying the plan script and covered amount',
        'fundPlan': 'the signed funding transaction saved as pending',
        'checkInheritanceStatus': 'the plan state determined by current verification evidence',
        'prepareRecovery': 'a signed review candidate that has not been committed or relayed',
        'recoverInheritance': 'the authenticated reviewed spend committed and encrypted locally',
        'importPlan': 'the validated imported or matching saved funded plan',
        'exportPlan': 'formatted public JSON recovery information without signing secrets',
        'getPlans': 'the wallet-specific ArrayList of decoded validated plans',
        'getPlan': 'the validated saved plan identified by the requested ID',
        'userFor': 'the actual Owner or Beneficiary through a parent User reference',
        'find': 'the saved plan matching the requested ID',
        'getKey': 'the device-bound AES SecretKey supplied by Android Keystore',
        'findWallet': 'the existing account filename, or null when no account matches',
        'saveWallet': 'the existing or newly encrypted wallet filename',
        'readEncrypted': 'authenticated plaintext bytes that the caller must clear after use',
        'loadWallet': 'the network-checked parsed Wallet when loading, or no value for the screen-loading callback',
        'getSavedWalletFiles': 'a sorted array of filenames belonging only to this build network',
        'deleteWallet': 'true if the selected local wallet file no longer exists',
        'encrypt': 'the IV and authenticated ciphertext envelope for atomic storage',
        'decrypt': 'plaintext only after successful GCM authentication',
        'parsePublicKey': 'a validated compressed public-only ECKey',
        'parseFeeRate': 'a fee rate from 1 through 1000 satoshis per virtual byte',
        'walletIdentity': 'the stable network-encoded account watching-key String',
        'canManage': 'whether this public role matches the plan owner permission',
        'canRecover': 'whether this role is the matching beneficiary of an AVAILABLE plan',
        'describeRole': 'the actual user role description selected by dynamic dispatch',
        'encode': 'public hexadecimal text or a JSONObject representation as described above',
        'decode': 'validated decoded bytes or an InheritancePlan as described above',
        'encodeUser': 'a JSONObject containing only the name, public key and address',
        'outputScript': 'the P2SH locking Script that commits to the redeem-script hash',
        'address': 'the selected-network public P2SH address for the inheritance output',
        'fundingTransaction': 'the parsed public funding transaction, without confirmation proof',
        'input': 'the current EditText String captured for subsequent validation',
        'readBeneficiary': 'the latest validated public beneficiary from setup fields',
        'numberedWords': 'the ordered numbered mnemonic display String for the protected setup view',
        'paymentError': 'a readable payment-failure message for the interface',
        'rootMessage': 'the underlying exception message or a readable fallback',
        'walletName': 'a short public filename identifier formatted with an Android resource',
        'empty': 'the inflated XML empty-state row',
        'transaction': 'the populated XML transaction row with its details listener',
        'plan': 'the populated XML plan row with its selection listener',
        'status': 'public text corresponding to the verified status enum',
    }
    corrections = []
    for match in pattern.finditer(source):
        before = source[:match.start()].rstrip()
        if not before.endswith('*/'):
            continue
        docstart = before.rfind('/**')
        needle = 'the value or object described above for the calling workflow'
        position = before.find(needle, docstart)
        if position >= 0 and match.group(3) in returns:
            corrections.append((position, position + len(needle), returns[match.group(3)]))
    for start, end, description in reversed(corrections):
        source = source[:start] + description + source[end:]
    path.write_text(source, encoding='utf-8', newline='')
print('Reviewed class and operation explanations applied to Java sources.')
