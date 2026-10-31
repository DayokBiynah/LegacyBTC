package com.example.legacybtc;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import org.bitcoinj.core.Transaction;

public class SendBitcoinActivity extends SelectedWalletActivity {
    private boolean sending;
    private boolean submitted;
    private String transactionId;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_send);
        transactionId = state == null ? null : state.getString("transaction_id");
        submitted = transactionId != null;
        if (submitted) {
            findViewById(R.id.sendButton).setEnabled(false);
            ((TextView) findViewById(R.id.confirmationText)).setText(getString(R.string.saved_pending_id, transactionId));
        }
        findViewById(R.id.cancelButton).setOnClickListener(view -> finish());
        findViewById(R.id.sendButton).setOnClickListener(view -> reviewPayment());
        loadWallet();
    }
    private String input(int id) { return ((EditText) findViewById(id)).getText().toString(); }
    private void reviewPayment() {
        if (!walletReady || sending || submitted) return;
        sending = true;
        PaymentInput validated;
        try { validated = PaymentInput.parse(input(R.id.recipientInput), input(R.id.amountInput)); }
        catch (Exception failure) { sending = false; showError(paymentError(failure)); return; }
        String recipient = validated.getAddress(), amount = validated.getAmount(), fee = input(R.id.feeInput);
        service.submit(() -> service.preparePayment(recipient, amount, fee)).whenComplete((request, error) -> runOnUiThread(() -> {
            if (isDestroyed() || isFinishing()) return;
            if (error != null) { sending = false; showError(paymentError(error)); return; }
            long feeSatoshis = request.tx.getFee().value;
            String message = "Bitcoin " + WalletPolicy.NETWORK_LABEL + "\n\nRecipient: " + recipient.trim() + "\nAmount: " + amount.trim()
                    + " BTC\nFee: " + org.bitcoinj.base.Coin.valueOf(feeSatoshis).toPlainString()
                    + " BTC\nTotal: " + WalletPolicy.parseAmount(amount).add(request.tx.getFee()).toPlainString() + " BTC";
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.confirm_transaction).setMessage(message)
                    .setNegativeButton(R.string.cancel, (d, which) -> sending = false)
                    .setPositiveButton(R.string.confirm_send, (d, which) -> authenticate(() -> {
                        service.submit(() -> service.sendBitcoin(request)).whenComplete((tx, failure) -> runOnUiThread(() -> {
                            if (isDestroyed() || isFinishing()) return;
                            if (failure != null) { sending = false; showError(paymentError(failure)); }
                            else { submitted = true; transactionId = tx.getTxId().toString(); findViewById(R.id.sendButton).setEnabled(false); broadcastSaved(tx); }
                        }));
                    })).create();
            dialog.setOnCancelListener(d -> sending = false);
            dialog.show();
        }));
    }
    private void broadcastSaved(Transaction tx) {
        ((TextView) findViewById(R.id.confirmationText)).setText(getString(R.string.signed_broadcasting, tx.getTxId().toString()));
        service.submit(() -> service.broadcast(tx)).thenCompose(future -> future)
                .whenComplete((sent, error) -> runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    // Do not offer a second payment after an uncertain relay result.
                    findViewById(R.id.sendButton).setEnabled(false);
                    ((TextView) findViewById(R.id.confirmationText)).setText(getString(error == null
                            ? R.string.relayed_id : R.string.relay_uncertain_id, tx.getTxId().toString()));
                }));
    }
    private String paymentError(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        if (error instanceof org.bitcoinj.core.InsufficientMoneyException) return "Insufficient available balance to cover amount plus fee.";
        return error.getMessage() == null ? "Payment failed. Review your inputs." : error.getMessage();
    }
    @Override protected void refresh() {
        ((TextView) findViewById(R.id.balanceText)).setText(getString(R.string.balance_value, service.getBalance().toPlainString()));
        String blocked = service.getSendBlockReason();
        boolean verified = blocked.isEmpty();
        findViewById(R.id.sendButton).setEnabled(verified && !sending && !submitted);
        if (!sending && !submitted) ((TextView) findViewById(R.id.confirmationText)).setText(verified ? "" : blocked);
    }
    @Override protected void onAuthenticationCancelled() { sending = false; }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putString("transaction_id", transactionId);
    }
}


