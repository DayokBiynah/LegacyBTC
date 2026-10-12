package com.example.legacybtc;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.util.ArrayList;

/**
 * I list only encrypted wallets belonging to this build's network, decrypt public receiving
 * addresses on the worker, and show unreadable entries without destroying them. Opening passes
 * the selected filename to the dashboard. Local deletion requires a warning and device
 * authentication; deleting a file does not cancel a Bitcoin inheritance output.
 */
public class OpenWalletActivity extends WalletActivity {
    /**
     * Android calls this lifecycle method when creating or recreating the component. I
     * initialize the parent behavior, connect Java listeners to the XML controls or register
     * application callbacks, and restore only the workflow state explicitly handled below.
     * Button callbacks capture input on the UI thread and delegate expensive wallet work to
     * the service. Errors are displayed instead of silently continuing.
     * @param state Bundle supplied by the calling workflow as described above
     */
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_open_wallet);
        findViewById(R.id.backButton).setOnClickListener(view -> finish());
    }
    /**
     * Android calls this when the screen returns to the foreground. I invoke parent lifecycle
     * handling and start the public-data refresh or wallet-list behavior shown below. Secured
     * screens enforce the application unlock state. The paired pause method stops periodic
     * callbacks so background screens do not keep refreshing.
     */
    @Override protected void onResume() { super.onResume(); displaySavedWallets(); }
    /**
     * I loop over this network's encrypted files on the worker, loading each public address or
     * retaining an unreadable entry. On the main thread I inflate rows and attach Open and
     * authenticated Delete listeners. The empty-state view explains when no wallets exist.
     */
    private void displaySavedWallets() {
        work(() -> {
            ArrayList<String[]> entries = new ArrayList<>();
            for (String file : WalletStorageManager.getSavedWalletFiles(this)) {
                try { entries.add(new String[]{file, WalletStorageManager.loadWallet(this, file).currentReceiveAddress().toString()}); }
                catch (Exception error) { entries.add(new String[]{file, "Unreadable wallet — restore from recovery words"}); }
            }
            return entries;
        }, entries -> {
            LinearLayout container = findViewById(R.id.savedWalletsContainer);
            container.removeAllViews();
            ((TextView) findViewById(R.id.networkStatusText)).setText(R.string.testnet_only);
            if (entries.isEmpty()) {
                container.addView(WalletUi.empty(container, R.string.no_wallets));
            }
            for (String[] entry : entries) {
                android.view.View row = getLayoutInflater().inflate(R.layout.item_saved_wallet, container, false);
                ((TextView) row.findViewById(R.id.walletNameText)).setText(WalletUi.walletName(this, entry[0]));
                ((TextView) row.findViewById(R.id.savedAddressText)).setText(entry[1]);
                Button open = row.findViewById(R.id.openSavedWalletButton);
                open.setOnClickListener(view -> dashboard(entry[0]));
                Button delete = row.findViewById(R.id.deleteSavedWalletButton);
                delete.setOnClickListener(view -> new AlertDialog.Builder(this).setTitle(R.string.delete_wallet)
                        .setMessage(R.string.delete_warning).setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.delete, (dialog, which) -> authenticate(() ->
                                work(() -> service.deleteWallet(entry[0]), deleted -> {
                                    if (!deleted) showError("Wallet could not be deleted.");
                                    displaySavedWallets();
                                }))).show());
                container.addView(row);
            }
        });
    }
}
