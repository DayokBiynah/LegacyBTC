package com.example.legacybtc;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.EditText;

/**
 * I implement the combined Create/Restore screen using Java and XML. Buttons capture recovery
 * Strings or request a fresh wallet on the service worker. New wallets display numbered
 * recovery words and require a backup checkbox before Continue. Restored wallets clear inputs
 * instead of redisplaying the phrase. Saved instance state contains a filename and public
 * workflow flags, never mnemonic words.
 */
public class CreateWalletActivity extends WalletActivity {
    private String walletFile;
    private boolean generating;
    private boolean restored;
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
        setContentView(R.layout.activity_create_wallet);
        walletFile = state == null ? null : state.getString("wallet_file");
        restored = state != null && state.getBoolean("restored");
        Button generate = findViewById(R.id.generateWalletButton);
        generate.setOnClickListener(view -> {
            if (generating || walletFile != null) return;
            generating = true;
            work(() -> {
                try { return service.createWallet(); } finally { generating = false; }
            }, file -> { walletFile = file; showWallet(); });
        });
        findViewById(R.id.restoreWalletButton).setOnClickListener(view -> {
            if (generating || walletFile != null) return;
            String phrase = ((EditText) findViewById(R.id.recoveryPhraseInput)).getText().toString();
            String passphrase = ((EditText) findViewById(R.id.passphraseInput)).getText().toString();
            generating = true;
            work(() -> {
                try { return service.restoreWallet(phrase, passphrase); } finally { generating = false; }
            }, file -> {
                walletFile = file; restored = true;
                ((EditText) findViewById(R.id.recoveryPhraseInput)).setText("");
                ((EditText) findViewById(R.id.passphraseInput)).setText("");
                showWallet();
            });
        });
        findViewById(R.id.continueButton).setOnClickListener(view -> {
            if (walletFile == null) { showError("Create or restore a wallet first."); return; }
            if (!restored && !((CheckBox) findViewById(R.id.backupConfirmed)).isChecked()) {
                showError("Write down your recovery words and confirm the backup before continuing."); return;
            }
            dashboard(walletFile);
        });
        findViewById(R.id.backButton).setOnClickListener(view -> finish());
        if (walletFile != null) showWallet();
    }
    /**
     * I reload the encrypted wallet before displaying its public address. For a newly created
     * wallet I number the mnemonic words and show the backup confirmation; a restored wallet
     * does not redisclose them. I hide setup input sections and reveal Continue only after
     * persistence succeeds.
     */
    private void showWallet() {
        work(() -> WalletStorageManager.loadWallet(this, walletFile), wallet -> {
            TextView address = findViewById(R.id.walletAddressText);
            address.setText(wallet.currentReceiveAddress().toString()); address.setVisibility(View.VISIBLE);
            TextView phrase = findViewById(R.id.recoveryPhraseText);
            // Restoring a wallet does not need to expose its already supplied recovery words again.
            phrase.setText(restored ? "" : numberedWords(wallet.getActiveKeyChain().getMnemonicCode()));
            phrase.setVisibility(restored ? View.GONE : View.VISIBLE);
            findViewById(R.id.createPath).setVisibility(View.GONE);
            findViewById(R.id.restorePath).setVisibility(View.GONE);
            findViewById(R.id.backupSection).setVisibility(restored ? View.GONE : View.VISIBLE);
            findViewById(R.id.walletReadySection).setVisibility(View.VISIBLE);
            findViewById(R.id.continueButton).setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.errorText)).setText("");
            findViewById(R.id.generateWalletButton).setEnabled(false);
            findViewById(R.id.restoreWalletButton).setEnabled(false);
            findViewById(R.id.backupConfirmed).setVisibility(restored ? View.GONE : View.VISIBLE);
        });
    }
    /**
     * I loop through the ordered mnemonic List and append a one-based number and newline for
     * each word. The returned String preserves backup order on narrow screens and large fonts;
     * it is displayed only in the protected creation view and is never logged.
     * @param words java.util.List<String> supplied by the calling workflow as described above
     * @return the ordered numbered mnemonic display String for the protected setup view
     */
    private String numberedWords(java.util.List<String> words) {
        StringBuilder display = new StringBuilder();
        for (int index = 0; index < words.size(); index++) {
            if (index > 0) display.append("\n");
            display.append(index + 1).append(".  ").append(words.get(index));
        }
        return display.toString();
    }
    /**
     * Android calls this as the screen leaves the foreground. I stop this screen's refresh
     * callbacks or conceal sensitive displayed data as implemented below, then call the parent
     * lifecycle method. Wallet mutations remain serialized by the shared service rather than
     * being discarded with the Activity.
     */
    @Override protected void onPause() {
        if (isFinishing()) ((TextView) findViewById(R.id.recoveryPhraseText)).setText("");
        super.onPause();
    }
    /**
     * I receive the Bundle supplied by Android before recreation. I save only explicit
     * workflow identifiers and flags, such as a wallet filename, selected plan ID, or
     * submitted transaction ID. Mnemonic words and signing keys are not placed in this Bundle.
     * New Activity instances reconstruct their screen through the existing service.
     * @param state Bundle supplied by the calling workflow as described above
     */
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("wallet_file", walletFile); // Never put recovery words in saved instance state.
        state.putBoolean("restored", restored);
    }
}
