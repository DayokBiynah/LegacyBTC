package com.example.legacybtc;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

/**
 * I reopen the selected encrypted wallet from a filename in the Intent after process
 * recreation. A Handler refreshes public screen information while resumed and stops callbacks
 * while paused. Child screens override refresh for their own content. The shared service
 * serializes wallet changes; connection readiness controls spending and verified recovery.
 */
public abstract class SelectedWalletActivity extends WalletActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    protected boolean walletReady;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (walletReady) {
                TextView network = findViewById(R.id.networkStatusText);
                if (network != null) network.setText(service.getStatus());
                refresh();
            }
            handler.postDelayed(this, 2000);
        }
    };
    /**
     * I use the wallet filename passed in the Activity Intent to reopen the same encrypted
     * selection after recreation. Success sets walletReady, updates public content, and starts
     * a connection when needed. Errors leave financial controls guarded by the unset readiness
     * flag.
     */
    protected void loadWallet() {
        String file = getIntent().getStringExtra("wallet_file");
        work(() -> service.openWallet(file), wallet -> {
            walletReady = true; onWalletReady(); refresh();
            if (!service.getNetwork().isNetworkStarted()) work(() -> { service.connect(); return true; }, ignored -> refresh());
        });
    }
    protected void onWalletReady() { }
    protected abstract void refresh();
    /**
     * I receive an Activity class from a button listener, require the opened wallet to be
     * ready, and start that screen with only the selected wallet filename. The destination
     * reloads through the shared service after process recreation.
     * @param screen Class<?> supplied by the calling workflow as described above
     */
    protected void navigate(Class<?> screen) {
        if (!walletReady) return;
        startActivity(new android.content.Intent(this, screen).putExtra("wallet_file", service.requireWallet().getFileName()));
    }
    /**
     * Android calls this when the screen returns to the foreground. I invoke parent lifecycle
     * handling and start the public-data refresh or wallet-list behavior shown below. Secured
     * screens enforce the application unlock state. The paired pause method stops periodic
     * callbacks so background screens do not keep refreshing.
     */
    @Override protected void onResume() { super.onResume(); handler.post(tick); }
    /**
     * Android calls this as the screen leaves the foreground. I stop this screen's refresh
     * callbacks or conceal sensitive displayed data as implemented below, then call the parent
     * lifecycle method. Wallet mutations remain serialized by the shared service rather than
     * being discarded with the Activity.
     */
    @Override protected void onPause() { handler.removeCallbacks(tick); super.onPause(); }
}
