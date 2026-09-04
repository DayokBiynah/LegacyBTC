package com.example.legacybtc;

// Imports Bundle so the Activity can receive saved state information
// when Android creates or recreates this screen.
import android.os.Bundle;

// Imports Button so this Activity can control the buttons
// defined in activity_create_wallet.xml.
import android.widget.Button;

// Imports TextView so the Activity can display the generated
// Bitcoin address and wallet recovery phrase.
import android.widget.TextView;

// Imports Toast so the Activity can display short status
// and error messages to the user.
import android.widget.Toast;

// Provides the Android Activity functionality used by this screen.
import androidx.appcompat.app.AppCompatActivity;

// Imports BitcoinNetwork so the wallet can be configured
// to operate on a specific Bitcoin network.
import org.bitcoinj.base.BitcoinNetwork;

// Imports ScriptType so the wallet can specify the type
// of Bitcoin receiving addresses it generates.
import org.bitcoinj.base.ScriptType;

// Imports the bitcoinj Wallet class, which represents
// a Bitcoin wallet containing keys, addresses, and transactions.
import org.bitcoinj.wallet.Wallet;


/**
 * CreateWalletActivity controls the screen used to create
 * and load a LegacyBTC Bitcoin wallet.
 *
 * This Activity allows the user to generate a Bitcoin Testnet wallet,
 * view its receiving address and recovery phrase, securely save the
 * wallet, and load a previously saved wallet.
 */
public class CreateWalletActivity extends AppCompatActivity {

    // Stores a reference to the Generate Wallet button so the
    // Activity can respond when the user selects it.
    private Button generateWalletButton;

    // Stores a reference to the Load Saved Wallet button so the
    // Activity can respond when the user selects it.
    private Button loadWalletButton;

    // Stores a reference to the Back button so the Activity can
    // return the user to the previous screen.
    private Button backButton;

    // Stores a reference to the TextView used to display
    // the generated wallet recovery phrase.
    private TextView recoveryPhraseText;

    // Stores a reference to the TextView used to display
    // the Bitcoin receiving address associated with the wallet.
    private TextView walletAddressText;

    // Stores the Bitcoin wallet created or loaded by bitcoinj.
    // The Wallet object manages the deterministic key hierarchy,
    // receiving addresses, balances, and Bitcoin transactions.
    private Wallet bitcoinWallet;


    /**
     * Executes when CreateWalletActivity is created.
     *
     * This method loads the XML layout, connects the XML controls
     * to Java variables, and defines the actions performed when
     * the user selects the available buttons.
     *
     * @param savedInstanceState contains saved Activity state
     *                           if Android is recreating the screen
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Initializes the Activity through the Android Activity lifecycle.
        super.onCreate(savedInstanceState);

        // Loads activity_create_wallet.xml as the visible interface
        // controlled by CreateWalletActivity.
        setContentView(R.layout.activity_create_wallet);


        // Finds the Generate Wallet button in the XML layout and
        // connects it to the Java variable.
        generateWalletButton =
                findViewById(R.id.generateWalletButton);

        // Finds the Load Saved Wallet button in the XML layout and
        // connects it to the Java variable.
        loadWalletButton =
                findViewById(R.id.loadWalletButton);

        // Finds the Back button in the XML layout and connects
        // it to the Java variable.
        backButton =
                findViewById(R.id.backButton);

        // Connects the recovery phrase TextView from the XML layout
        // to its Java variable.
        recoveryPhraseText =
                findViewById(R.id.recoveryPhraseText);

        // Connects the wallet address TextView from the XML layout
        // to its Java variable.
        walletAddressText =
                findViewById(R.id.walletAddressText);


        // Executes when the user selects Generate Wallet.
        generateWalletButton.setOnClickListener(view -> {

            /*
             * Creates a new deterministic Bitcoin wallet.
             *
             * BitcoinNetwork.TESTNET configures the wallet for Bitcoin Testnet,
             * which uses test bitcoins instead of real BTC.
             *
             * ScriptType.P2WPKH creates native SegWit receiving addresses.
             * These addresses normally begin with "tb1" on Bitcoin Testnet.
             *
             * createDeterministic() generates the wallet from a randomly
             * generated deterministic seed. That seed can recreate the
             * wallet and its associated Bitcoin keys.
             */
            bitcoinWallet = Wallet.createDeterministic(
                    BitcoinNetwork.TESTNET,
                    ScriptType.P2WPKH
            );


