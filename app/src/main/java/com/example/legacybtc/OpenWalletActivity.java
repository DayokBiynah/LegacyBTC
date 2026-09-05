package com.example.legacybtc;

// Imports Bundle so the Activity can receive saved state information
// when Android creates or recreates this screen.
import android.os.Bundle;

// Imports Button so the Activity can create and control
// wallet selection and deletion buttons.
import android.widget.Button;

// Imports LinearLayout so wallet entries can be added
// dynamically to the Open Existing Wallet screen.
import android.widget.LinearLayout;

// Imports TextView so the Activity can display wallet
// addresses and status information.
import android.widget.TextView;

// Imports Toast so the Activity can display status
// and error messages to the user.
import android.widget.Toast;

// Provides the Android Activity functionality used by this screen.
import androidx.appcompat.app.AppCompatActivity;

// Provides a confirmation dialog before a wallet
// is permanently removed from local application storage.
import androidx.appcompat.app.AlertDialog;

// Imports the bitcoinj Wallet class so saved Bitcoin
// wallets can be reconstructed from encrypted storage.
import org.bitcoinj.wallet.Wallet;


/**
 * OpenWalletActivity controls the screen used to view,
 * open, and delete Bitcoin wallets stored on the device.
 *
 * The Activity retrieves all encrypted LegacyBTC wallet files,
 * loads each wallet, displays its receiving address, and allows
 * the user to select or remove a specific saved wallet.
 */
public class OpenWalletActivity extends AppCompatActivity {

    // Stores a reference to the layout used to display
    // all encrypted Bitcoin wallets saved by LegacyBTC.
    private LinearLayout savedWalletsContainer;

    // Stores a reference to the Back button so the Activity
    // can return the user to the previous screen.
    private Button backButton;


    /**
     * Executes when OpenWalletActivity is created.
     *
     * This method loads the XML layout, connects its controls
     * to Java variables, and displays all encrypted wallets
     * currently stored by LegacyBTC.
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


        // Connects the saved-wallet container from the XML layout
        // to its Java variable.
        savedWalletsContainer =
                findViewById(R.id.savedWalletsContainer);

        // Connects the Back button from the XML layout
        // to its Java variable.
        backButton =
                findViewById(R.id.backButton);


        // Loads and displays all encrypted wallets stored
        // inside LegacyBTC's private application storage.
        displaySavedWallets();


        // Executes when the user selects Back.
        backButton.setOnClickListener(view -> {

            // Closes OpenWalletActivity and returns
            // the user to MainActivity.
            finish();
        });
    }


    /**
     * Retrieves every encrypted LegacyBTC wallet file and
     * creates a selectable screen entry for each wallet.
     *
     * Each wallet entry contains a button for opening the wallet
     * and a separate button for deleting its encrypted file.
     */
    private void displaySavedWallets() {

        // Removes any existing views before rebuilding the wallet list.
        // This prevents duplicate visual entries when the list is refreshed.
        savedWalletsContainer.removeAllViews();


        // Retrieves the filenames of every encrypted wallet
        // currently stored by LegacyBTC.
        String[] walletFiles =
                WalletStorageManager.getSavedWalletFiles(
                        OpenWalletActivity.this
                );


        // Displays a message when no saved wallets are found.
        if (walletFiles.length == 0) {

            // Creates a TextView used to inform the user
            // that no encrypted wallets are currently stored.
            TextView noWalletsText =
                    new TextView(
                            OpenWalletActivity.this
                    );

            // Sets the informational message displayed on screen.
            noWalletsText.setText(
                    "No saved wallets found."
            );

            // Sets the text size for readability.
            noWalletsText.setTextSize(16);

            // Adds spacing around the message.
            noWalletsText.setPadding(
                    16,
                    16,
                    16,
                    16
            );

            // Adds the message to the saved-wallet container.
            savedWalletsContainer.addView(
                    noWalletsText
            );

            return;
        }


        // Processes each encrypted wallet file individually.
        for (String walletFileName : walletFiles) {

            try {

                // Decrypts and reconstructs the wallet associated
                // with the current encrypted wallet file.
                Wallet wallet =
                        WalletStorageManager.loadWallet(
                                OpenWalletActivity.this,
                                walletFileName
                        );


                // Retrieves the wallet's current Bitcoin
                // receiving address.
                String walletAddress =
                        wallet.currentReceiveAddress()
                                .toString();


                /*
                 * Creates a horizontal layout representing one wallet.
                 *
                 * The row contains:
                 * 1. A button displaying the wallet address.
                 * 2. A Delete button for removing that wallet.
                 */
                LinearLayout walletRow =
                        new LinearLayout(
                                OpenWalletActivity.this
                        );

                // Places the wallet controls beside each other.
                walletRow.setOrientation(
                        LinearLayout.HORIZONTAL
                );

                // Vertically centers the wallet and Delete buttons
                // so both controls align evenly within the row.
                walletRow.setGravity(
                        android.view.Gravity.CENTER_VERTICAL
                );


                // Defines the size and spacing of the wallet row.
                LinearLayout.LayoutParams rowLayoutParameters =
                        new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                        );

                // Adds space below each wallet entry.
                rowLayoutParameters.setMargins(
                        0,
                        0,
                        0,
                        16
                );

                // Applies the layout settings to the wallet row.
                walletRow.setLayoutParams(
                        rowLayoutParameters
                );


                // Creates the button used to represent
                // and select this saved Bitcoin wallet.
                Button walletButton =
                        new Button(
                                OpenWalletActivity.this
                        );

                // Displays the wallet receiving address so the
                // user can identify which wallet is being opened.
                walletButton.setText(
                        walletAddress
                );


                // Gives the wallet button most of the available row width
                // while leaving space for the Delete button.
                LinearLayout.LayoutParams walletButtonParameters =
                        new LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1
                        );

                // Adds spacing between the wallet button
                // and the Delete button.
                walletButtonParameters.setMargins(
                        0,
                        0,
                        12,
                        0
                );

                walletButton.setLayoutParams(
                        walletButtonParameters
                );


                // Executes when the user selects this saved wallet.
                walletButton.setOnClickListener(view -> {

                    // Confirms which wallet was selected.
                    Toast.makeText(
                            OpenWalletActivity.this,
                            "Wallet opened:\n" + walletAddress,
                            Toast.LENGTH_LONG
                    ).show();
                });


                // Creates the button used to remove this
                // specific wallet from device storage.
                Button deleteButton =
                        new Button(
                                OpenWalletActivity.this
                        );

                // Labels the wallet deletion action.
                deleteButton.setText(
                        "Delete"
                );


                // Keeps the Delete button at its natural width
                // while aligning it with the wallet button.
                LinearLayout.LayoutParams deleteButtonParameters =
                        new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                        );

