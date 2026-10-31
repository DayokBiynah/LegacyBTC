package com.example.legacybtc;

import android.content.Context;
import android.content.Intent;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.bitcoinj.base.*;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.Wallet;
import org.junit.Test;
import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class RegressionIntegrationTest {
    static class Offline extends BitcoinNetworkManager {
        int rescans, broadcasts;
        @Override public void start(Context c,Wallet w,String f) { }
        @Override public void stop() { }
        @Override public boolean isNetworkStarted() { return true; }
        @Override public boolean isReady() { return false; }
        @Override public void rescan(Context c,Wallet w,String f) { rescans++; }
        @Override public java.util.concurrent.CompletableFuture<Transaction> broadcast(Transaction tx) { broadcasts++; throw new AssertionError("Unexpected broadcast"); }
    }
    Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    @Test public void backupOnlyWalletAndDeletedWalletRestoreRetainIdentityAndPlans() throws Exception {
        Context c=context(); Wallet wallet=WalletPolicy.createWallet(); String file=WalletStorageManager.saveWallet(c,wallet);
        File base=new File(c.getFilesDir(),file), backup=new File(c.getFilesDir(),file+".bak");
        ECKey other=new ECKey();
        InheritancePlan plan=new InheritancePlan(new Owner("Owner",wallet.currentReceiveKey().getPublicKeyAsHex(),wallet.currentReceiveAddress().toString()),
                new Beneficiary("Beneficiary",other.getPublicKeyAsHex(),other.toAddress(ScriptType.P2WPKH,WalletPolicy.NETWORK).toString()),Coin.CENT,3600,java.time.Instant.now().getEpochSecond());
        InheritanceRepository repo=new InheritanceRepository(c);
        try {
            repo.save(file,plan);
            assertTrue(base.renameTo(backup));
            assertTrue(java.util.Arrays.asList(WalletStorageManager.getSavedWalletFiles(c)).contains(file));
            assertEquals(WalletPolicy.walletIdentity(wallet),WalletPolicy.walletIdentity(WalletStorageManager.loadWallet(c,file)));
            assertTrue(base.exists());
            WalletStorageManager.deleteWallet(c,file);
            Wallet restored=WalletPolicy.restoreWallet(String.join(" ",wallet.getActiveKeyChain().getMnemonicCode()),"");
            assertEquals(file,WalletStorageManager.saveWallet(c,restored));
            assertEquals(plan.getId(),repo.find(file,plan.getId()).getId());
        } finally {
            WalletStorageManager.deleteWallet(c,file);
            new android.util.AtomicFile(new File(c.getFilesDir(),"inheritance_"+file)).delete();
            new android.util.AtomicFile(new File(c.getFilesDir(),"identity_"+file)).delete();
        }
    }
    @Test public void duplicateImportFinishesPreviouslyInterruptedDiscovery() throws Exception {
        Context c=context(); Wallet owner=WalletPolicy.createWallet(), beneficiary=WalletPolicy.createWallet();
        String file=WalletStorageManager.saveWallet(c,beneficiary); Offline network=new Offline(); BitcoinService service=new BitcoinService(c,network);
        InheritanceService inheritance=new InheritanceService(service,new InheritanceRepository(c));
        InheritancePlan plan=new InheritancePlan(new Owner("Owner",owner.currentReceiveKey().getPublicKeyAsHex(),owner.currentReceiveAddress().toString()),
                new Beneficiary("Beneficiary",beneficiary.currentReceiveKey().getPublicKeyAsHex(),beneficiary.currentReceiveAddress().toString()),Coin.CENT,3600,java.time.Instant.now().getEpochSecond());
        Transaction funding=new Transaction(); funding.addInput(Sha256Hash.of(new byte[]{77}),0,ScriptBuilder.createEmpty()); funding.addOutput(plan.getAmount(),InheritanceScript.outputScript(plan)); plan.attachFunding(funding,0);
        try {
            service.submit(()->service.openWallet(file)).get();
            new InheritanceRepository(c).save(file,plan); // Interrupted immediately after repository persistence.
            service.submit(()->inheritance.importPlan(InheritanceRepository.encode(plan).toString())).get();
            service.submit(()->inheritance.importPlan(InheritanceRepository.encode(plan).toString())).get();
            assertEquals(2,network.rescans);
            assertEquals(1,inheritance.getPlans().size());
            assertTrue(service.requireWallet().getWallet().getWatchedScripts().stream().anyMatch(script ->
                    java.util.Arrays.equals(script.getProgram(),InheritanceScript.outputScript(plan).getProgram())));
            assertEquals(0,network.broadcasts);
        } finally {
            service.submit(()->{service.shutdown();return true;}).get(); WalletStorageManager.deleteWallet(c,file);
            new android.util.AtomicFile(new File(c.getFilesDir(),"inheritance_"+file)).delete();
        }
    }
    @Test public void rapidSaveAndCancelledDraftDialogDoNotDuplicateOrCancelPlan() throws Exception {
        Context c=context(); Field singleton=BitcoinService.class.getDeclaredField("instance"); singleton.setAccessible(true);
        BitcoinService old=BitcoinService.get(c); old.submit(()->{old.closeWallet();return true;}).get();
        Offline network=new Offline(); BitcoinService service=new BitcoinService(c,network); singleton.set(null,service);
        String file=WalletStorageManager.saveWallet(c,WalletPolicy.createWallet()); ECKey beneficiary=new ECKey();
        InheritanceService inheritance=new InheritanceService(service,new InheritanceRepository(c));
        try(ActivityScenario<InheritanceSetupActivity> screen=ActivityScenario.launch(new Intent(c,InheritanceSetupActivity.class).putExtra("wallet_file",file))) {
            service.submit(()->true).get(20,TimeUnit.SECONDS); InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            screen.onActivity(activity->{
                ((EditText)activity.findViewById(R.id.beneficiaryNameInput)).setText("Beneficiary");
                ((EditText)activity.findViewById(R.id.beneficiaryKeyInput)).setText(beneficiary.getPublicKeyAsHex());
                ((EditText)activity.findViewById(R.id.beneficiaryAddressInput)).setText(beneficiary.toAddress(ScriptType.P2WPKH,WalletPolicy.NETWORK).toString());
                ((EditText)activity.findViewById(R.id.inheritanceAmountInput)).setText("0.01");
                ((EditText)activity.findViewById(R.id.periodInput)).setText("1");
                activity.findViewById(R.id.savePlanButton).performClick(); activity.findViewById(R.id.savePlanButton).performClick();
            });
            service.submit(()->true).get(20,TimeUnit.SECONDS); InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(1,service.submit(inheritance::getPlans).get().size());
            screen.onActivity(activity->activity.findViewById(R.id.cancelPlanButton).performClick());
            service.submit(()->true).get(20,TimeUnit.SECONDS); InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            screen.onActivity(activity -> {
                try {
                    Field confirmation = InheritanceSetupActivity.class.getDeclaredField("draftConfirmation"); confirmation.setAccessible(true);
                    AlertDialog dialog = (AlertDialog)confirmation.get(activity);
                    assertNotNull(dialog); assertTrue(dialog.isShowing());
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            assertFalse(service.submit(inheritance::getPlans).get().get(0).isCancelled());
            assertEquals(0,network.broadcasts);
        } finally {
            service.submit(()->{service.shutdown();return true;}).get(20,TimeUnit.SECONDS); singleton.set(null,old);
            WalletStorageManager.deleteWallet(c,file); new android.util.AtomicFile(new File(c.getFilesDir(),"inheritance_"+file)).delete();
        }
    }
}
