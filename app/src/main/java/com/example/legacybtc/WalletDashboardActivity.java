package com.example.legacybtc;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

/**
 * I display the opened wallet's spendable, confirmed, and unconfirmed balances, public
 * receiving address, recent transactions, inheritance summary, and peer status. Navigation
 * passes only the wallet filename. A resumed Handler refreshes public data; signing,
 * synchronization startup, and encrypted storage run through the service worker. Status text
 * does not claim confirmation merely because a transaction was relayed.
 */
public class WalletDashboardActivity extends WalletActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean opening;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!opening) refreshWallet();
            handler.postDelayed(this, 2000);
        }
    };
    /**
     * Android calls this lifecycle method when creating or recreating the component. I
     * initialize the parent behavior, connect Java listeners to the XML controls or register
     * application callbacks, and restore only the workflow state explicitly handled below.
     * Button callbacks capture input on the UI thread and delegate expensive wallet work to
     * the service. Errors are displayed instead of silently continuing.
     * @param state Bundle supplied by the calling workflow as described above
     */
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_dashboard);
        String file = getIntent().getStringExtra("wallet_file");
        if (file == null) { showError("No wallet selected."); finish(); return; }
        opening = true;
        work(() -> service.openWallet(file), wallet -> {
            opening = false; refreshWallet();
            work(() -> { service.connect(); return true; }, ignored -> refreshWallet());
        });
        findViewById(R.id.connectButton).setOnClickListener(view -> work(() -> { service.connect(); return true; }, ignored -> refreshWallet()));
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
    /**
     * I connect a dashboard button resource ID to its destination Activity class. The listener
     * waits for asynchronous wallet opening and passes the selected filename through an
     * Intent. The destination still performs its own selected-wallet loading and security
     * checks.
     * @param id int supplied by the calling workflow as described above
     * @param screen Class<?> supplied by the calling workflow as described above
     */
    private void link(int id, Class<?> screen) {
        findViewById(id).setOnClickListener(view -> {
            if (!opening) startActivity(new android.content.Intent(this, screen)
                    .putExtra("wallet_file", service.requireWallet().getFileName()));
        });
    }
    /**
     * Android calls this when the screen returns to the foreground. I invoke parent lifecycle
     * handling and start the public-data refresh or wallet-list behavior shown below. Secured
     * screens enforce the application unlock state. The paired pause method stops periodic
     * callbacks so background screens do not keep refreshing.
     */
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    /**
     * Android calls this as the screen leaves the foreground. I stop this screen's refresh
     * callbacks or conceal sensitive displayed data as implemented below, then call the parent
     * lifecycle method. Wallet mutations remain serialized by the shared service rather than
     * being discarded with the Activity.
     */
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    /**
     * I retrieve current balances, address, peer status, recent wallet transactions, and
     * inheritance summaries for the dashboard. The loop limits recent records to three, while
     * plan lookups run through the worker. Missing wallet state is tolerated during
     * asynchronous opening, so the interface waits rather than accessing an unavailable
     * selection.
     */
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
            work(() -> {
                InheritanceService inheritance = new InheritanceService(this);
                StringBuilder summary = new StringBuilder();
                for (InheritancePlan plan : inheritance.getPlans()) {
                    summary.append(plan.getBeneficiary().getName()).append(" • ").append(plan.getAmount().toPlainString())
                            .append(" BTC • ").append(WalletUi.status(inheritance.checkInheritanceStatus(plan))).append("\n");
                }
                return summary.toString();
            }, summary -> {
                ((TextView) findViewById(R.id.inheritanceStatusText)).setText(summary.isEmpty() ? getString(R.string.no_plan)
                        : getString(R.string.inheritance_summary, summary));
            });
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
