package com.example.legacybtc;

import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.core.*;
import org.bitcoinj.store.MemoryBlockStore;
import org.junit.Test;
import static org.junit.Assert.*;

public class HeaderValidationTest {
    private static final String MAINNET_BLOCK_ONE = "010000006fe28c0ab6f1b372c1a6a246ae63f74f931e8365e15a089c68d6190000000000982051fd1e4ba744bbbe680e1fee14677ba1a3c3540bf7b1cdb606e857233e0e61bc6649ffff001d01e36299";
    @Test public void consensusVerifierAcceptsHistoricalHeaderAndRejectsTampering() throws Exception {
        new org.bitcoinj.core.Context();
        MemoryBlockStore store = new MemoryBlockStore(org.bitcoinj.params.MainNetParams.get().getGenesisBlock());
        BlockChain chain = new BlockChain(BitcoinNetwork.MAINNET,store);
        assertTrue(chain.add(BitcoinNetworkManager.readHeader(MAINNET_BLOCK_ONE)));
        assertEquals(1,store.getChainHead().getHeight());
        StoredBlock ancestor = ElectrumProof.ancestor(store.getChainHead(),org.bitcoinj.params.MainNetParams.get().getGenesisBlock());
        assertEquals(0,ancestor.getHeight());
        assertEquals(org.bitcoinj.params.MainNetParams.get().getGenesisBlock().getWork(),ancestor.getChainWork());
        assertThrows(java.io.IOException.class,()->ElectrumProof.ancestor(store.getChainHead(),BitcoinNetworkManager.readHeader(MAINNET_BLOCK_ONE)));
        String changed = MAINNET_BLOCK_ONE.substring(0,152) + "00000000";
        assertThrows(VerificationException.class,()->new BlockChain(BitcoinNetwork.MAINNET,new MemoryBlockStore(org.bitcoinj.params.MainNetParams.get().getGenesisBlock()))
                .add(BitcoinNetworkManager.readHeader(changed)));
        assertThrows(java.io.IOException.class,()->BitcoinNetworkManager.readHeader("00"));
    }
}
