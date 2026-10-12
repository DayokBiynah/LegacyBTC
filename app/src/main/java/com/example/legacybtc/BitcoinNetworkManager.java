package com.example.legacybtc;

import android.content.Context;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.CheckpointManager;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.Wallet;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * I manage one wallet's SPV connection to the selected Bitcoin network. bitcoinj verifies
 * block headers and discovers relevant transactions using peers and bloom filters; it is not a
 * full node. Network parameters choose genesis, consensus configuration, default port, and DNS
 * seeds. Each wallet has a separate chain file so opening another wallet cannot skip its
 * history. Mainnet and Testnet use distinct configuration and filenames.
 */
public class BitcoinNetworkManager {
    private volatile PeerGroup peerGroup;
    private SPVBlockStore blockStore;
    private volatile Wallet connectedWallet;
    private volatile boolean networkStarted, synchronizedChain;
    private volatile String status = WalletPolicy.NETWORK_LABEL + ": offline";
    private static NetworkParameters parameters() {
        return BuildConfig.MAINNET ? MainNetParams.get() : TestNet3Params.get();
    }

    /**
     * I receive an Android Context, a wallet, and optionally its chain filename. I enforce the
     * build network, stop the previous session, reconcile the wallet's saved height with its
     * SPV header store, and apply bundled checkpoints only before the earliest known keys.
     * DnsDiscovery obtains candidate peers from network DNS seeds unless a manual host is
     * configured. PeerGroup starts TCP Bitcoin sessions, attaches the wallet for relevant
     * transaction updates, and downloads headers and filtered blocks. Progress callbacks
     * update visible state; exceptions close resources and propagate to the caller.
     * @param context Context supplied by the calling workflow as described above
     * @param wallet Wallet supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     */
    public void start(Context context, Wallet wallet) throws Exception {
        start(context, wallet, "legacybtc_" + WalletPolicy.NETWORK_ID);
    }

    /**
     * I receive an Android Context, a wallet, and optionally its chain filename. I enforce the
     * build network, stop the previous session, reconcile the wallet's saved height with its
     * SPV header store, and apply bundled checkpoints only before the earliest known keys.
     * DnsDiscovery obtains candidate peers from network DNS seeds unless a manual host is
     * configured. PeerGroup starts TCP Bitcoin sessions, attaches the wallet for relevant
     * transaction updates, and downloads headers and filtered blocks. Progress callbacks
     * update visible state; exceptions close resources and propagate to the caller.
     * @param context Context supplied by the calling workflow as described above
     * @param wallet Wallet supplied by the calling workflow as described above
     * @param walletFile String supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     */
    public void start(Context context, Wallet wallet, String walletFile) throws Exception {
        WalletPolicy.requireNetwork(wallet);
        stop();
        connectedWallet = wallet;
        status = WalletPolicy.NETWORK_LABEL + ": discovering peers";
        try {
            // A separate chain prevents opening one wallet from skipping another wallet's history.
            blockStore = new SPVBlockStore(parameters(), new File(context.getFilesDir(), walletFile + ".spvchain"));
            if (wallet.getLastBlockSeenHash() == null && blockStore.getChainHead().getHeight() > 0) {
                blockStore.clear();
            } else if (wallet.getLastBlockSeenHash() != null
                    && !blockStore.getChainHead().getHeader().getHash().equals(wallet.getLastBlockSeenHash())) {
                StoredBlock savedHead = blockStore.get(wallet.getLastBlockSeenHash());
                if (savedHead != null) blockStore.setChainHead(savedHead);
                else { blockStore.clear(); WalletReplay.resetForReplay(wallet); }
            }
            if (blockStore.getChainHead().getHeight() == 0 && wallet.earliestKeyCreationTime().isAfter(Instant.EPOCH)) {
                // Bundled bitcoinj checkpoints skip headers before any keys or watched scripts existed.
                // Restored seeds with unknown birthdays still replay from genesis.
                try (java.io.InputStream checkpoints = CheckpointManager.openStream(parameters())) {
                    if (checkpoints != null) CheckpointManager.checkpoint(parameters(), checkpoints, blockStore, wallet.earliestKeyCreationTime());
                }
            }
            BlockChain chain = new BlockChain(WalletPolicy.NETWORK, wallet, blockStore);
            peerGroup = new PeerGroup(WalletPolicy.NETWORK, chain);
            peerGroup.setUserAgent("LegacyBTC", "1.0");
            peerGroup.setMaxConnections(4);
            peerGroup.setMinBroadcastConnections(1);
            peerGroup.setRequiredServices(5); // Full blockchain and BIP-37 bloom-filter support.
            peerGroup.addWallet(wallet);
            android.content.SharedPreferences settings = context.getSharedPreferences(WalletPolicy.NETWORK_PREFERENCES, Context.MODE_PRIVATE);
            String host = settings.getString("host", "");
            if (host == null || host.isEmpty()) peerGroup.addPeerDiscovery(new DnsDiscovery(WalletPolicy.NETWORK));
            else {
                peerGroup.setMaxConnections(1);
                NetworkSettings validated = new NetworkSettings(host, Integer.toString(settings.getInt("port", WalletPolicy.DEFAULT_PORT)));
                peerGroup.addAddress(java.net.InetAddress.getByName(validated.getHost()), validated.getPort());
                status = WalletPolicy.NETWORK_LABEL + ": connecting to configured peer";
            }
            peerGroup.addDisconnectedEventListener((peer, count) -> {
                if (count == 0) { synchronizedChain = false; status = WalletPolicy.NETWORK_LABEL + ": reconnecting"; }
            });
            peerGroup.startAsync().get(30, TimeUnit.SECONDS);
            networkStarted = true;
            peerGroup.startBlockChainDownload(new DownloadProgressTracker() {
                @Override protected void progress(double percent, int blocks, Instant date) {
                    synchronizedChain = false;
                    status = String.format(java.util.Locale.ROOT, "%s: syncing %.1f%% • %d blocks remaining", WalletPolicy.NETWORK_LABEL, percent, blocks);
                }
                @Override protected void doneDownload() {
                    synchronizedChain = true;
                    status = WalletPolicy.NETWORK_LABEL + ": synchronized";
                }
            });
        } catch (Exception error) {
            stop();
            status = WalletPolicy.NETWORK_LABEL + " connection failed. Retry when online.";
            throw error;
        }
    }

