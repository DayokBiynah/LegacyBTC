package com.example.legacybtc;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.EditText;

public class CreateWalletActivity extends WalletActivity {
    private String walletFile;
    private boolean generating;
    private WalletSetupModel model;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_create_wallet);
        walletFile = state == null ? null : state.getString("wallet_file");
        model = new androidx.lifecycle.ViewModelProvider(this).get(WalletSetupModel.class);
        Button generate = findViewById(R.id.generateWalletButton);
        generate.setOnClickListener(view -> {
            if (generating || walletFile != null) return;
            generating = true;
            model.operation = service.submit(service::createWallet);
            observeCreation();
        });
        findViewById(R.id.continueButton).setOnClickListener(view -> {
            if (walletFile == null) { showError("Create a wallet first."); return; }
            if (!((CheckBox) findViewById(R.id.backupConfirmed)).isChecked()) {
                showError("Write down your recovery words and confirm the backup before continuing."); return;
            }
            dashboard(walletFile);
        });
        findViewById(R.id.backButton).setOnClickListener(view -> finish());
        if (walletFile != null) showWallet();
        else if (model.operation != null) { generating = true; observeCreation(); }
    }
    private void observeCreation() {
        model.operation.whenComplete((file,error) -> runOnUiThread(() -> {
            if (isDestroyed() || isFinishing()) return;
            generating = false;
            if (error != null) { model.operation = null; showError("Wallet creation could not complete. Check Saved Wallets before retrying."); return; }
            walletFile = file; showWallet();
        }));
    }
    private void showWallet() {
        work(() -> WalletStorageManager.loadWallet(this, walletFile), wallet -> {
            TextView address = findViewById(R.id.walletAddressText);
            address.setText(wallet.currentReceiveAddress().toString()); address.setVisibility(View.VISIBLE);
            TextView phrase = findViewById(R.id.recoveryPhraseText);
            phrase.setText(numberedWords(wallet.getActiveKeyChain().getMnemonicCode()));
            phrase.setVisibility(View.VISIBLE);
            findViewById(R.id.createPath).setVisibility(View.GONE);
            findViewById(R.id.backupSection).setVisibility(View.VISIBLE);
            findViewById(R.id.walletReadySection).setVisibility(View.VISIBLE);
            findViewById(R.id.continueButton).setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.errorText)).setText("");
            findViewById(R.id.generateWalletButton).setEnabled(false);
            findViewById(R.id.backupConfirmed).setVisibility(View.VISIBLE);
        });
    }
    private String numberedWords(java.util.List<String> words) {
        StringBuilder display = new StringBuilder();
        for (int index = 0; index < words.size(); index++) {
            if (index > 0) display.append("\n");
            display.append(index + 1).append(".  ").append(words.get(index));
        }
        return display.toString();
    }
    @Override protected void onPause() {
        if (isFinishing()) ((TextView) findViewById(R.id.recoveryPhraseText)).setText("");
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("wallet_file", walletFile); // Never put recovery words in saved instance state.
    }
}