            // Obtains the wallet's current Bitcoin receiving address.
            // Bitcoin can be sent to this address after the wallet
            // is connected to the Bitcoin network.
            String receiveAddress =
                    bitcoinWallet.currentReceiveAddress().toString();

            // Places the generated Bitcoin Testnet receiving address
            // inside the wallet address TextView.
            walletAddressText.setText(receiveAddress);

            // Makes the wallet address visible after the Bitcoin
            // wallet has been successfully generated.
            walletAddressText.setVisibility(TextView.VISIBLE);


            // Retrieves the mnemonic recovery words associated with the
            // deterministic key chain of the newly generated Bitcoin wallet.
            java.util.List<String> mnemonicWords =
                    bitcoinWallet.getActiveKeyChain().getMnemonicCode();

            // Checks that the wallet contains a mnemonic recovery phrase
            // before attempting to display it.
            if (mnemonicWords != null) {

                // Combines the individual mnemonic words into a single
                // space-separated recovery phrase for display.
                String recoveryPhrase =
                        String.join(" ", mnemonicWords);

                // Places the generated recovery phrase inside the TextView.
                recoveryPhraseText.setText(recoveryPhrase);

                // Makes the recovery phrase visible after wallet generation.
                recoveryPhraseText.setVisibility(TextView.VISIBLE);
            }


            // Attempts to securely save the newly generated wallet.
            try {

                // Encrypts and saves the newly generated Bitcoin wallet
                // inside the application's private Android storage.
                WalletStorageManager.saveWallet(
                        CreateWalletActivity.this,
                        bitcoinWallet
                );

            } catch (Exception exception) {

                // Displays an error message if the wallet cannot be
                // encrypted or saved successfully.
                Toast.makeText(
                        CreateWalletActivity.this,
                        "Wallet could not be saved.",
                        Toast.LENGTH_LONG
                ).show();

                // Stops the remaining wallet-created confirmation code
                // because the wallet was not successfully saved.
                return;
            }


            // Displays a confirmation that the wallet was successfully
            // generated and securely saved.
            Toast.makeText(
                    CreateWalletActivity.this,
                    "Wallet created and saved.",
                    Toast.LENGTH_LONG
            ).show();
        });


        // Executes when the user selects Load Saved Wallet.
        loadWalletButton.setOnClickListener(view -> {

            try {

                // Decrypts and reconstructs the Bitcoin wallet previously
                // saved in the application's private internal storage.
                bitcoinWallet =
                        WalletStorageManager.loadWallet(
                                CreateWalletActivity.this
                        );

                // Retrieves the current receiving address from the
                // reconstructed wallet.
                String loadedAddress =
                        bitcoinWallet.currentReceiveAddress().toString();

                // Places the loaded wallet's receiving address on the screen
                // so it can be compared with the originally generated address.
                walletAddressText.setText(loadedAddress);

                // Makes the loaded wallet address visible.
                walletAddressText.setVisibility(TextView.VISIBLE);

                // Displays confirmation that the encrypted wallet
                // was successfully decrypted and reconstructed.
                Toast.makeText(
                        CreateWalletActivity.this,
                        "Saved wallet loaded successfully.",
                        Toast.LENGTH_LONG
                ).show();

            } catch (Exception exception) {

                // Displays an error message if no saved wallet exists
                // or if the stored wallet cannot be decrypted or loaded.
                Toast.makeText(
                        CreateWalletActivity.this,
                        "Saved wallet could not be loaded.",
                        Toast.LENGTH_LONG
                ).show();
            }
        });


        // Executes when the user selects Back.
        backButton.setOnClickListener(view -> {

            // Closes CreateWalletActivity and returns the user
            // to the Activity that opened it, which is MainActivity.
            finish();
        });
    }
}