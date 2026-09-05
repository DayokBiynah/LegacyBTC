package com.example.legacybtc;

// Imports Bundle so the Activity can receive saved state information
// when Android creates or recreates this screen.
import android.os.Bundle;

// Imports Button so the Activity can control the buttons
// defined in activity_restore_wallet.xml.
import android.widget.Button;

// Imports EditText so the Activity can receive the
// user's 12-word Bitcoin recovery phrase.
import android.widget.EditText;

// Imports TextView so the Activity can display the
// receiving address of the restored Bitcoin wallet.
import android.widget.TextView;

// Imports Toast so the Activity can display confirmation
// and error messages to the user.
import android.widget.Toast;

// Provides the Android Activity functionality used by this screen.
import androidx.appcompat.app.AppCompatActivity;

// Imports BitcoinNetwork so the restored wallet uses
// the Bitcoin Testnet network during development.
import org.bitcoinj.base.BitcoinNetwork;

// Imports ScriptType so the restored wallet uses the
// same native SegWit address type as created wallets.
import org.bitcoinj.base.ScriptType;

// Imports DeterministicSeed so the 12-word mnemonic phrase
// can be converted back into the deterministic Bitcoin seed.
import org.bitcoinj.wallet.DeterministicSeed;

// Imports Wallet so the deterministic seed can be used
// to reconstruct the Bitcoin wallet.
import org.bitcoinj.wallet.Wallet;


/**
 * RestoreWalletActivity controls the screen used to restore
 * a LegacyBTC wallet from a 12-word recovery phrase.
 *
 * The Activity converts the recovery phrase into a deterministic
 * Bitcoin seed and reconstructs the wallet using bitcoinj.
 */
public class RestoreWalletActivity extends AppCompatActivity {

    // Stores a reference to the text field where the user
    // enters the 12-word recovery phrase.
    private EditText recoveryPhraseInput;

    // Stores a reference to the Restore Wallet button.
    private Button restoreWalletButton;

    // Stores a reference to the Back button.
    private Button backButton;

    // Stores a reference to the TextView used to display
    // the receiving address of the restored wallet.
    private TextView restoredAddressText;

    // Stores the Bitcoin wallet reconstructed from
    // the recovery phrase.
    private Wallet restoredWallet;


    /**
     * Executes when RestoreWalletActivity is created.
     *
     * This method loads the XML layout, connects its controls
     * to Java variables, and defines the restore and back actions.
     *
     * @param savedInstanceState contains saved Activity state
     *                           if Android recreates the screen
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Initializes the Activity through the Android lifecycle.
        super.onCreate(savedInstanceState);

        // Loads activity_restore_wallet.xml as the interface
        // controlled by this Activity.
        setContentView(R.layout.activity_restore_wallet);


        // Connects the recovery phrase input field from
        // the XML layout to its Java variable.
        recoveryPhraseInput =
                findViewById(R.id.recoveryPhraseInput);

        // Connects the Restore Wallet button from the
        // XML layout to its Java variable.
        restoreWalletButton =
                findViewById(R.id.restoreWalletButton);

        // Connects the Back button from the XML layout
        // to its Java variable.
        backButton =
                findViewById(R.id.backButton);

        // Connects the restored wallet address TextView
        // from the XML layout to its Java variable.
        restoredAddressText =
                findViewById(R.id.restoredAddressText);


        // Executes when the user selects Restore Wallet.
        restoreWalletButton.setOnClickListener(view -> {

            // Reads the recovery phrase entered by the user.
            String recoveryPhrase =
                    recoveryPhraseInput.getText()
                            .toString()
                            .trim()
                            .toLowerCase();

            // Splits the phrase by one or more spaces so the
            // number of mnemonic words can be validated.
            String[] recoveryWords =
                    recoveryPhrase.split("\\s+");

            // Requires exactly 12 words for LegacyBTC's
            // current wallet recovery process.
            if (recoveryWords.length != 12) {

                Toast.makeText(
                        RestoreWalletActivity.this,
                        "Enter a valid 12-word recovery phrase.",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }


            try {

                /*
                 * Reconstructs a deterministic seed from the
                 * recovery phrase entered by the user.
                 *
                 * The empty string represents the absence of an
                 * additional BIP-39 passphrase.
                 */
                DeterministicSeed deterministicSeed =
                        DeterministicSeed.ofMnemonic(
                                recoveryPhrase,
                                ""
                        );

                // Verifies that the recovery phrase is a valid BIP-39 mnemonic.
                // This checks the mnemonic words and checksum before a wallet
                // is reconstructed from the seed.
                deterministicSeed.check();


                /*
                 * Reconstructs the Bitcoin wallet from the seed.
                 *
                 * TESTNET matches the network used when the wallet
                 * was created.
                 *
                 * P2WPKH matches the native SegWit address type
                 * used by CreateWalletActivity.
                 */
                restoredWallet =
                        Wallet.fromSeed(
                                BitcoinNetwork.TESTNET,
                                deterministicSeed,
                                ScriptType.P2WPKH
                        );

                // Securely encrypts and saves the restored Bitcoin wallet
                // as a separate wallet inside LegacyBTC's private storage.
                WalletStorageManager.saveWallet(
                        RestoreWalletActivity.this,
                        restoredWallet
                );


                // Retrieves the current receiving address from
                // the reconstructed Bitcoin wallet.
                String restoredAddress =
                        restoredWallet.currentReceiveAddress().toString();

                // Displays the restored receiving address
                // directly on the screen.
                restoredAddressText.setText(restoredAddress);

                // Makes the restored address visible.
                restoredAddressText.setVisibility(TextView.VISIBLE);

                // Confirms that the recovery phrase successfully
                // reconstructed a Bitcoin wallet.
                Toast.makeText(
                        RestoreWalletActivity.this,
                        "Wallet restored successfully.",
                        Toast.LENGTH_LONG
                ).show();

            } catch (Exception exception) {

                // Displays an error if the recovery phrase is invalid
                // or the wallet cannot be reconstructed.
                Toast.makeText(
                        RestoreWalletActivity.this,
                        "Wallet could not be restored.",
                        Toast.LENGTH_LONG
                ).show();
            }
        });


        // Executes when the user selects Back.
        backButton.setOnClickListener(view -> {

            // Closes RestoreWalletActivity and returns
            // the user to MainActivity.
            finish();
        });
    }
}