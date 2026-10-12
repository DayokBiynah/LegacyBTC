package com.example.legacybtc;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.Wallet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Android Keystore, file compatibility, plan packages, and actual Activity lifecycle checks. */
@RunWith(AndroidJUnit4.class)
public class WalletIntegrationTest {
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }

    @Test public void encryptedUpdatesPreserveIdentityAndDuplicateDetection() throws Exception {
        Context context = context();
        Wallet wallet = WalletPolicy.createWallet();
        String file = WalletStorageManager.saveWallet(context, wallet);
        try {
            String identity = WalletPolicy.walletIdentity(wallet);
            String address = wallet.freshReceiveAddress().toString();
            WalletStorageManager.updateWallet(context, file, wallet);
            Wallet loaded = WalletStorageManager.loadWallet(context, file);
            assertEquals(identity, WalletPolicy.walletIdentity(loaded));
            assertEquals(address, loaded.currentReceiveAddress().toString());
            Wallet original = WalletPolicy.restoreWallet(String.join(" ", wallet.getActiveKeyChain().getMnemonicCode()), "");
            assertEquals(file, WalletStorageManager.saveWallet(context, original));
            assertEquals(file, WalletStorageManager.findWallet(context, original));
            // Stored data must be an authenticated ciphertext envelope, not a protobuf wallet.
            try (java.io.FileInputStream input = context.openFileInput(file)) { assertEquals(12, input.read()); }
            assertThrows(IllegalArgumentException.class, () -> WalletStorageManager.loadWallet(context, "../wallet.enc"));
        } finally { WalletStorageManager.deleteWallet(context, file); }
    }

    @Test public void planPackagesRoundTripAndRejectTamperedAmounts() throws Exception {
        Context context = context();
        Wallet wallet = WalletPolicy.createWallet();
        String file = WalletStorageManager.saveWallet(context, wallet);
        ECKey ownerKey = wallet.currentReceiveKey(), beneficiaryKey = new ECKey();
        Owner owner = new Owner("Test owner", Hex.encode(ownerKey.getPubKey()), ownerKey.toAddress(ScriptType.P2WPKH, WalletPolicy.NETWORK).toString());
        Beneficiary beneficiary = new Beneficiary("Test beneficiary", Hex.encode(beneficiaryKey.getPubKey()), beneficiaryKey.toAddress(ScriptType.P2WPKH, WalletPolicy.NETWORK).toString());
        InheritancePlan plan = new InheritancePlan(owner, beneficiary, Coin.CENT, 3600, java.time.Instant.now().getEpochSecond());
        Transaction funding = new Transaction();
        funding.addInput(Sha256Hash.of(new byte[]{10}), 0, ScriptBuilder.createEmpty());
        funding.addOutput(plan.getAmount(), InheritanceScript.outputScript(plan));
        plan.attachFunding(funding, 0);
        try {
            InheritanceRepository repository = new InheritanceRepository(context);
            repository.save(file, plan);
            InheritancePlan loaded = repository.find(file, plan.getId());
            assertEquals(plan.getFundingHex(), loaded.getFundingHex());
            assertEquals(InheritanceScript.address(plan), InheritanceScript.address(loaded));
            org.json.JSONObject exported = InheritanceRepository.encode(plan);
            assertFalse(exported.toString().contains("privateKey"));
            exported.put("satoshis", plan.getAmount().value + 1);
            assertThrows(IllegalArgumentException.class, () -> InheritanceRepository.decode(exported));
            assertEquals(1, java.util.Arrays.stream(WalletStorageManager.getSavedWalletFiles(context)).filter(file::equals).count());
        } finally {
            WalletStorageManager.deleteWallet(context, file);
            new android.util.AtomicFile(new java.io.File(context.getFilesDir(), "inheritance_" + file)).delete();
        }
    }

    @Test public void allProposalScreensLaunchAndClaimsStartDisabled() throws Exception {
        Context context = context();
        String file = WalletStorageManager.saveWallet(context, WalletPolicy.createWallet());
        BitcoinService service = BitcoinService.get(context);
        try {
            checkScreen(context, CreateWalletActivity.class, null, R.id.generateWalletButton, R.id.restoreWalletButton,
                    R.id.recoveryPhraseInput, R.id.passphraseInput, R.id.continueButton, R.id.backButton);
            checkScreen(context, RestoreWalletActivity.class, null, R.id.recoveryPhraseInput, R.id.restoreWalletButton, R.id.continueButton);
            checkScreen(context, WalletDashboardActivity.class, file, R.id.balanceText, R.id.sendButton, R.id.receiveButton, R.id.inheritanceButton, R.id.historyButton, R.id.settingsButton);
            checkScreen(context, SendBitcoinActivity.class, file, R.id.recipientInput, R.id.amountInput, R.id.feeInput, R.id.sendButton, R.id.cancelButton);
            checkScreen(context, ReceiveBitcoinActivity.class, file, R.id.walletAddressText, R.id.qrImage, R.id.copyAddressButton, R.id.generateAddressButton, R.id.shareButton);
            checkScreen(context, InheritanceSetupActivity.class, file, R.id.addBeneficiaryButton, R.id.setPeriodButton, R.id.savePlanButton, R.id.updatePlanButton, R.id.cancelPlanButton);
            checkScreen(context, TransactionHistoryActivity.class, file, R.id.transactionsContainer, R.id.backButton);
            try (ActivityScenario<BeneficiaryRecoveryActivity> screen = ActivityScenario.launch(new Intent(context, BeneficiaryRecoveryActivity.class).putExtra("wallet_file", file))) {
                screen.onActivity(activity -> {
                    assertNotNull(activity.findViewById(R.id.checkStatusButton));
                    assertNotNull(activity.findViewById(R.id.viewDetailsButton));
                    assertFalse(activity.findViewById(R.id.claimButton).isEnabled());
                });
                screen.recreate();
                screen.onActivity(activity -> assertFalse(activity.findViewById(R.id.claimButton).isEnabled()));
            }
        } finally {
            service.submit(() -> { service.closeWallet(); return true; }).get(45, TimeUnit.SECONDS);
            WalletStorageManager.deleteWallet(context, file);
        }
    }
    @Test public void receiveQrContainsTheDisplayedTestnetAddress() throws Exception {
        Context context = context();
        String file = WalletStorageManager.saveWallet(context, WalletPolicy.createWallet());
        BitcoinService service = BitcoinService.get(context);
        try (ActivityScenario<ReceiveBitcoinActivity> screen = ActivityScenario.launch(
                new Intent(context, ReceiveBitcoinActivity.class).putExtra("wallet_file", file))) {
            java.util.concurrent.atomic.AtomicReference<android.graphics.Bitmap> image = new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicReference<String> address = new java.util.concurrent.atomic.AtomicReference<>();
            long deadline = android.os.SystemClock.elapsedRealtime() + 45_000;
            // The QR is built on the wallet worker, so wait for its actual displayed bitmap.
            while (image.get() == null && android.os.SystemClock.elapsedRealtime() < deadline) {
                screen.onActivity(activity -> {
                    android.graphics.drawable.Drawable drawable = ((android.widget.ImageView) activity.findViewById(R.id.qrImage)).getDrawable();
                    if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                        image.set(((android.graphics.drawable.BitmapDrawable) drawable).getBitmap());
                        address.set(((TextView) activity.findViewById(R.id.walletAddressText)).getText().toString());
                    }
                });
                if (image.get() == null) android.os.SystemClock.sleep(100);
            }
            assertNotNull("Receive screen did not produce a QR code", image.get());
            WalletPolicy.validateAddress(address.get());
            android.graphics.Bitmap bitmap = image.get();
            int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
            bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
            com.google.zxing.BinaryBitmap binary = new com.google.zxing.BinaryBitmap(
                    new com.google.zxing.common.HybridBinarizer(new com.google.zxing.RGBLuminanceSource(
                            bitmap.getWidth(), bitmap.getHeight(), pixels)));
            assertEquals("bitcoin:" + address.get(), new com.google.zxing.MultiFormatReader().decode(binary).getText());
        } finally {
            service.submit(() -> { service.closeWallet(); return true; }).get(45, TimeUnit.SECONDS);
            WalletStorageManager.deleteWallet(context, file);
        }
    }
    private <T extends android.app.Activity> void checkScreen(Context context, Class<T> type, String file, int... controls) {
        Intent intent = new Intent(context, type);
        if (file != null) intent.putExtra("wallet_file", file);
        try (ActivityScenario<T> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                for (int id : controls) assertNotNull(type.getSimpleName() + " missing control", activity.findViewById(id));
                assertTrue((activity.getWindow().getAttributes().flags & android.view.WindowManager.LayoutParams.FLAG_SECURE) != 0);
            });
        }
    }
}
