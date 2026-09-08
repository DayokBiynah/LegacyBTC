package com.example.legacybtc;

// Provides access to Android application storage.
import android.content.Context;

// Provides Bitcoin Testnet network information.
import org.bitcoinj.base.BitcoinNetwork;

// Maintains a lightweight Bitcoin blockchain using
// block headers downloaded from Bitcoin peers.
import org.bitcoinj.core.BlockChain;

// Manages connections to Bitcoin network peers.
import org.bitcoinj.core.PeerGroup;

// Discovers Bitcoin peers automatically through DNS.
import org.bitcoinj.net.discovery.DnsDiscovery;

// Provides lightweight SPV blockchain storage.
import org.bitcoinj.store.SPVBlockStore;

// Represents the Bitcoin wallet that will be synchronized.
import org.bitcoinj.wallet.Wallet;

// Provides access to the SPV blockchain file.
import java.io.File;

// Provides Bitcoin Testnet network parameters required
// by bitcoinj's SPV blockchain storage.
import org.bitcoinj.params.TestNet3Params;



/**
 * BitcoinNetworkManager manages LegacyBTC's connection
 * to the Bitcoin Testnet network.
 *
 * The class connects a bitcoinj Wallet to a lightweight
 * SPV blockchain and a group of Bitcoin network peers.
 *
 * This allows wallet transactions and balances to be
 * synchronized without running a full Bitcoin node.
 */
public class BitcoinNetworkManager {

    // Identifies Bitcoin Testnet as the network used
    // during LegacyBTC development and testing.
    private static final BitcoinNetwork NETWORK =
            BitcoinNetwork.TESTNET;

    // Stores the lightweight blockchain headers downloaded
    // from Bitcoin peers.
    private SPVBlockStore blockStore;

    // Processes downloaded Bitcoin blocks and connects
    // relevant transactions to the selected wallet.
    private BlockChain blockChain;

    // Maintains connections to Bitcoin Testnet peers.
    private PeerGroup peerGroup;

    // Stores the wallet currently connected to the network.
    private Wallet connectedWallet;

    // Tracks whether Bitcoin Testnet networking has
    // already been started for the selected wallet.
    private boolean networkStarted = false;


    /**
     * Starts Bitcoin Testnet networking for a selected wallet.
     *
     * The wallet is connected to an SPV blockchain and PeerGroup
     * so bitcoinj can discover peers and synchronize relevant
     * Bitcoin transactions.
     *
     * @param context provides access to LegacyBTC application storage
     * @param wallet  the Bitcoin wallet that will be synchronized
     * @throws Exception if blockchain storage or networking setup fails
     */
    public void start(
            Context context,
            Wallet wallet
    ) throws Exception {

        // Prevents the same Bitcoin networking service
        // from being started more than once.
        if (networkStarted) {
            return;
        }

        // Stores a reference to the wallet being synchronized.
        connectedWallet = wallet;


        /*
         * Creates a file used to store Bitcoin block headers.
         *
         * SPV synchronization stores block headers instead of
         * downloading the complete Bitcoin blockchain.
         */
        File blockChainFile =
                new File(
                        context.getFilesDir(),
                        "legacybtc_testnet.spvchain"
                );


        // Creates or opens the SPV blockchain storage file
        // using Bitcoin Testnet network parameters.
        blockStore =
                new SPVBlockStore(
                        TestNet3Params.get(),
                        blockChainFile
                );

        /*
         * Creates the lightweight Bitcoin blockchain.
         *
         * The selected wallet is attached to the chain so
         * transactions relevant to its addresses can be detected
         * while Bitcoin data is synchronized.
         */
        blockChain =
                new BlockChain(
                        NETWORK,
                        connectedWallet,
                        blockStore
                );


        /*
         * Creates the PeerGroup responsible for managing
         * Bitcoin Testnet peer connections.
         */
        peerGroup =
                new PeerGroup(
                        NETWORK,
                        blockChain
                );


        // Adds the selected wallet to the PeerGroup so its
        // addresses and transactions can be monitored.
        peerGroup.addWallet(
                connectedWallet
        );


        /*
         * Enables automatic Bitcoin Testnet peer discovery.
         *
         * DNS discovery obtains peer addresses without requiring
         * a hard-coded Bitcoin server.
         */
        peerGroup.addPeerDiscovery(
                new DnsDiscovery(
                        NETWORK
                )
        );


        // Starts Bitcoin peer discovery and network connections.
        peerGroup.startAsync();

        // Records that Bitcoin networking has been started
        // so duplicate start attempts can be prevented.
        networkStarted = true;

        // Begins downloading blockchain information from peers.
        peerGroup.startBlockChainDownload(
                null
        );
    }


    /**
     * Stops Bitcoin networking and releases blockchain resources.
     *
     * This should be called when LegacyBTC no longer needs the
     * active Bitcoin network connection.
     */
    public void stop() {

        // Stops active Bitcoin peer connections if networking
        // was previously started.
        if (peerGroup != null) {

            peerGroup.stopAsync();
        }

        // Closes the SPV blockchain storage file.
        if (blockStore != null) {

            try {

                blockStore.close();

            } catch (Exception exception) {

                // No additional action is required if the
                // blockchain storage file is already closed.
            }
        }

        // Records that Bitcoin networking is no longer active.
        networkStarted = false;
    }


    /**
     * Returns the number of Bitcoin peers currently connected
     * to LegacyBTC.
     *
     * @return the number of active Bitcoin peer connections
     */
    public int getConnectedPeerCount() {

        // No peers can exist before PeerGroup has been created.
        if (peerGroup == null) {
            return 0;
        }

        // Returns the current number of connected Bitcoin peers.
        return peerGroup.numConnectedPeers();
    }


    /**
     * Returns the wallet currently attached to the
     * Bitcoin network manager.
     *
     * @return the connected bitcoinj Wallet object
     */
    public Wallet getConnectedWallet() {

        return connectedWallet;
    }

    /**
     * Reports whether Bitcoin Testnet networking
     * has already been started.
     *
     * @return true when networking is active
     */
    public boolean isNetworkStarted() {

        return networkStarted;
    }

}