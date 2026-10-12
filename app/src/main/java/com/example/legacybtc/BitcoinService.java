package com.example.legacybtc;

import android.content.Context;
import org.bitcoinj.base.Address;
import org.bitcoinj.base.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletChangeEventListener;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * I coordinate the opened wallet, encrypted persistence, transaction signing, and peer
 * session. A single ScheduledThreadPoolExecutor serializes mutations so two screens cannot
 * spend or save concurrently. Activities submit operations and display their CompletableFuture
 * results on the Android main thread. bitcoinj owns the keys, UTXOs, and transaction objects;
 * I keep application storage and lifecycle handling here.
 */
public final class BitcoinService {
    private static BitcoinService instance;
    private final Context context;
    private final org.bitcoinj.core.Context bitcoinContext = new org.bitcoinj.core.Context();
    private final ScheduledThreadPoolExecutor worker = new ScheduledThreadPoolExecutor(1);
    private final BitcoinNetworkManager network;
    private volatile BitcoinWallet selected;
    private volatile String storageError = "";
    private boolean saveScheduled;
    private final WalletChangeEventListener changes = wallet -> scheduleSave();

    private BitcoinService(Context context) { this(context, new BitcoinNetworkManager()); }
    // Dependency injection lets integration tests verify the same service without real coins or peers.
    BitcoinService(Context context, BitcoinNetworkManager network) {
        this.context = context.getApplicationContext(); this.network = network;
    }
    public static synchronized BitcoinService get(Context context) {
        if (instance == null) instance = new BitcoinService(context);
        return instance;
    }
    public interface Operation<T> { T run() throws Exception; }
    /**
     * I receive an Operation callback, queue it on the single worker, and propagate the
     * bitcoinj Context onto that thread. The CompletableFuture carries its returned value to
     * the Activity or completes exceptionally when the operation fails. This keeps disk access
     * and signing off the Android main thread.
     * @param operation Operation<T> supplied by the calling workflow as described above
     * @return a CompletableFuture completed with the operation result or its exception
     */
    public <T> CompletableFuture<T> submit(Operation<T> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        worker.execute(() -> {
            org.bitcoinj.core.Context.propagate(bitcoinContext);
            try { result.complete(operation.run()); } catch (Exception error) { result.completeExceptionally(error); }
        });
        return result;
    }
    /**
     * I create a deterministic P2WPKH wallet with bitcoinj and immediately pass it to
     * encrypted storage. The service returns the saved filename for navigation; the policy
     * returns the Wallet for persistence. This operation generates keys without connecting to
     * peers or moving coins. A storage failure prevents the setup screen from claiming the
     * wallet is saved.
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return the new Wallet at policy level or its encrypted filename at service level
     */
    public String createWallet() throws Exception { return WalletStorageManager.saveWallet(context, WalletPolicy.createWallet()); }
    /**
     * I receive the recovery phrase and original optional BIP-39 passphrase as Strings from
     * the restore interface. WalletPolicy validates the ordered words and checksum and
     * reconstructs the deterministic SegWit account. The service encrypts it or reuses an
     * existing matching account, returning the filename for Continue. Failed mnemonic or
     * storage checks stop the workflow.
     * @param phrase String supplied by the calling workflow as described above
     * @param passphrase String supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return the reconstructed Wallet or its existing/new encrypted filename
     */
    public String restoreWallet(String phrase, String passphrase) throws Exception {
        return WalletStorageManager.saveWallet(context, WalletPolicy.restoreWallet(phrase, passphrase));
    }
    /**
     * I receive the selected encrypted filename from navigation. I close and save a different
     * opened wallet first, load this wallet, restore watched inheritance addresses, and
     * recover locally saved funding when necessary. A wallet change listener schedules future
     * encrypted saves. The returned BitcoinWallet becomes the shared selection; loading errors
     * leave the screen unable to spend.
     * @param file String supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return the selected BitcoinWallet wrapper after encrypted loading succeeds
     */
    public BitcoinWallet openWallet(String file) throws Exception {
        if (selected != null && selected.getFileName().equals(file)) return selected;
        closeWallet();
        Wallet wallet = WalletStorageManager.loadWallet(context, file);
        for (InheritancePlan plan : new InheritanceRepository(context).getPlans(file)) {
            if (!plan.isFunded()) continue;
            wallet.addWatchedAddress(InheritanceScript.address(plan), java.time.Instant.ofEpochSecond(plan.getCreatedAt()));
            if (wallet.findKeyFromPubKey(Hex.decode(plan.getOwner().getPublicKey())) != null
                    && wallet.getTransaction(plan.fundingTransaction().getTxId()) == null) {
                Transaction funding = plan.fundingTransaction();
                funding.getConfidence().setSource(org.bitcoinj.core.TransactionConfidence.Source.SELF);
                wallet.maybeCommitTx(funding);
            }
        }
        selected = new BitcoinWallet(file, wallet);
        wallet.addChangeEventListener(Runnable::run, changes);
        storageError = "";
        return selected;
    }
    /**
     * I pass the opened wallet and its filename to the network manager. Network startup,
     * header synchronization, and peer discovery run on the service worker. A startup
     * exception reaches the interface; financial actions remain unavailable until
     * synchronization and a peer connection are verified.
     * @throws Exception if the validation, library, or storage operation described above fails
     */
    public void connect() throws Exception {
        BitcoinWallet current = requireWallet();
        network.start(context, current.getWallet(), current.getFileName());
    }
    /**
     * I stop peers before detaching the wallet listener and persisting the current wallet.
     * Only a successful save clears the selected reference, so a storage failure is not
     * silently discarded. Calling this with no selection has no effect.
     * @throws Exception if the validation, library, or storage operation described above fails
     */
    public void closeWallet() throws Exception {
        if (selected != null) {
            network.stop();
            selected.getWallet().removeChangeEventListener(changes);
            persist();
            selected = null;
        }
    }
    /**
     * I receive a validated filename from the authenticated deletion flow. The service closes
     * and saves a selected wallet before the storage helper removes its local AtomicFile. The
     * boolean result reports file removal. This does not remove an inheritance UTXO from the
     * blockchain; the warning requires a separate backup.
     * @param file String supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return true if the selected local wallet file no longer exists
     */
    public boolean deleteWallet(String file) throws Exception {
        if (selected != null && selected.getFileName().equals(file)) closeWallet();
        return WalletStorageManager.deleteWallet(context, file);
    }
    /**
     * I snapshot the selected BitcoinWallet reference and reject a missing selection with
     * IllegalStateException. The returned wrapper supplies the same wallet and filename to
     * subsequent operations. This central guard prevents screens from operating on stale or
     * unopened wallet data.
     * @return the currently selected wallet wrapper, never null
     */
    public BitcoinWallet requireWallet() {
        BitcoinWallet current = selected;
        if (current == null) throw new IllegalStateException("Open a wallet first.");
        return current;
    }
    /**
     * I serialize the currently selected bitcoinj wallet through
     * WalletStorageManager.updateWallet. Success clears the storage error; failure records a
     * visible error and propagates the exception so further financial operations stop rather
     * than broadcasting an unsaved transaction.
     * @throws Exception if the validation, library, or storage operation described above fails
     */
    public void persist() throws Exception {
        BitcoinWallet current = requireWallet();
        try { WalletStorageManager.updateWallet(context, current.getFileName(), current.getWallet()); storageError = ""; }
        catch (Exception e) { storageError = "Wallet could not be saved. Stop sending and retry storage."; throw e; }
    }
    /**
     * I coalesce wallet change events into one delayed encrypted save on the worker. The
     * synchronized scheduling flag prevents redundant queued writes. bitcoinj Context is
     * propagated before storage operations. Failure remains recorded in storageError for
     * wallet screens and requireReady to report.
     */
    private synchronized void scheduleSave() {
        if (saveScheduled) return;
        saveScheduled = true;
        worker.schedule(() -> {
            synchronized (this) { saveScheduled = false; }
            org.bitcoinj.core.Context.propagate(bitcoinContext);
            if (selected != null) {
                try { persist(); } catch (Exception ignored) { /* Error is displayed on every wallet screen. */ }
            }
        }, 1, TimeUnit.SECONDS);
    }
    /**
     * I ask Wallet.freshReceiveAddress for the next deterministic receiving address, then
     * persist the advanced keychain before returning its public String to the Receive screen.
     * Earlier addresses remain tracked. A save failure prevents displaying a newly generated
     * address as safely persisted.
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return the newly generated public receiving address after persistence
     */
    public String generateAddress() throws Exception {
        String address = requireWallet().getWallet().freshReceiveAddress().toString();
        persist();
        return address;
    }
    /**
     * I return the amount bitcoinj currently selects for ordinary payments as an exact Coin
     * value. Watched inheritance outputs are excluded from payment candidates. Confirmed and
     * unconfirmed output totals are displayed separately because confirmation category and
     * spendability are different questions. The interface formats this result as BTC and
     * preparation checks it before selecting inputs.
     * @return the spendable ordinary-payment amount as an exact Coin
     */
    public Coin getBalance() { return requireWallet().getBalance(); }
    /**
     * I retrieve the opened wallet's actual bitcoinj transactions and expose an ArrayList of
     * public BitcoinTransaction records. The wallet wrapper loops through recent transactions,
     * including confidence changes; the service delegates to that wrapper. The History and
     * Dashboard screens use these records rather than simulated balances or a second database.
     * @return an ArrayList of actual wallet transaction history records
     */
    public ArrayList<BitcoinTransaction> getTransactions() { return requireWallet().getTransactions(); }
    /**
     * I receive a transaction ID String from a history row and loop through the actual public
     * history records until that ID matches. The returned BitcoinTransaction supplies details;
     * null means the transaction is no longer present and the screen stops rather than
     * accessing a missing record.
     * @param id String supplied by the calling workflow as described above
     * @return the matching public history record, or null when no ID matches
     */
    public BitcoinTransaction findTransaction(String id) {
        for (BitcoinTransaction tx : getTransactions()) if (tx.getId().equals(id)) return tx;
        return null;
    }
    public Address validateAddress(String address) { return WalletPolicy.validateAddress(address); }
    /**
     * I check storage and synchronization readiness, then pass recipient, BTC amount, and fee-
     * rate Strings to BitcoinPayments.prepare. The returned unsigned SendRequest contains
     * selected inputs, change, and the fee shown to the user. I persist newly derived change
     * keys before displaying the review. No transaction is committed here.
     * @param recipient String supplied by the calling workflow as described above
     * @param amount String supplied by the calling workflow as described above
     * @param feeRate String supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return the unsigned SendRequest whose outputs and fee appear in the review
     */
    public SendRequest preparePayment(String recipient, String amount, String feeRate) throws Exception {
        requireReady();
        SendRequest request = BitcoinPayments.prepare(requireWallet().getWallet(), recipient, amount, feeRate);
        persist(); // Save any new change keys before displaying the review.
        return request;
    }
    /**
     * I receive the exact reviewed SendRequest after the user confirms and authenticates.
     * signReviewed rechecks inputs and signs through bitcoinj. I mark its source SELF, commit
     * it to the wallet pending pool, and persist encrypted data before returning the
     * Transaction to the UI for explicit relay. A failure propagates; a saved pending
     * transaction is retried by ID instead of recreated.
     * @param reviewed SendRequest supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return the signed pending Transaction saved before explicit relay
     */
    public Transaction sendBitcoin(SendRequest reviewed) throws Exception {
        Transaction transaction = signReviewed(reviewed);
        transaction.getConfidence().setSource(org.bitcoinj.core.TransactionConfidence.Source.SELF);
        requireWallet().getWallet().commitTx(transaction);
        persist();
        return transaction;
    }
    /**
     * I receive the unsigned SendRequest retained by the review dialog. I check readiness
     * and the exact fee, then loop over connected UTXOs to detect a competing spend during
     * authentication. Wallet.signTransaction fills signatures with wallet credentials;
     * TransactionInput.verify checks each signed input against its connected output.
     * The returned transaction is still uncommitted. sendBitcoin or fundPlan must save it
     * before relay, and the UI invokes those methods only after explicit confirmation.
     * @param reviewed exact request displayed in the confirmation dialog
     * @return signed transaction with the reviewed outputs and fee
     * @throws Exception if inputs changed, readiness failed, or signing/verification fails
     */
    Transaction signReviewed(SendRequest reviewed) throws Exception {
        requireReady();
        Wallet wallet = requireWallet().getWallet();
        if (reviewed.tx.getFee() == null || !reviewed.tx.getFee().isPositive()
                || reviewed.tx.getFee().isGreaterThan(WalletPolicy.MAX_TRANSACTION_FEE))
            throw new IllegalStateException("Review a transaction with a positive fee within the 0.01 BTC safety limit.");
        for (org.bitcoinj.core.TransactionInput input : reviewed.tx.getInputs()) {
            if (input.getConnectedOutput() == null || !input.getConnectedOutput().isAvailableForSpending())
                throw new IllegalStateException("Balance changed. Review the payment again.");
        }
        reviewed.signInputs = true;
        wallet.signTransaction(reviewed);
        for (org.bitcoinj.core.TransactionInput input : reviewed.tx.getInputs()) input.verify();
        return reviewed.tx;
    }
    /**
     * I stop peers and rebuild the per-wallet chain history so a newly watched inheritance
     * address can discover older funding and spends. The service resets chain-derived wallet
     * data while preserving keys and pending transactions and persists that reset. Recovery
     * remains unavailable until the resulting synchronization completes.
     * @throws Exception if the validation, library, or storage operation described above fails
     */
    public void rescan() throws Exception {
        BitcoinWallet current = requireWallet();
        network.stop();
        WalletReplay.resetForReplay(current.getWallet());
        persist(); // Saving the reset height lets a restart repeat the same safe replay.
        network.rescan(context, current.getWallet(), current.getFileName());
    }
    /**
     * I receive a signed, saved Transaction from an authenticated submission or retry. The
     * service checks readiness and selected-wallet ownership; PeerGroup.broadcastTransaction
     * sends it to connected peers and returns an asynchronous relay result. Relay is not
     * mining confirmation. Timeout or peer failure leaves the same pending transaction
     * available in History.
     * @param tx Transaction supplied by the calling workflow as described above
     * @return a future for peer relay acknowledgement, not mining confirmation
     */
    public CompletableFuture<Transaction> broadcast(Transaction tx) {
        // I relay only an exact transaction already saved by this selected wallet.
        // The UI reaches this method after review/authentication, or authenticated retry.
        requireReady();
        Transaction saved = requireWallet().getWallet().getTransaction(tx.getTxId());
        if (saved == null || saved.getConfidence().getSource() != org.bitcoinj.core.TransactionConfidence.Source.SELF
                || !java.util.Arrays.equals(saved.serialize(), tx.serialize()))
            throw new IllegalArgumentException("Only the selected wallet's saved, authorized transaction can be relayed.");
        CompletableFuture<Transaction> result = new CompletableFuture<>();
        network.broadcast(tx).whenComplete((sent, error) -> {
            if (error == null) result.complete(sent); else result.completeExceptionally(error);
        });
        // This timeout works on API 26 too; CompletableFuture.orTimeout requires API 31.
        worker.schedule(() -> result.completeExceptionally(new java.util.concurrent.TimeoutException("Relay not confirmed.")),
                45, TimeUnit.SECONDS);
        return result;
    }
    /**
     * I reject a missing selected wallet, recorded storage failure, or an
     * unsynchronized/disconnected peer session. Signing and recovery call this reusable guard
     * before mutating transactions. A successful check provides readiness at that moment;
     * later operations still recheck eligibility where required.
     */
    public void requireReady() {
        requireWallet();
        if (!storageError.isEmpty()) throw new IllegalStateException(storageError);
        if (!network.isReady()) throw new IllegalStateException("Wait for " + WalletPolicy.NETWORK_LABEL + " synchronization and a peer connection.");
    }
    public BitcoinNetworkManager getNetwork() { return network; }
    public String getStatus() { return storageError.isEmpty() ? network.getStatus() + " • " + network.getConnectedPeerCount() + " peers" : storageError; }
    void shutdown() throws Exception { closeWallet(); worker.shutdown(); }
}
