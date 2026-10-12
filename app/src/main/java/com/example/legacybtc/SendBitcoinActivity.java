package com.example.legacybtc;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import org.bitcoinj.core.Transaction;

/**
 * I collect the recipient address, exact decimal BTC amount, and manual fee rate. The service
 * prepares an unsigned transaction and I display its destination, amount, fee, total, and
 * network for review. Confirm invokes Android credential authentication before signing and
 * saving. A submitted transaction stays disabled after an uncertain relay result so retry uses
 * its existing ID rather than paying twice.
 */
public class SendBitcoinActivity extends SelectedWalletActivity {
    private boolean sending;
    private boolean submitted;
    private String transactionId;
    /**
     * Android calls this lifecycle method when creating or recreating the component. I
     * initialize the parent behavior, connect Java listeners to the XML controls or register
     * application callbacks, and restore only the workflow state explicitly handled below.
     * Button callbacks capture input on the UI thread and delegate expensive wallet work to
     * the service. Errors are displayed instead of silently continuing.
     * @param state Bundle supplied by the calling workflow as described above
     */
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
    /**
     * I read the EditText identified by the supplied Android resource ID and return its
     * current String. Callers capture this value before submitting worker operations, then run
     * domain validation. Reading text alone does not establish a valid amount, address, or
     * period.
     * @param id int supplied by the calling workflow as described above
     * @return the current EditText String captured for subsequent validation
     */
    private String input(int id) { return ((EditText) findViewById(id)).getText().toString(); }
    /**
     * I capture recipient, BTC amount, and fee text after checking that this screen has not
     * already submitted. The worker prepares an unsigned request. I display its exact output
     * and fee in an AlertDialog; Cancel releases the review guard, while Confirm requests
     * Android authentication before signing and encrypted saving. Relay uses the saved
     * transaction ID.
     */
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
                            if (failure != null) { sending = false; showError(paymentError(failure)); }
                            else { submitted = true; transactionId = tx.getTxId().toString(); findViewById(R.id.sendButton).setEnabled(false); broadcastSaved(tx); }
                        }));
                    })).create();
            dialog.setOnCancelListener(d -> sending = false);
            dialog.show();
        }));
    }
    /**
     * I show the saved transaction ID and request relay through the service. A successful
     * result reports relay while still awaiting mining. A failure preserves the pending ID and
     * disables another payment, directing the user to authenticated History retry.
     * @param tx Transaction supplied by the calling workflow as described above
     */
    private void broadcastSaved(Transaction tx) {
        ((TextView) findViewById(R.id.confirmationText)).setText(getString(R.string.signed_broadcasting, tx.getTxId().toString()));
        service.submit(() -> service.broadcast(tx)).thenCompose(future -> future)
                .whenComplete((sent, error) -> runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    // Do not offer a second payment after an uncertain relay result.
                    findViewById(R.id.sendButton).setEnabled(false);
                    ((TextView) findViewById(R.id.confirmationText)).setText(getString(error == null
                            ? R.string.relayed_id : R.string.relay_uncertain_id, tx.getTxId().toString()));
                }));
    }
    /**
     * I unwrap asynchronous exception causes and translate InsufficientMoneyException into an
     * amount-plus-fee message. Other errors use their available message or a generic retry
     * instruction. This returned String is displayed on the Send screen, never a fabricated
     * success.
     * @param error Throwable supplied by the calling workflow as described above
     * @return a readable payment-failure message for the interface
     */
    private String paymentError(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        if (error instanceof org.bitcoinj.core.InsufficientMoneyException) return "Insufficient available balance to cover amount plus fee.";
        return error.getMessage() == null ? "Payment failed. Review your inputs." : error.getMessage();
    }
    /**
     * I read the selected wallet's public data and update this screen's displayed address,
     * balance, plan status, or history. The conditions below avoid redundant or overlapping
     * work. Expensive repository/script operations use the worker, and their results return to
     * the main thread. Refresh does not authorize a payment or claim.
     */
    @Override protected void refresh() {
        ((TextView) findViewById(R.id.balanceText)).setText(getString(R.string.balance_value, service.getBalance().toPlainString()));
    }
    @Override protected void onAuthenticationCancelled() { sending = false; }
    /**
     * I receive the Bundle supplied by Android before recreation. I save only explicit
     * workflow identifiers and flags, such as a wallet filename, selected plan ID, or
     * submitted transaction ID. Mnemonic words and signing keys are not placed in this Bundle.
     * New Activity instances reconstruct their screen through the existing service.
     * @param state Bundle supplied by the calling workflow as described above
     */
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putString("transaction_id", transactionId);
    }
}
