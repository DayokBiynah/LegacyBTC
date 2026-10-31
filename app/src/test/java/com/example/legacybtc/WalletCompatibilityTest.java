package com.example.legacybtc;

import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.Wallet;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.crypto.KeyGenerator;
import static org.junit.Assert.*;

public class WalletCompatibilityTest {
    @Test public void existingEnvelopePreservesKeysIssuedBranchesAndPendingBytes() throws Exception {
        new org.bitcoinj.core.Context();
        Wallet original = WalletPolicy.createWallet();
        String identity = WalletPolicy.walletIdentity(original);
        for (int i = 0; i < 25; i++) {
            original.freshReceiveAddress();
            original.getActiveKeyChain().getKey(org.bitcoinj.wallet.KeyChain.KeyPurpose.CHANGE);
        }
        Transaction pending = new Transaction();
        pending.addInput(Sha256Hash.of(new byte[]{71}), 0, ScriptBuilder.createEmpty());
        pending.addOutput(Coin.CENT, original.currentReceiveAddress());
        pending.getConfidence().setSource(TransactionConfidence.Source.SELF);
        original.commitTx(pending);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        original.saveToFileStream(bytes);
        javax.crypto.SecretKey key = KeyGenerator.getInstance("AES").generateKey();
        byte[] legacyEnvelope = WalletCipher.encrypt(bytes.toByteArray(), key);
        assertEquals(12, legacyEnvelope[0]);
        Wallet restored = Wallet.loadFromFileStream(new ByteArrayInputStream(WalletCipher.decrypt(legacyEnvelope, key)));
        assertEquals(identity, WalletPolicy.walletIdentity(restored));
        assertEquals(original.currentReceiveAddress(), restored.currentReceiveAddress());
        assertEquals(original.getActiveKeyChain().getIssuedInternalKeys(), restored.getActiveKeyChain().getIssuedInternalKeys());
        assertArrayEquals(pending.serialize(), restored.getTransaction(pending.getTxId()).serialize());
        assertTrue(restored.currentReceiveKey().hasPrivKey());
        assertEquals(original.getActiveKeyChain().getMnemonicCode(), restored.getActiveKeyChain().getMnemonicCode());
    }
}
