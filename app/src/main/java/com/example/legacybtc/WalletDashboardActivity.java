package com.example.legacybtc;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

public class WalletDashboardActivity extends WalletActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean opening, refreshingPlans;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!opening) refreshWallet();
            handler.postDelayed(this, 2000);
        }
    };
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_dashboard);
        String file = getIntent().getStringExtra("wallet_file");
        if (file == null) { showError("No wallet selected."); finish(); return; }
        opening = true;
        work(() -> service.openWallet(file), wallet -> {
            opening = false; refreshWallet();
            work(() -> { service.connect(); return true; }, ignored -> refreshWallet());
        });
        findViewById(R.id.connectButton).setOnClickListener(view -> work(() -> { service.reconnect(); return true; }, ignored -> refreshWallet()));
        link(R.id.sendButton, SendBitcoinActivity.class);
        link(R.id.receiveButton, ReceiveBitcoinActivity.class);
        link(R.id.historyButton, TransactionHistoryActivity.class);
        link(R.id.settingsButton, SettingsActivity.class);
        link(R.id.inheritanceButton, InheritanceSetupActivity.class);
        findViewById(R.id.copyAddressButton).setOnClickListener(view -> {
            if (opening) return;
            ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(
                    android.content.ClipData.newPlainText("Bitcoin " + WalletPolicy.NETWORK_LABEL + " address", service.requireWallet().getAddress()));
            showMessage(getString(R.string.address_copied));
        });
        findViewById(R.id.backButton).setOnClickListener(view -> work(() -> { service.closeWallet(); return true; }, ignored -> finish()));
    }
    private void link(int id, Class<?> screen) {
        findViewById(id).setOnClickListener(view -> {
            if (!opening) startActivity(new android.content.Intent(this, screen)
                    .putExtra("wallet_file", service.requireWallet().getFileName()));
        });
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onOperationFailed() { refreshingPlans = false; }
    private void refreshWallet() {
        try {
            BitcoinWallet wallet = service.requireWallet();
            ((TextView) findViewById(R.id.walletIdentityText)).setText(WalletUi.walletName(this, wallet.getFileName()));
            ((TextView) findViewById(R.id.balanceText)).setText(getString(R.string.balance_value, wallet.getBalance().toPlainString()));
            ((TextView) findViewById(R.id.confirmedBalanceText)).setText(getString(R.string.confirmed_balance_value,
                    WalletPolicy.getOutputBalance(wallet.getWallet(), true).toPlainString()));
            ((TextView) findViewById(R.id.pendingBalanceText)).setText(getString(R.string.unconfirmed_balance_value,
                    WalletPolicy.getOutputBalance(wallet.getWallet(), false).toPlainString()));
            ((TextView) findViewById(R.id.walletAddressText)).setText(wallet.getAddress());
            ((TextView) findViewById(R.id.networkStatusText)).setText(service.getStatus());
            if (!refreshingPlans) {
            refreshingPlans = true;
            work(() -> {
                InheritanceService inheritance = new InheritanceService(this);
                StringBuilder summary = new StringBuilder(LegacyScheduledPayments.summary(this,service.requireWallet().getFileName()));
                for (InheritancePlan plan : inheritance.getPlans()) {
                    summary.append(plan.getBeneficiary().getName()).append(" • ").append(plan.getAmount().toPlainString())
                            .append(" BTC • ").append(WalletUi.status(inheritance.checkInheritanceStatus(plan))).append("\n");
                }
                return summary.toString();
            }, summary -> {
                refreshingPlans = false;
                ((TextView) findViewById(R.id.inheritanceStatusText)).setText(summary.isEmpty() ? getString(R.string.no_plan)
                        : getString(R.string.inheritance_summary, summary));
            });
            }
            StringBuilder recent = new StringBuilder();
            int count = 0;
            for (BitcoinTransaction tx : wallet.getTransactions()) {
                if (count++ == 3) break;
                recent.append(tx.getType()).append(" • ").append(tx.getAmount().toPlainString()).append(" BTC\n").append(tx.getStatus()).append("\n\n");
            }
            ((TextView) findViewById(R.id.recentTransactionsText)).setText(recent.length() == 0 ? getString(R.string.no_transactions) : recent.toString());
        } catch (IllegalStateException ignored) { /* Wait for the encrypted wallet to load. */ }
    }
}
