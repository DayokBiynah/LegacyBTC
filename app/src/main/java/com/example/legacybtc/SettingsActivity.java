package com.example.legacybtc;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;

/**
 * Configures a TLS Electrum endpoint without converting legacy P2P preferences.
 * Recovery-word disclosure requires device credentials and a protected dialog.
 */
public class SettingsActivity extends SelectedWalletActivity {
    /**
     * Android calls this lifecycle method when creating or recreating the component. I
     * initialize the parent behavior, connect Java listeners to the XML controls or register
     * application callbacks, and restore only the workflow state explicitly handled below.
     * Button callbacks capture input on the UI thread and delegate expensive wallet work to
     * the service. Errors are displayed instead of silently continuing.
     * @param state Bundle supplied by the calling workflow as described above
     */
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_settings);
        findViewById(R.id.backButton).setOnClickListener(view -> finish());
        android.content.SharedPreferences preferences = getSharedPreferences(WalletPolicy.NETWORK_ID + "_electrum", MODE_PRIVATE);
        ((EditText) findViewById(R.id.peerHostInput)).setText(preferences.getString("host", ""));
        ((EditText) findViewById(R.id.peerPortInput)).setText(String.format(java.util.Locale.ROOT, "%d", preferences.getInt("port", 50002)));
        findViewById(R.id.saveNetworkButton).setOnClickListener(view -> {
            try {
                NetworkSettings settings = new NetworkSettings(((EditText) findViewById(R.id.peerHostInput)).getText().toString(),
                        ((EditText) findViewById(R.id.peerPortInput)).getText().toString());
                work(() -> {
                    if (!preferences.edit().putString("host", settings.getHost()).putInt("port", settings.getPort()).commit())
                        throw new IllegalStateException("Could not save peer settings.");
                    service.getNetwork().disconnectServers(); service.reconnect(); return true;
                }, ignored -> showMessage(WalletPolicy.NETWORK_LABEL + " TLS Electrum settings saved. Discovery is restarting."));
            } catch (Exception e) { showError(e.getMessage()); }
        });
        findViewById(R.id.revealRecoveryButton).setOnClickListener(view -> {
            if (walletReady) authenticate(() -> {
                String phrase = String.join(" ", service.requireWallet().getWallet().getActiveKeyChain().getMnemonicCode());
                new AlertDialog.Builder(this).setTitle(R.string.recovery_words).setMessage(phrase)
                        .setPositiveButton(R.string.done, null).show().getWindow()
                        .addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
            });
        });
        loadWallet();
    }
    /**
     * Reads the selected wallet's public data and update this screen's displayed address,
     * balance, plan status, or history. The conditions below avoid redundant or overlapping
     * work. Expensive repository/script operations use the worker, and their results return to
     * the main thread. Refresh does not authorize a payment or claim.
     */
    @Override protected void refresh() { ((TextView) findViewById(R.id.networkStatusText)).setText(service.getStatus()); }
}
