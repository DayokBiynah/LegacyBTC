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

public final class BitcoinService {
    private static BitcoinService instance;
    private final Context context;
    private volatile org.bitcoinj.core.Context bitcoinContext = new org.bitcoinj.core.Context();
    private final ScheduledThreadPoolExecutor worker = new ScheduledThreadPoolExecutor(1);
    private final BitcoinNetworkManager network;
    private volatile BitcoinWallet selected;
    private volatile String storageError = "";
    private boolean saveScheduled;
    private volatile long revision;
    private long selection;
    private final java.util.Map<SendRequest, PaymentReview> reviews = new java.util.WeakHashMap<>();
    private final java.util.Map<Transaction, PaymentReview> recoveryReviews = new java.util.WeakHashMap<>();
    private static final class PaymentReview {
        final long selection; final String file; final byte[] bytes; final long amount; final String exclude;
        PaymentReview(long selection, String file, Transaction tx) { this.selection = selection; this.file = file; this.bytes = tx.serialize(); this.amount=0; this.exclude=""; }
        PaymentReview(long selection,String file,Transaction tx,long amount,String exclude) {this.selection=selection;this.file=file;this.bytes=tx.serialize();this.amount=amount;this.exclude=exclude;}
    }
    private final WalletChangeEventListener changes = wallet -> scheduleSave();

