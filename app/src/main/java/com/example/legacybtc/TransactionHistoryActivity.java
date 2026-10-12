package com.example.legacybtc;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import java.text.DateFormat;
import java.util.ArrayList;

/**
 * I render real wallet transactions in a scrollable XML container with type, amount, ID, date,
 * and confidence status. A rendering signature avoids rebuilding unchanged rows. Details offer
 * authenticated rebroadcast only for a locally created pending transaction, preserving its
 * original ID and signed contents instead of constructing a second payment.
 */
public class TransactionHistoryActivity extends SelectedWalletActivity {
    private String lastRendered = "";
    /**
     * Android calls this lifecycle method when creating or recreating the component. I
     * initialize the parent behavior, connect Java listeners to the XML controls or register
     * application callbacks, and restore only the workflow state explicitly handled below.
     * Button callbacks capture input on the UI thread and delegate expensive wallet work to
     * the service. Errors are displayed instead of silently continuing.
     * @param state Bundle supplied by the calling workflow as described above
     */
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_history);
        findViewById(R.id.backButton).setOnClickListener(view -> finish()); loadWallet();
    }
    /**
     * I read the selected wallet's public data and update this screen's displayed address,
     * balance, plan status, or history. The conditions below avoid redundant or overlapping
     * work. Expensive repository/script operations use the worker, and their results return to
     * the main thread. Refresh does not authorize a payment or claim.
     */
    @Override protected void refresh() {
        ArrayList<BitcoinTransaction> records = service.getTransactions();
        StringBuilder signature = new StringBuilder();
        for (BitcoinTransaction record : records) signature.append(record.getId()).append(record.getStatus());
        if (lastRendered.equals(signature.toString()) && !records.isEmpty()) return;
        lastRendered = signature.toString();
        LinearLayout list = findViewById(R.id.transactionsContainer);
        list.removeAllViews();
        if (records.isEmpty()) list.addView(WalletUi.empty(list, R.string.history_empty));
        for (BitcoinTransaction record : records) {
            list.addView(WalletUi.transaction(list, record, () -> showTransaction(record.getId())));
        }
    }
    /**
     * I find the real wallet transaction by its ID and show public details. Only a pending
     * self-created transaction offers Retry Broadcast. That explicit action requires Android
     * authentication and relays the existing signed transaction, so uncertain relay does not
     * create a second payment.
     * @param id String supplied by the calling workflow as described above
     */
    private void showTransaction(String id) {
        BitcoinTransaction record = service.findTransaction(id);
        if (record == null) return;
        Transaction tx = service.requireWallet().getWallet().getTransaction(Sha256Hash.wrap(id));
        AlertDialog.Builder details = new AlertDialog.Builder(this).setTitle(record.getType())
                .setMessage(record.getId() + "\n" + record.getStatus()).setNegativeButton(R.string.back, null);
        if (tx.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING
                && tx.getConfidence().getSource() == TransactionConfidence.Source.SELF) {
            details.setPositiveButton(R.string.retry_broadcast, (dialog, which) -> authenticate(() -> {
                service.submit(() -> { service.requireReady(); return service.broadcast(tx); }).thenCompose(future -> future)
                        .whenComplete((result, error) -> runOnUiThread(() ->
                                showMessage(error == null ? "Transaction relayed. Awaiting confirmation." : "Relay not confirmed. The same transaction remains saved.")));
            }));
        }
        details.show();
    }
}
