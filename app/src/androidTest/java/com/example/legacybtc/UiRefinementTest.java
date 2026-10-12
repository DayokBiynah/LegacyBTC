package com.example.legacybtc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.Wallet;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** I check UI wiring and render only disposable fixtures for the selected build network. */
@RunWith(AndroidJUnit4.class)
public class UiRefinementTest {
    private BitcoinService previousService;
    private BitcoinService uiService;
    private java.lang.reflect.Field singleton;
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }

    @Before public void useOfflineNetworkForVisualFixtures() throws Exception {
        previousService = BitcoinService.get(context());
        previousService.submit(() -> { previousService.closeWallet(); return true; }).get(45, TimeUnit.SECONDS);
        uiService = new BitcoinService(context(), new OfflineUiNetwork());
        singleton = BitcoinService.class.getDeclaredField("instance");
        singleton.setAccessible(true);
        singleton.set(null, uiService);
    }
    @After public void restoreApplicationService() throws Exception {
        try { uiService.submit(() -> { uiService.shutdown(); return true; }).get(45, TimeUnit.SECONDS); }
        finally { singleton.set(null, previousService); }
    }
    /** UI fixtures stay offline; the separate Testnet test checks actual peer connectivity. */
    private static final class OfflineUiNetwork extends BitcoinNetworkManager {
        @Override public void start(Context context, Wallet wallet, String file) { }
        @Override public void stop() { }
        @Override public boolean isNetworkStarted() { return true; }
        @Override public boolean isReady() { return false; }
        @Override public int getConnectedPeerCount() { return 0; }
        @Override public String getStatus() { return "Bitcoin " + WalletPolicy.NETWORK_LABEL + " · Offline"; }
    }

    @Test public void createBackupAndRestoreHaveSeparateStates() throws Exception {
        Context context = context();
        BitcoinService service = BitcoinService.get(context);
        Set<String> before = new HashSet<>(java.util.Arrays.asList(WalletStorageManager.getSavedWalletFiles(context)));
        AtomicReference<String> words = new AtomicReference<>();
        try {
            try (ActivityScenario<CreateWalletActivity> screen = ActivityScenario.launch(CreateWalletActivity.class)) {
                screen.onActivity(activity -> {
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.createPath).getVisibility());
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.restorePath).getVisibility());
                    assertEquals(View.GONE, activity.findViewById(R.id.backupSection).getVisibility());
                    activity.findViewById(R.id.generateWalletButton).performClick();
                });
                await(() -> {
                    AtomicReference<Boolean> ready = new AtomicReference<>(false);
                    screen.onActivity(activity -> ready.set(activity.findViewById(R.id.backupSection).getVisibility() == View.VISIBLE));
                    return ready.get();
                });
                screen.recreate();
                await(() -> {
                    AtomicReference<Boolean> ready = new AtomicReference<>(false);
                    screen.onActivity(activity -> ready.set(activity.findViewById(R.id.backupSection).getVisibility() == View.VISIBLE));
                    return ready.get();
                });
                screen.onActivity(activity -> {
                    assertEquals(View.GONE, activity.findViewById(R.id.createPath).getVisibility());
                    assertEquals(View.GONE, activity.findViewById(R.id.restorePath).getVisibility());
                    assertTrue(((TextView) activity.findViewById(R.id.recoveryPhraseText)).getText().toString().startsWith("1.  "));
                    ((CheckBox) activity.findViewById(R.id.backupConfirmed)).setChecked(true);
                    activity.findViewById(R.id.continueButton).performClick();
                });
                await(() -> resumedIs(WalletDashboardActivity.class));
                words.set(String.join(" ", service.requireWallet().getWallet().getActiveKeyChain().getMnemonicCode()));
                finishResumed();
            }
            try (ActivityScenario<CreateWalletActivity> screen = ActivityScenario.launch(CreateWalletActivity.class)) {
                screen.onActivity(activity -> {
                    ((EditText) activity.findViewById(R.id.recoveryPhraseInput)).setText(words.get());
                    activity.findViewById(R.id.restoreWalletButton).performClick();
                });
                await(() -> {
                    AtomicReference<Boolean> ready = new AtomicReference<>(false);
                    screen.onActivity(activity -> ready.set(activity.findViewById(R.id.walletReadySection).getVisibility() == View.VISIBLE));
                    return ready.get();
                });
                screen.onActivity(activity -> {
                    assertEquals(View.GONE, activity.findViewById(R.id.backupSection).getVisibility());
                    assertEquals("", ((TextView) activity.findViewById(R.id.recoveryPhraseText)).getText().toString());
                    assertEquals("", ((EditText) activity.findViewById(R.id.recoveryPhraseInput)).getText().toString());
                });
            }
        } finally {
            words.set(null); // Recovery words are never written into screenshots or reports.
            service.submit(() -> { service.closeWallet(); return true; }).get(45, TimeUnit.SECONDS);
            for (String file : WalletStorageManager.getSavedWalletFiles(context)) {
                if (!before.contains(file)) WalletStorageManager.deleteWallet(context, file);
            }
        }
    }

    @Test public void screensRenderAndNavigationKeepsExistingDestinations() throws Exception {
        Context context = context();
        assertTrue(ColorUtils.calculateContrast(context.getColor(R.color.ui_text), context.getColor(R.color.ui_surface)) >= 4.5);
        assertTrue(ColorUtils.calculateContrast(context.getColor(R.color.ui_secondary), context.getColor(R.color.ui_surface)) >= 4.5);
        assertTrue(ColorUtils.calculateContrast(context.getColor(R.color.ui_on_accent), context.getColor(R.color.ui_accent)) >= 4.5);
        assertTrue(ColorUtils.calculateContrast(context.getColor(R.color.ui_control_border), context.getColor(R.color.ui_surface)) >= 3);
        Wallet wallet = WalletPolicy.createWallet();
        Transaction incoming = new Transaction();
        incoming.addInput(Sha256Hash.of(new byte[]{55, 61}), 0, ScriptBuilder.createEmpty());
        incoming.addOutput(Coin.CENT, wallet.currentReceiveAddress());
        wallet.maybeCommitTx(incoming);
        String file = WalletStorageManager.saveWallet(context, wallet);
        BitcoinService service = BitcoinService.get(context);
        try {
            service.submit(() -> service.openWallet(file)).get(45, TimeUnit.SECONDS);
            ECKey key = new ECKey();
            InheritanceService inheritance = new InheritanceService(context);
            InheritancePlan plan = service.submit(() -> inheritance.createInheritancePlan("Test owner",
                    inheritance.addBeneficiary("Test beneficiary", Hex.encode(key.getPubKey()), key.toAddress(ScriptType.P2WPKH, WalletPolicy.NETWORK).toString()), "0.005", 3600)).get();
            render(MainActivity.class, null, "welcome", null);
            render(CreateWalletActivity.class, null, "create-restore", null);
            render(RestoreWalletActivity.class, null, "restore", null);
            render(OpenWalletActivity.class, null, "saved-wallets", null);
            render(WalletDashboardActivity.class, file, "dashboard", null);
            render(SendBitcoinActivity.class, file, "send", null);
            render(ReceiveBitcoinActivity.class, file, "receive", null);
            render(TransactionHistoryActivity.class, file, "history", null);
            render(InheritanceSetupActivity.class, file, "inheritance", plan.getId());
            render(BeneficiaryRecoveryActivity.class, file, "recovery", plan.getId());
            render(SettingsActivity.class, file, "settings", null);
            try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
                int[] buttons = {R.id.createWalletButton, R.id.restoreWalletButton, R.id.openWalletButton};
                Class<?>[] destinations = {CreateWalletActivity.class, RestoreWalletActivity.class, OpenWalletActivity.class};
                for (int index = 0; index < buttons.length; index++) {
                    int button = buttons[index]; Class<?> destination = destinations[index];
                    screen.onActivity(activity -> activity.findViewById(button).performClick());
                    await(() -> resumedIs(destination)); finishResumed();
                    await(() -> resumedIs(MainActivity.class));
                }
            }
            try (ActivityScenario<WalletDashboardActivity> screen = ActivityScenario.launch(new Intent(context, WalletDashboardActivity.class).putExtra("wallet_file", file))) {
                await(() -> { try { return service.requireWallet().getFileName().equals(file); } catch (Exception e) { return false; } });
                service.submit(() -> true).get(45, TimeUnit.SECONDS);
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                int[] buttons = {R.id.sendButton, R.id.receiveButton, R.id.historyButton, R.id.inheritanceButton, R.id.settingsButton};
                Class<?>[] destinations = {SendBitcoinActivity.class, ReceiveBitcoinActivity.class, TransactionHistoryActivity.class, InheritanceSetupActivity.class, SettingsActivity.class};
                for (int index = 0; index < buttons.length; index++) {
                    int button = buttons[index]; Class<?> destination = destinations[index];
                    screen.onActivity(activity -> activity.findViewById(button).performClick());
                    await(() -> resumedIs(destination));
                    finishResumed();
                    await(() -> resumedIs(WalletDashboardActivity.class));
                }
            }
        } finally {
            service.submit(() -> { service.closeWallet(); return true; }).get(45, TimeUnit.SECONDS);
            WalletStorageManager.deleteWallet(context, file);
            new android.util.AtomicFile(new File(context.getFilesDir(), "inheritance_" + file)).delete();
        }
    }

    private <T extends Activity> void render(Class<T> type, String file, String name, String plan) throws Exception {
        Intent intent = new Intent(context(), type);
        if (file != null) intent.putExtra("wallet_file", file);
        if (plan != null) intent.putExtra("plan_id", plan);
        try (ActivityScenario<T> screen = ActivityScenario.launch(intent)) {
            BitcoinService.get(context()).submit(() -> true).get(45, TimeUnit.SECONDS);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            screen.onActivity(activity -> {
                if (type == InheritanceSetupActivity.class) {
                    View select = activity.findViewById(R.id.selectPlanButton);
                    if (select != null) select.performClick();
                }
            });
            // Give updated text a layout pass before measuring or drawing the screen.
            android.os.SystemClock.sleep(500);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            screen.onActivity(activity -> {
                ViewGroup content = activity.findViewById(android.R.id.content);
                View scroll = content.getChildAt(0);
                View body = ((ViewGroup) scroll).getChildAt(0);
                assertTrue(type.getSimpleName() + " has no content", body.getHeight() > 0);
                checkBounds(body, body.getWidth());
                capture(activity, body, name);
            });
        }
    }
    @Test public void compactAndLargeFontLayoutsStayScrollable() {
        int[] layouts = {R.layout.activity_main, R.layout.activity_create_wallet, R.layout.activity_restore_wallet,
                R.layout.activity_open_wallet, R.layout.activity_dashboard, R.layout.activity_send,
                R.layout.activity_receive, R.layout.activity_history, R.layout.activity_inheritance_setup,
                R.layout.activity_recovery, R.layout.activity_settings};
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            screen.onActivity(activity -> {
                for (float scale : new float[]{1.5f, 2f}) {
                    android.content.res.Configuration config = new android.content.res.Configuration(activity.getResources().getConfiguration());
                    config.fontScale = scale; config.screenWidthDp = 320; config.screenHeightDp = 480;
                    Context themed = new android.view.ContextThemeWrapper(activity.createConfigurationContext(config), R.style.Theme_LegacyBTC);
                    float density = themed.getResources().getDisplayMetrics().density;
                    int width = Math.round(320 * density), height = Math.round(480 * density);
                    for (int layout : layouts) {
                        ViewGroup root = (ViewGroup) activity.getLayoutInflater().cloneInContext(themed).inflate(layout, null);
                        View feedback = root.findViewById(R.id.errorText);
                        if (feedback != null) feedback.setVisibility(View.GONE);
                        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                        root.layout(0, 0, width, height);
                        assertTrue(root instanceof android.widget.ScrollView);
                        checkBounds(root.getChildAt(0), width);
                        if (scale == 2f && layout == R.layout.activity_dashboard) capture(activity, root.getChildAt(0), "large-font-dashboard");
                        if (scale == 2f && layout == R.layout.activity_inheritance_setup) capture(activity, root.getChildAt(0), "large-font-inheritance");
                    }
                }
            });
        }
    }
    private void checkBounds(View view, int width) {
        if (view.getVisibility() != View.VISIBLE) return;
        assertTrue("A view exceeded the screen width", view.getWidth() <= width);
        if (view instanceof android.widget.Button) {
            float density = view.getResources().getDisplayMetrics().density;
            assertTrue("Button touch target is too short", view.getHeight() / density >= 48);
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) checkBounds(((ViewGroup) view).getChildAt(i), width);
    }
    private void capture(Activity activity, View body, String name) {
        File directory = new File(activity.getExternalFilesDir(null), "ui-review");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap bitmap = Bitmap.createBitmap(body.getWidth(), body.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(activity.getColor(R.color.ui_background));
        body.draw(canvas);
        try (FileOutputStream out = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        } catch (Exception error) { throw new AssertionError(error); }
        // Test APK cleanup removes private external files. Public fixture images survive for review.
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name + ".png");
            values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LegacyBTC-ui-review");
            android.net.Uri uri = activity.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            assertNotNull(uri);
            try (java.io.OutputStream out = activity.getContentResolver().openOutputStream(uri)) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
            } catch (Exception error) { throw new AssertionError(error); }
        }
        bitmap.recycle();
    }
    private boolean resumedIs(Class<?> type) {
        AtomicReference<Boolean> found = new AtomicReference<>(false);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) if (type.isInstance(activity)) found.set(true);
        });
        return found.get();
    }
    private void finishResumed() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) activity.finish();
        });
    }
    private interface Condition { boolean ready() throws Exception; }
    private void await(Condition condition) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 45_000;
        while (!condition.ready()) {
            if (android.os.SystemClock.elapsedRealtime() > deadline) fail("UI operation timed out");
            android.os.SystemClock.sleep(100);
        }
    }
}
