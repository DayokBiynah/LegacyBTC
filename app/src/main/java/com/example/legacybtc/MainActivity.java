package com.example.legacybtc;

// Imports Bundle to receive any saved state information when
// MainActivity is created or recreated by the Android operating system.
import android.os.Bundle;

// Imports the Button class so MainActivity can access and respond
// to the Create Wallet and Restore Wallet buttons defined in the XML layout.
import android.widget.Button;

// Imports Toast to display a short on-screen confirmation after
// the user selects the Create Wallet or Restore Wallet option.
import android.widget.Toast;

// Imports AppCompatActivity, which provides the Android Activity
// functionality required for MainActivity to operate as an application screen.
import androidx.appcompat.app.AppCompatActivity;

// Imports Intent so MainActivity can open another Activity
// when the user selects an option from the entry screen.
import android.content.Intent;



/**
 * MainActivity is the entry screen of the LegacyBTC application.
 *
 * The Activity displays the options for creating a new Bitcoin wallet
 * or restoring an existing Bitcoin wallet and handles user interaction
 * with those controls.
 */
public class MainActivity extends AppCompatActivity {

    // Stores a reference to the Create Wallet button so its click
    // event can be handled from Java.
    private Button createWalletButton;

    // Stores a reference to the Restore Wallet button so its click
    // event can be handled from Java.
    private Button restoreWalletButton;

    /**
     * Executes when MainActivity is created.
     *
     * This method loads the activity_main.xml interface, connects the
     * XML buttons to their Java variables, and defines the action that
     * occurs when either button is selected.
     *
     * @param savedInstanceState contains previously saved Activity state
     *                           when Android recreates the Activity
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

        // Registers a click listener that executes when the user selects
        // the Restore Wallet button.
        restoreWalletButton.setOnClickListener(view -> {

            // Displays "Restore Wallet selected" at the bottom of the screen
            // for a short period to confirm that the button click was detected.
            Toast.makeText(
                    MainActivity.this,
                    "Restore Wallet selected",
                    Toast.LENGTH_SHORT
            ).show();
        });
    }
}