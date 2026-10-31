package com.example.legacybtc;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Restores this application's deterministic SegWit account using the ordered recovery phrase
 * and optional original BIP-39 passphrase. The worker validates and encrypts the wallet, while
 * callbacks clear sensitive fields and enable Continue. Duplicate detection reuses a saved
 * account instead of overwriting its history. Electrum discovery begins after opening the
 * dashboard.
 */
public class RestoreWalletActivity extends WalletActivity {
    private String walletFile;
    private boolean restoring;
    private WalletSetupModel model;
    /**
     * Android calls this lifecycle method when creating or recreating the component. I
     * initialize the parent behavior, connect Java listeners to the XML controls or register
     * application callbacks, and restore only the workflow state explicitly handled below.
     * Button callbacks capture input on the UI thread and delegate expensive wallet work to
     * the service. Errors are displayed instead of silently continuing.
     * @param state Bundle supplied by the calling workflow as described above
     */
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_restore_wallet);
        walletFile = state == null ? null : state.getString("wallet_file");
        model = new androidx.lifecycle.ViewModelProvider(this).get(WalletSetupModel.class);
        findViewById(R.id.restoreWalletButton).setOnClickListener(view -> {
            if (restoring) return;
            String phrase = ((EditText) findViewById(R.id.recoveryPhraseInput)).getText().toString();
            String passphrase = ((EditText) findViewById(R.id.passphraseInput)).getText().toString();
            restoring = true;
            model.operation = service.submit(() -> service.restoreWallet(phrase,passphrase));
            observeRestoration();
        });
        findViewById(R.id.continueButton).setOnClickListener(view -> {
            if (walletFile == null) showError("Restore a wallet before continuing."); else dashboard(walletFile);
        });
        findViewById(R.id.backButton).setOnClickListener(view -> finish());
        if (walletFile != null) showRestoredState();
        else if (model.operation != null) { restoring = true; observeRestoration(); }
    }
    private void observeRestoration() {
        model.operation.whenComplete((file,error) -> runOnUiThread(() -> {
            if (isDestroyed() || isFinishing()) return;
            restoring = false;
            if (error != null) {
                model.operation = null;
                Throwable cause = error;
                while (cause.getCause() != null) cause = cause.getCause();
                showError(cause.getMessage() == null ? "Restoration failed." : cause.getMessage()); return;
            }
            walletFile = file;
            ((EditText)findViewById(R.id.recoveryPhraseInput)).setText("");
            ((EditText)findViewById(R.id.passphraseInput)).setText("");
            showRestoredState();
        }));
    }
    /**
     * hides recovery input controls and reveal Continue after the wallet has been encrypted
     * successfully. The public confirmation text replaces the sensitive phrase; failures do
     * not enter this state.
     */
    private void showRestoredState() {
        findViewById(R.id.restorePath).setVisibility(android.view.View.GONE);
        findViewById(R.id.continueButton).setVisibility(android.view.View.VISIBLE);
        ((TextView) findViewById(R.id.restoredAddressText)).setText(R.string.restore_saved);
        ((TextView) findViewById(R.id.errorText)).setText("");
    }
    /**
     * Receives the Bundle supplied by Android before recreation. Saves only explicit
     * workflow identifiers and flags, such as a wallet filename, selected plan ID, or
     * submitted transaction ID. Mnemonic words and signing keys are not placed in this Bundle.
     * New Activity instances reconstruct their screen through the existing service.
     * @param state Bundle supplied by the calling workflow as described above
     */
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putString("wallet_file", walletFile);
    }
}