    private BitcoinService(Context context) { this(context, new BitcoinNetworkManager()); }
    // Dependency injection lets integration tests verify the same service without real coins or peers.
    BitcoinService(Context context, BitcoinNetworkManager network) {
        this.context = context.getApplicationContext();
        this.network = network;
        network.setWalletAccess(new BitcoinNetworkManager.WalletAccess() {
            @Override public BitcoinNetworkManager.Capture capture(String file) throws Exception {
                return submit(() -> {
                    BitcoinWallet current = requireWallet();
                    if (!current.getFileName().equals(file)) throw new BitcoinNetworkManager.StateConflict();
                    return BitcoinNetworkManager.capture(current.getWallet(), revision, selection);
                }).get(30, TimeUnit.SECONDS);
            }
            @Override public boolean apply(String file, BitcoinNetworkManager.Capture capture, Wallet wallet) throws Exception {
                return submit(() -> {
                    if (selected == null || !selected.getFileName().equals(file) || selection != capture.selection || revision != capture.revision) return false;
                    org.bitcoinj.core.Context oldContext = bitcoinContext;
                    org.bitcoinj.core.Context.propagate(capture.context);
                    try { WalletStorageManager.updateWallet(context,file,wallet); }
                    catch (Exception error) {
                        org.bitcoinj.core.Context.propagate(oldContext);
                        storageError = "Wallet persistence failed. Sending paused; retry storage.";
                        throw error;
                    }
                    bitcoinContext = capture.context;
                    selected.getWallet().removeChangeEventListener(changes);
                    selected = new BitcoinWallet(file,wallet);
                    wallet.addChangeEventListener(Runnable::run,changes);
                    storageError = ""; revision++;
                    return true;
                }).get(30,TimeUnit.SECONDS);
            }
        });
    }
    public static synchronized BitcoinService get(Context context) {
        if (instance == null) instance = new BitcoinService(context);
        return instance;
    }
    public interface Operation<T> { T run() throws Exception; }
    public <T> CompletableFuture<T> submit(Operation<T> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        worker.execute(() -> {
            org.bitcoinj.core.Context.propagate(bitcoinContext);
            try { result.complete(operation.run()); } catch (Exception error) { result.completeExceptionally(error); }
        });
        return result;
    }
    public String createWallet() throws Exception { return WalletStorageManager.saveWallet(context, WalletPolicy.createWallet()); }
    public String restoreWallet(String phrase, String passphrase) throws Exception {
        return WalletStorageManager.saveWallet(context, WalletPolicy.restoreWallet(phrase, passphrase));
    }
    public BitcoinWallet openWallet(String file) throws Exception {
        if (selected != null && selected.getFileName().equals(file)) return selected;
        closeWallet();
        Wallet wallet = WalletStorageManager.loadWallet(context, file);
        java.io.File baseline = new java.io.File(context.getFilesDir(),file + ".before-electrum");
        if (!baseline.exists()) java.nio.file.Files.copy(new java.io.File(context.getFilesDir(),file).toPath(),baseline.toPath());
        wallet.setTransactionBroadcaster(null);
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
        selection++; revision++; reviews.clear(); recoveryReviews.clear();
        wallet.addChangeEventListener(Runnable::run, changes);
        storageError = "";
        return selected;
    }
    public void connect() throws Exception {
        BitcoinWallet current = requireWallet();
        if (!network.isNetworkStarted()) {
            network.start(context, current.getWallet(), current.getFileName());
        }
    }
    public void reconnect() throws Exception {
        BitcoinWallet current = requireWallet();
        // Persist the latest wallet chain position before closing the current session.
        persist();
        network.stop();
        network.start(context, current.getWallet(), current.getFileName());
    }
    public void closeWallet() throws Exception {
        if (selected != null) {
            network.stop();
            persist();
            selected.getWallet().removeChangeEventListener(changes);
            selected = null;
            selection++; reviews.clear(); recoveryReviews.clear();
        }
    }
    public boolean deleteWallet(String file) throws Exception {
        if (selected != null && selected.getFileName().equals(file)) closeWallet();
        return WalletStorageManager.deleteWallet(context, file);
    }
    public BitcoinWallet requireWallet() {
        BitcoinWallet current = selected;
        if (current == null) throw new IllegalStateException("Open a wallet first.");
        return current;
    }
    public void persist() throws Exception {
        BitcoinWallet current = requireWallet();
        try { WalletStorageManager.updateWallet(context, current.getFileName(), current.getWallet()); storageError = ""; revision++; }
        catch (Exception e) { storageError = "Wallet could not be saved. Stop sending and retry storage."; throw e; }
    }
    private synchronized void scheduleSave() {
        revision++; // Invalidate a captured view immediately, before the delayed disk save.
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
    public String generateAddress() throws Exception {
        String address = requireWallet().getWallet().freshReceiveAddress().toString();
        persist();
        return address;
    }
    public Coin getBalance() { return requireWallet().getBalance(); }
    public ArrayList<BitcoinTransaction> getTransactions() { return requireWallet().getTransactions(); }
    public BitcoinTransaction findTransaction(String id) {
        for (BitcoinTransaction tx : getTransactions()) if (tx.getId().equals(id)) return tx;
        return null;
    }
    public Address validateAddress(String address) { return WalletPolicy.validateAddress(address); }
    public SendRequest preparePayment(String recipient, String amount, String feeRate) throws Exception {
        return prepareReservedPayment(recipient,amount,feeRate,"");
    }
    SendRequest prepareReservedPayment(String recipient,String amount,String feeRate,String exclude) throws Exception {
        requireReady();
        SendRequest request = BitcoinPayments.prepare(requireWallet().getWallet(), recipient, amount, feeRate);
        checkReservations(WalletPolicy.parseAmount(amount).value,request.tx.getFee().value,exclude);
        persist(); // Save any new change keys before displaying the review.
        reviews.put(request,new PaymentReview(selection,requireWallet().getFileName(),request.tx,WalletPolicy.parseAmount(amount).value,exclude));
        return request;
    }
    public Transaction sendBitcoin(SendRequest reviewed) throws Exception {
        Transaction transaction = signReviewed(reviewed);
        transaction.getConfidence().setSource(org.bitcoinj.core.TransactionConfidence.Source.SELF);
        requireWallet().getWallet().commitTx(transaction);
        persist();
        return transaction;
    }
    Transaction signReviewed(SendRequest reviewed) throws Exception {
        requireReady();
        Wallet wallet = requireWallet().getWallet();
        PaymentReview review=reviews.remove(reviewed);
        validateReview(review,reviewed.tx);
        if (reviewed.tx.getFee() == null || !reviewed.tx.getFee().isPositive()
                || reviewed.tx.getFee().isGreaterThan(WalletPolicy.MAX_TRANSACTION_FEE))
            throw new IllegalStateException("Review a transaction with a positive fee within the 0.01 BTC safety limit.");
        checkReservations(review.amount,reviewed.tx.getFee().value,review.exclude);
        for (int index = 0; index < reviewed.tx.getInputs().size(); index++) {
            org.bitcoinj.core.TransactionInput input = reviewed.tx.getInput(index);
            org.bitcoinj.core.TransactionOutput current = null;
            for (org.bitcoinj.core.TransactionOutput candidate : wallet.calculateAllSpendCandidates(true,true)) {
                if (candidate.getOutPointFor().equals(input.getOutpoint())) { current = candidate; break; }
            }
            if (current == null || input.getConnectedOutput() == null || !current.isAvailableForSpending()
                    || !current.getValue().equals(input.getConnectedOutput().getValue())
                    || !java.util.Arrays.equals(current.getScriptPubKey().getProgram(),input.getConnectedOutput().getScriptPubKey().getProgram()))
                throw new IllegalStateException("Balance changed. Review the payment again.");
            // Rebind without marking the output spent: signing replaces immutable input objects.
            reviewed.tx.replaceInput(index,new org.bitcoinj.core.TransactionInput(reviewed.tx,input.getScriptBytes(),
                    current.getOutPointFor(),input.getSequenceNumber(),current.getValue(),input.getWitness()));
        }
        reviewed.signInputs = true;
        wallet.signTransaction(reviewed);
        for (org.bitcoinj.core.TransactionInput input : reviewed.tx.getInputs()) input.verify();
        return reviewed.tx;
    }
    public void rescan() throws Exception {
        BitcoinWallet current = requireWallet();
        persist();
        network.rescan(context, current.getWallet(), current.getFileName());
    }
    public CompletableFuture<Transaction> broadcast(Transaction tx) {
        // relays only an exact transaction already saved by this selected wallet.
        // The UI reaches this method after review/authentication, or authenticated retry.
        requireReady();
        Transaction saved = requireWallet().getWallet().getTransaction(tx.getTxId());
        if (saved == null || saved.getConfidence().getSource() != org.bitcoinj.core.TransactionConfidence.Source.SELF
                || saved.getConfidence().getConfidenceType() != org.bitcoinj.core.TransactionConfidence.ConfidenceType.PENDING
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
    void requireVerifiedState() {
        requireWallet();
        if (!storageError.isEmpty()) throw new IllegalStateException(storageError);
        if (!network.isReady()) throw new IllegalStateException(network.getStatus());
    }
    public void requireReady() {
        requireWallet();
        if (!storageError.isEmpty()) throw new IllegalStateException(storageError);
        if (!network.isReady()) throw new IllegalStateException(network.getStatus());
        try { LegacyScheduledPayments.checkBeforeSpending(context,requireWallet()); InheritanceJournal.checkBeforeSpending(context,requireWallet()); }
        catch(Exception error){throw new IllegalStateException("Resolve the preserved signed transaction journal before spending: "+error.getMessage(),error);}
    }
    private void checkReservations(long amount,long fee,String exclude)throws Exception {
        LegacyScheduledPayments.checkBeforeSpending(context,requireWallet());
        InheritanceJournal.checkBeforeSpending(context,requireWallet());
    }
    public String getSendBlockReason() {
        if (!storageError.isEmpty()) return storageError;
        if (!network.isReady()) return network.getStatus();
        try { LegacyScheduledPayments.checkBeforeSpending(context,requireWallet()); InheritanceJournal.checkBeforeSpending(context,requireWallet()); }
        catch(Exception error){return error.getMessage();}
        return "";
    }
    public BitcoinNetworkManager getNetwork() { return network; }
    public String getStatus() { return storageError.isEmpty() ? network.getStatus() : storageError; }
    void rememberRecovery(Transaction tx) { recoveryReviews.put(tx,new PaymentReview(selection,requireWallet().getFileName(),tx)); }
    void validateRecovery(Transaction tx) { validateReview(recoveryReviews.get(tx),tx); }
    private void validateReview(PaymentReview review, Transaction tx) {
        if (review == null || review.selection != selection || !review.file.equals(requireWallet().getFileName())
                || !java.util.Arrays.equals(review.bytes,tx.serialize())) throw new IllegalStateException("Wallet or reviewed transaction changed. Review again.");
    }
    void shutdown() throws Exception { closeWallet(); network.shutdown(); worker.shutdown(); }
}




