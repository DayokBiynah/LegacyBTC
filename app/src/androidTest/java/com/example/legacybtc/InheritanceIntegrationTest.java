package com.example.legacybtc;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.script.ScriptBuilder;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Runs the real plan/storage/signing services against synthetic funding and a simulated chain clock. */
@RunWith(AndroidJUnit4.class)
public class InheritanceIntegrationTest {
    private static final class TestNetwork extends BitcoinNetworkManager {
        boolean ready = true;
        long median;
        @Override public boolean isReady() { return ready; }
        @Override public void stop() { }
        @Override public void start(Context context, Wallet wallet, String file) { }
        @Override public void rescan(Context context, Wallet wallet, String file) { }
        @Override public long medianTimePast() { return median; }
    }
    @Test public void fundingExportImportAndClaimEnforceAllConditions() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Wallet owner = WalletPolicy.createWallet(), beneficiary = WalletPolicy.createWallet();
        new org.bitcoinj.core.Context();
        Transaction income = new Transaction();
        income.addInput(Sha256Hash.of(new byte[]{31}), 0, ScriptBuilder.createEmpty());
        income.addOutput(Coin.COIN, owner.currentReceiveAddress()); owner.commitTx(income);
        confirm(owner, income);
        String ownerFile = WalletStorageManager.saveWallet(context, owner), beneficiaryFile = WalletStorageManager.saveWallet(context, beneficiary);
        TestNetwork ownerNetwork = new TestNetwork(), beneficiaryNetwork = new TestNetwork();
        BitcoinService ownerService = new BitcoinService(context, ownerNetwork), beneficiaryService = new BitcoinService(context, beneficiaryNetwork);
        InheritanceService ownerPlans = new InheritanceService(ownerService, new InheritanceRepository(context));
        InheritanceService beneficiaryPlans = new InheritanceService(beneficiaryService, new InheritanceRepository(context));
        try {
            ownerService.submit(() -> ownerService.openWallet(ownerFile)).get(20, TimeUnit.SECONDS);
            beneficiaryService.submit(() -> beneficiaryService.openWallet(beneficiaryFile)).get(20, TimeUnit.SECONDS);
            String key = beneficiary.currentReceiveKey().getPublicKeyAsHex();
            Beneficiary person = ownerPlans.addBeneficiary("Synthetic beneficiary", key, beneficiary.currentReceiveAddress().toString());
            InheritancePlan draft = ownerService.submit(() -> ownerPlans.createInheritancePlan("Synthetic owner", person, "0.01", 3600)).get();
            InheritancePlan updated = ownerService.submit(() -> ownerPlans.updatePlan(draft.getId(), person, "0.02", 7200)).get();
            assertEquals(draft.getId(), updated.getId());
            assertEquals(Coin.CENT.multiply(2), updated.getAmount());
            SendRequest review = ownerService.submit(() -> ownerPlans.prepareFunding(updated.getId(), "5")).get();
            Transaction funding = ownerService.submit(() -> ownerPlans.fundPlan(updated.getId(), review)).get();
            InheritancePlan funded = ownerService.submit(() -> ownerPlans.getPlan(updated.getId())).get();
            assertEquals(InheritancePlan.Status.FUNDING_PENDING, ownerPlans.checkInheritanceStatus(funded));
            assertThrows(IllegalStateException.class, () -> ownerPlans.updatePlan(funded.getId(), person, "0.03", 3600));
            assertThrows(IllegalStateException.class, () -> ownerPlans.cancelDraft(funded.getId()));
            confirm(ownerService.requireWallet().getWallet(), funding);
            ownerNetwork.median = funded.getUnlockTime();
            assertEquals(InheritancePlan.Status.TIMELOCKED, ownerPlans.checkInheritanceStatus(funded));
            String exported = ownerService.submit(() -> ownerPlans.exportPlan(funded.getId())).get();
            InheritancePlan imported = beneficiaryService.submit(() -> beneficiaryPlans.importPlan(exported)).get();
            Transaction observed = Transaction.read(ByteBuffer.wrap(funding.serialize()));
            beneficiaryService.requireWallet().getWallet().maybeCommitTx(observed);
            confirm(beneficiaryService.requireWallet().getWallet(), observed);
            beneficiaryNetwork.median = funded.getUnlockTime();
            assertEquals(InheritancePlan.Status.TIMELOCKED, beneficiaryPlans.checkInheritanceStatus(imported));
            assertThrows(IllegalStateException.class, () -> beneficiaryPlans.prepareRecovery(imported.getId(), "5", false));
            beneficiaryNetwork.ready = false; beneficiaryNetwork.median++;
            assertEquals(InheritancePlan.Status.UNVERIFIED, beneficiaryPlans.checkInheritanceStatus(imported));
            assertThrows(IllegalStateException.class, () -> beneficiaryPlans.prepareRecovery(imported.getId(), "5", false));
            beneficiaryNetwork.ready = true;
            Transaction claim = beneficiaryService.submit(() -> beneficiaryPlans.prepareRecovery(imported.getId(), "5", false)).get();
            assertEquals(funded.getUnlockTime(), claim.getLockTime());
            // I model chain-time regression after review. Submission must recheck finality
            // rather than trust the earlier enabled button or prebuilt signature.
            beneficiaryNetwork.median = funded.getUnlockTime();
            assertThrows(IllegalStateException.class, () -> beneficiaryPlans.recoverInheritance(imported.getId(), claim, false));
            assertNull(beneficiaryService.requireWallet().getWallet().getTransaction(claim.getTxId()));
            beneficiaryNetwork.median++;
            Transaction saved = beneficiaryService.submit(() -> beneficiaryPlans.recoverInheritance(imported.getId(), claim, false)).get();
            assertNotNull(WalletStorageManager.loadWallet(context, beneficiaryFile).getTransaction(saved.getTxId()));
            assertEquals(InheritancePlan.Status.SPEND_PENDING, beneficiaryPlans.checkInheritanceStatus(imported));
            assertThrows(IllegalStateException.class, () -> beneficiaryPlans.prepareRecovery(imported.getId(), "5", false));
            confirm(beneficiaryService.requireWallet().getWallet(), saved);
            assertEquals(InheritancePlan.Status.SPENT, beneficiaryPlans.checkInheritanceStatus(imported));
        } finally {
            ownerService.submit(() -> { ownerService.shutdown(); return true; }).get(20, TimeUnit.SECONDS);
            beneficiaryService.submit(() -> { beneficiaryService.shutdown(); return true; }).get(20, TimeUnit.SECONDS);
            for (String file : new String[]{ownerFile, beneficiaryFile}) {
                WalletStorageManager.deleteWallet(context, file);
                new android.util.AtomicFile(new java.io.File(context.getFilesDir(), "inheritance_" + file)).delete();
            }
        }
    }
    private static void confirm(Wallet wallet, Transaction tx) throws Exception {
        // Use bitcoinj's block-receive path so serialized test wallets have consistent pools and heights.
        org.bitcoinj.core.Block header = (BuildConfig.MAINNET ? org.bitcoinj.params.MainNetParams.get()
                : org.bitcoinj.params.TestNet3Params.get()).getGenesisBlock();
        org.bitcoinj.core.StoredBlock block = new org.bitcoinj.core.StoredBlock(header, header.getWork(), 100);
        wallet.receiveFromBlock(tx, block, org.bitcoinj.core.AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
        tx.getConfidence().setDepthInBlocks(6);
    }
}