                deleteButton.setLayoutParams(
                        deleteButtonParameters
                );


                // Executes when the user selects Delete.
                deleteButton.setOnClickListener(view -> {

                    /*
                     * Creates a confirmation dialog before deletion.
                     *
                     * This prevents an accidental button press from
                     * immediately removing the encrypted wallet file.
                     */
                    new AlertDialog.Builder(
                            OpenWalletActivity.this
                    )
                            .setTitle(
                                    "Delete Wallet"
                            )

                            .setMessage(
                                    "Remove this wallet from this device?"
                            )

                            // Defines the action performed when
                            // the user confirms deletion.
                            .setPositiveButton(
                                    "Delete",
                                    (dialog, which) -> {

                                        /*
                                         * Deletes only the encrypted file
                                         * belonging to this wallet.
                                         *
                                         * Other saved wallets are not affected.
                                         */
                                        boolean deleted =
                                                WalletStorageManager.deleteWallet(
                                                        OpenWalletActivity.this,
                                                        walletFileName
                                                );


                                        // Checks whether Android successfully
                                        // removed the encrypted wallet file.
                                        if (deleted) {

                                            // Removes the wallet row from the
                                            // visible wallet list immediately.
                                            savedWalletsContainer.removeView(
                                                    walletRow
                                            );

                                            // Confirms successful local deletion.
                                            Toast.makeText(
                                                    OpenWalletActivity.this,
                                                    "Wallet deleted.",
                                                    Toast.LENGTH_LONG
                                            ).show();


                                            /*
                                             * Checks whether any saved wallets
                                             * remain after the deletion.
                                             *
                                             * If the deleted wallet was the last
                                             * wallet, the screen is rebuilt so
                                             * "No saved wallets found." appears.
                                             */
                                            String[] remainingWalletFiles =
                                                    WalletStorageManager
                                                            .getSavedWalletFiles(
                                                                    OpenWalletActivity.this
                                                            );

                                            if (remainingWalletFiles.length == 0) {

                                                displaySavedWallets();
                                            }

                                        } else {

                                            // Displays an error if Android
                                            // could not delete the wallet file.
                                            Toast.makeText(
                                                    OpenWalletActivity.this,
                                                    "Wallet could not be deleted.",
                                                    Toast.LENGTH_LONG
                                            ).show();
                                        }
                                    }
                            )

                            // Closes the dialog without deleting anything.
                            .setNegativeButton(
                                    "Cancel",
                                    null
                            )

                            // Displays the completed confirmation dialog.
                            .show();
                });


                // Adds the wallet selection button
                // to this wallet's horizontal row.
                walletRow.addView(
                        walletButton
                );

                // Adds the Delete button beside the
                // wallet selection button.
                walletRow.addView(
                        deleteButton
                );

                // Adds the complete wallet row to the
                // visible saved-wallet list.
                savedWalletsContainer.addView(
                        walletRow
                );


            } catch (Exception exception) {

                // Creates an error message if a particular encrypted
                // wallet file cannot be decrypted or reconstructed.
                TextView errorText =
                        new TextView(
                                OpenWalletActivity.this
                        );

                // Explains that one stored wallet could not be opened.
                errorText.setText(
                        "A saved wallet could not be opened."
                );

                // Adds the error message to the wallet list.
                savedWalletsContainer.addView(
                        errorText
                );
            }
        }
    }
}