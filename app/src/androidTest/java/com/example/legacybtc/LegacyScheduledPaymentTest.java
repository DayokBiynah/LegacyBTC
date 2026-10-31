package com.example.legacybtc;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.script.ScriptBuilder;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/** Real Android encryption and read-only retirement compatibility; no network or relay. */
@RunWith(AndroidJUnit4.class)
public class LegacyScheduledPaymentTest {
    @Test public void retiredSignedJournalIsPreservedAndOrphanInputsCannotBeReused()throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        org.bitcoinj.core.Context.propagate(new org.bitcoinj.core.Context());
        Wallet owner=WalletPolicy.createWallet(),recipient=WalletPolicy.createWallet();
        Transaction income=new Transaction();income.addInput(Sha256Hash.of(new byte[]{71}),0,ScriptBuilder.createEmpty());income.addOutput(Coin.COIN,owner.currentReceiveAddress());owner.commitTx(income);
        org.bitcoinj.core.Block header=ElectrumHeaderStore.parameters().getGenesisBlock();
        owner.receiveFromBlock(income,new StoredBlock(header,header.getWork(),100),AbstractBlockChain.NewBlockType.BEST_CHAIN,0);
        income.getConfidence().setDepthInBlocks(6);
        String walletFile=WalletStorageManager.saveWallet(context,owner);
        String id=java.util.UUID.randomUUID().toString(),name="automatic_"+WalletPolicy.NETWORK_ID+"_"+id+".enc";
        try {
            SendRequest payment=BitcoinPayments.prepare(owner,recipient.currentReceiveAddress().toString(),"0.001","2");
            payment.signInputs=true;owner.signTransaction(payment);
            JSONObject record=new JSONObject().put("version",1).put("network",WalletPolicy.NETWORK_ID).put("wallet",walletFile).put("identity",WalletPolicy.walletIdentity(owner))
                .put("id",id).put("state","SIGNED").put("txHex",Hex.encode(payment.tx.serialize())).put("txid",payment.tx.getTxId().toString());
            WalletStorageManager.writeEncrypted(context,name,record.toString().getBytes(StandardCharsets.UTF_8));
            byte[] original=java.nio.file.Files.readAllBytes(new java.io.File(context.getFilesDir(),name).toPath());
            BitcoinWallet selected=new BitcoinWallet(walletFile,owner);
            assertTrue(LegacyScheduledPayments.summary(context,walletFile).contains("DISABLED"));
            assertThrows(IllegalStateException.class,()->LegacyScheduledPayments.checkBeforeSpending(context,selected));
            owner.commitTx(payment.tx);WalletStorageManager.updateWallet(context,walletFile,owner);
            LegacyScheduledPayments.checkBeforeSpending(context,selected);
            assertArrayEquals(original,java.nio.file.Files.readAllBytes(new java.io.File(context.getFilesDir(),name).toPath()));
            LegacyScheduledPayments.disableJobs(context);
        } finally {
            new android.util.AtomicFile(new java.io.File(context.getFilesDir(),name)).delete();
            WalletStorageManager.deleteWallet(context,walletFile);
        }
    }
}
