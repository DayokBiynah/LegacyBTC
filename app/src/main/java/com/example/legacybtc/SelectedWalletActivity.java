package com.example.legacybtc;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

public abstract class SelectedWalletActivity extends WalletActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    protected boolean walletReady;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (walletReady) {
                TextView network = findViewById(R.id.networkStatusText);
                if (network != null) network.setText(service.getStatus());
                TextView estimate = findViewById(R.id.feeEstimateText);
                if (estimate != null) {
                    long rate = service.getNetwork().getEstimatedFeeRate();
                    estimate.setText(rate == 0 ? "Live fee estimate unavailable. Enter a manual rate."
                            : "Six-block estimate: " + rate + " sat/vB. Enter your chosen rate below.");
                }
                refresh();
            }
            handler.postDelayed(this, 2000);
        }
    };
    protected void loadWallet() {
        String file = getIntent().getStringExtra("wallet_file");
        work(() -> service.openWallet(file), wallet -> {
            walletReady = true; onWalletReady(); refresh();
            if (!service.getNetwork().isNetworkStarted()) work(() -> { service.connect(); return true; }, ignored -> refresh());
        });
    }
    protected void onWalletReady() { }
    protected abstract void refresh();
    protected void navigate(Class<?> screen) {
        if (!walletReady) return;
        startActivity(new android.content.Intent(this, screen).putExtra("wallet_file", service.requireWallet().getFileName()));
    }
    @Override protected void onResume() { super.onResume(); handler.post(tick); }
    @Override protected void onPause() { handler.removeCallbacks(tick); super.onPause(); }
}
