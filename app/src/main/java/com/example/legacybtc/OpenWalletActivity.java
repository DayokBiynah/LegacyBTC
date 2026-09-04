package com.example.legacybtc;

// Imports Bundle so the Activity can receive saved state information
// when Android creates or recreates this screen.
import android.os.Bundle;

// Imports Button so the Activity can control the buttons
// defined in activity_open_wallet.xml.
import android.widget.Button;

// Imports TextView so the Activity can display the receiving
// address of the wallet loaded from secure storage.
import android.widget.TextView;

// Imports Toast so the Activity can display status and
// error messages to the user.
import android.widget.Toast;

// Provides the Android Activity functionality used by this screen.
import androidx.appcompat.app.AppCompatActivity;

// Imports the bitcoinj Wallet class so the Activity can
// work with the reconstructed Bitcoin wallet.
import org.bitcoinj.wallet.Wallet;


/**
 * OpenWalletActivity controls the screen used to open
 * a Bitcoin wallet already stored on the device.
 *
 * The Activity uses WalletStorageManager to decrypt and
 * reconstruct the saved bitcoinj Wallet object.
 */
public class OpenWalletActivity extends AppCompatActivity {

    // Stores a reference to the button used to open
    // the encrypted Bitcoin wallet stored on the device.
    private Button openSavedWalletButton;

    // Stores a reference to the Back button so the Activity
    // can return the user to the previous screen.
    private Button backButton;

    // Stores a reference to the TextView used to display
    // the receiving address of the loaded wallet.
    private TextView walletAddressText;

    // Stores the Bitcoin wallet reconstructed from
    // the encrypted wallet file.
    private Wallet bitcoinWallet;


    /**
     * Executes when OpenWalletActivity is created.
     *
     * This method loads the XML interface, connects its controls
     * to Java variables, and defines the actions performed by
     * the Open Wallet and Back buttons.
     *
     * @param savedInstanceState contains saved Activity state
     *                           if Android recreates the screen
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Initializes the Activity through the Android lifecycle.
        super.onCreate(savedInstanceState);

        // Loads activity_open_wallet.xml as the interface
        // controlled by this Activity.
        setContentView(R.layout.activity_open_wallet);


        // Connects the Open Saved Wallet button from the XML layout
        // to its Java variable.
        openSavedWalletButton =
                findViewById(R.id.openSavedWalletButton);

        // Connects the Back button from the XML layout
        // to its Java variable.
        backButton =
                findViewById(R.id.backButton);

        // Connects the wallet address TextView from the XML layout
        // to its Java variable.
        walletAddressText =
                findViewById(R.id.walletAddressText);


        // Executes when the user selects Open Saved Wallet.
        openSavedWalletButton.setOnClickListener(view -> {

            try {

                // Decrypts the wallet file stored in the application's
                // private storage and reconstructs the bitcoinj Wallet.
                bitcoinWallet =
                        WalletStorageManager.loadWallet(
                                OpenWalletActivity.this
                        );

                // Retrieves the current Bitcoin receiving address
                // from the reconstructed wallet.
                String loadedAddress =
                        bitcoinWallet.currentReceiveAddress().toString();

                // Displays the loaded wallet's receiving address
                // directly on the screen.
                walletAddressText.setText(loadedAddress);

                // Makes the address visible after the wallet
                // has been successfully loaded.
                walletAddressText.setVisibility(TextView.VISIBLE);

                // Confirms that the encrypted wallet was successfully
                // opened and reconstructed.
                Toast.makeText(
                        OpenWalletActivity.this,
                        "Wallet opened successfully.",
                        Toast.LENGTH_LONG
                ).show();

            } catch (Exception exception) {

                // Displays an error message if no saved wallet exists
                // or if the encrypted wallet cannot be opened.
                Toast.makeText(
                        OpenWalletActivity.this,
                        "Saved wallet could not be opened.",
                        Toast.LENGTH_LONG
                ).show();
            }
        });


        // Executes when the user selects Back.
        backButton.setOnClickListener(view -> {

            // Closes OpenWalletActivity and returns the user
            // to MainActivity.
            finish();
        });
    }
}