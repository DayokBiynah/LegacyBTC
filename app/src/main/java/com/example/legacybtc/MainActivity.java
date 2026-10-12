package com.example.legacybtc;

// Imports Bundle to receive any saved state information when
// MainActivity is created or recreated by the Android operating system.
import android.os.Bundle;

// Imports the Button class so MainActivity can access and respond
// to the Create Wallet and Restore Wallet buttons defined in the XML layout.
import android.widget.Button;

// Imports Intent so MainActivity can open another Activity
// when the user selects an option from the entry screen.
import android.content.Intent;



/**
 * I provide the public welcome navigation to Create/Restore and Open Existing Wallet. These
 * buttons create Android Intents to the appropriate Activities and do not load private wallet
 * information. The common WalletActivity handles screenshot protection and subsequent secured
 * screens.
 */
public class MainActivity extends WalletActivity {
    // The public welcome screen can be shown without unlocking wallet data.
    @Override protected boolean requiresUnlock() { return false; }

    // Stores a reference to the Create Wallet button so its click
    // event can be handled from Java.
    private Button createWalletButton;

    // Stores a reference to the Restore Wallet button so its click
    // event can be handled from Java.
    private Button restoreWalletButton;

    // Stores a reference to the Open Existing Wallet button so the
    // Activity can respond when the user selects it.
    private Button openWalletButton;


    /**
     * Android calls this lifecycle method when creating or recreating the component. I
     * initialize the parent behavior, connect Java listeners to the XML controls or register
     * application callbacks, and restore only the workflow state explicitly handled below.
     * Button callbacks capture input on the UI thread and delegate expensive wallet work to
     * the service. Errors are displayed instead of silently continuing.
     * @param savedInstanceState Bundle supplied by the calling workflow as described above
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Calls the AppCompatActivity implementation of onCreate()
        // so Android can perform the standard Activity initialization.
        super.onCreate(savedInstanceState);

        // Loads activity_main.xml and displays it as the interface
        // controlled by MainActivity.
        setContentView(R.layout.activity_main);

        // Finds the XML element whose ID is createWalletButton and stores
        // its reference so MainActivity can respond when the button is clicked.
        createWalletButton = findViewById(R.id.createWalletButton);

        // Finds the XML element whose ID is restoreWalletButton and stores
        // its reference so MainActivity can respond when the button is clicked.
        restoreWalletButton = findViewById(R.id.restoreWalletButton);

        // Connects the Open Existing Wallet button from the XML layout
        // to its Java variable.
        openWalletButton =
                findViewById(R.id.openWalletButton);


        // Registers a click listener that executes when the user selects
        // the Create Wallet button.
        createWalletButton.setOnClickListener(view -> {

            // Creates an Intent that identifies CreateWalletActivity as the
            // screen that should open from MainActivity.
            Intent createWalletIntent = new Intent(
                    MainActivity.this,
                    CreateWalletActivity.class
            );

            // Starts CreateWalletActivity and displays the Create Wallet screen.
            startActivity(createWalletIntent);
        });

        // Executes when the user selects Restore Wallet.
        restoreWalletButton.setOnClickListener(view -> {

            // Creates an Intent that opens the screen used to
            // reconstruct a wallet from its recovery phrase.
            Intent restoreWalletIntent =
                    new Intent(
                            MainActivity.this,
                            RestoreWalletActivity.class
                    );

            // Opens RestoreWalletActivity.
            startActivity(restoreWalletIntent);
        });

        // Executes when the user selects Open Existing Wallet.
        openWalletButton.setOnClickListener(view -> {

            // Creates an Intent that opens the screen used to access
            // a Bitcoin wallet already stored on the device.
            Intent openWalletIntent =
                    new Intent(
                            MainActivity.this,
                            OpenWalletActivity.class
                    );

            // Opens OpenWalletActivity.
            startActivity(openWalletIntent);
        });

    }
}
