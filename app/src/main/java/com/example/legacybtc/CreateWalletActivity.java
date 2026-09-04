package com.example.legacybtc;

// Imports Bundle so the Activity can receive saved state information
// when Android creates or recreates this screen.
import android.os.Bundle;

// Imports Button so this Activity can control the buttons
// defined in activity_create_wallet.xml.
import android.widget.Button;

// Imports Toast so the Activity can display a short confirmation
// message when the Generate Wallet button is selected.
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
 * CreateWalletActivity controls the screen used to begin
 * the creation of a new LegacyBTC Bitcoin wallet.
 *
 * This Activity connects the Generate Wallet and Back buttons
 * from the XML layout to their Java actions.
 */
public class CreateWalletActivity extends AppCompatActivity {

    // Stores a reference to the Generate Wallet button so the
    // Activity can respond when the user selects it.
    private Button generateWalletButton;

    // Stores a reference to the Back button so the Activity can
    // return the user to the previous screen.
    private Button backButton;

    // Stores the Bitcoin wallet created by bitcoinj.
    // The Wallet object manages the deterministic key hierarchy,
    // receiving addresses, balances, and Bitcoin transactions.
    private Wallet bitcoinWallet;


    /**
     * Executes when CreateWalletActivity is created.
     *
     * This method loads the XML layout, connects the XML buttons
     * to Java variables, and defines the actions performed when
     * the buttons are selected.
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
        generateWalletButton = findViewById(R.id.generateWalletButton);

        // Finds the Back button in the XML layout and connects
        // it to the Java variable.
        backButton = findViewById(R.id.backButton);

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
             * generated deterministic seed. That seed can later recreate
             * the same wallet and its Bitcoin keys.
             */
            bitcoinWallet = Wallet.createDeterministic(
                    BitcoinNetwork.TESTNET,
                    ScriptType.P2WPKH
            );

            // Obtains the wallet's current Bitcoin receiving address.
            // Bitcoin can be sent to this address after the wallet is
            // connected to the Bitcoin network.
            String receiveAddress =
                    bitcoinWallet.currentReceiveAddress().toString();

            // Displays the generated Testnet Bitcoin address so the wallet
            // creation process can be verified during development.
            Toast.makeText(
                    CreateWalletActivity.this,
                    "Wallet created:\n" + receiveAddress,
                    Toast.LENGTH_LONG
            ).show();
        });

        // Executes when the user selects Back.
        backButton.setOnClickListener(view -> {

            // Closes CreateWalletActivity and returns the user
            // to the Activity that opened it, which is MainActivity.
            finish();
        });
    }
}