    /**
     * I clear readiness before asking PeerGroup.stopAsync to stop its network threads. I wait
     * with a finite timeout before closing the SPVBlockStore, because active peers may still
     * use it. A stopped session cannot authorize a spend. Shutdown exceptions propagate rather
     * than pretending the file is safely closed.
     * @throws Exception if the validation, library, or storage operation described above fails
     */
    public void stop() throws Exception {
        synchronizedChain = false;
        networkStarted = false;
        if (peerGroup != null) {
            peerGroup.stopAsync().get(30, TimeUnit.SECONDS);
            peerGroup = null;
        }
        if (blockStore != null) { blockStore.close(); blockStore = null; }
        connectedWallet = null;
        status = WalletPolicy.NETWORK_LABEL + ": offline";
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
        PeerGroup peers = peerGroup;
        if (peers == null || peers.numConnectedPeers() == 0)
            throw new IllegalStateException("Connect to " + WalletPolicy.NETWORK_LABEL + " before broadcasting.");
        return peers.broadcastTransaction(tx).future();
    }
    /**
     * I stop peers and rebuild the per-wallet chain history so a newly watched inheritance
     * address can discover older funding and spends. The service resets chain-derived wallet
     * data while preserving keys and pending transactions and persists that reset. Recovery
     * remains unavailable until the resulting synchronization completes.
     * @param context Context supplied by the calling workflow as described above
     * @param wallet Wallet supplied by the calling workflow as described above
     * @param file String supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     */
    public void rescan(Context context, Wallet wallet, String file) throws Exception {
        stop();
        SPVBlockStore store = new SPVBlockStore(parameters(), new File(context.getFilesDir(), file + ".spvchain"));
        try { store.clear(); } finally { store.close(); }
        start(context, wallet, file);
    }
    public boolean isReady() {
        PeerGroup peers = peerGroup;
        return synchronizedChain && peers != null && peers.numConnectedPeers() > 0;
    }
    public int getConnectedPeerCount() { PeerGroup peers = peerGroup; return peers == null ? 0 : peers.numConnectedPeers(); }
    public Wallet getConnectedWallet() { return connectedWallet; }
    public boolean isNetworkStarted() { return networkStarted; }
    public String getStatus() { return status; }

    /**
     * I retrieve the best verified header and loop backward through eleven SPV headers. After
     * sorting their epoch-second timestamps, I return the middle value required by BIP-113.
     * Missing headers or an unsynchronized session prevent eligibility. I do not substitute
     * the phone clock for blockchain finality.
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return the median epoch-second timestamp of the last eleven verified headers
     */
    public long medianTimePast() throws Exception {
        if (!isReady() || blockStore == null) throw new IllegalStateException("Synchronize " + WalletPolicy.NETWORK_LABEL + " to check a timelock.");
        ArrayList<Long> times = new ArrayList<>();
        StoredBlock block = blockStore.getChainHead();
        for (int i = 0; i < 11 && block != null; i++) {
            times.add(block.getHeader().getTimeSeconds());
            block = blockStore.get(block.getHeader().getPrevBlockHash());
        }
        if (times.size() != 11) throw new IllegalStateException("Not enough verified headers to check the timelock.");
        Collections.sort(times);
        return times.get(times.size() / 2);
    }
}
