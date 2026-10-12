package com.example.legacybtc;

import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.function.Consumer;

/**
 * I provide the common Android screen behavior: FLAG_SECURE, device-credential authentication,
 * system/keyboard insets, errors, navigation, and asynchronous operations. Financial actions
 * run only after their reviewed callback passes Android authentication. I retain no private
 * keys in Intent or Bundle data. Background results return to the main thread and are ignored
 * after this Activity finishes.
 */
public abstract class WalletActivity extends AppCompatActivity {
    protected BitcoinService service;
    private Runnable confirmedAction;
    private boolean unlockingScreen;
    private ActivityResultLauncher<Intent> authentication;
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
        service = BitcoinService.get(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        authentication = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            Runnable action = confirmedAction;
            confirmedAction = null;
            LegacyBtcApplication app = (LegacyBtcApplication) getApplication();
            app.setAuthenticating(false);
            if (result.getResultCode() == RESULT_OK) {
                app.setUnlocked(true);
                if (action != null) action.run();
                else showError("Screen was recreated. Review the action again.");
            } else {
                onAuthenticationCancelled();
                if (unlockingScreen) finish();
                else showError("Authentication cancelled. No transaction was sent.");
            }
            unlockingScreen = false;
        });
    }
    protected boolean requiresUnlock() { return true; }
    protected void onAuthenticationCancelled() { }
    /**
     * Android calls this when the screen returns to the foreground. I invoke parent lifecycle
     * handling and start the public-data refresh or wallet-list behavior shown below. Secured
     * screens enforce the application unlock state. The paired pause method stops periodic
     * callbacks so background screens do not keep refreshing.
     */
    @Override protected void onResume() {
        super.onResume();
        View content = findViewById(android.R.id.content);
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        LegacyBtcApplication app = (LegacyBtcApplication) getApplication();
        if (requiresUnlock() && keyguard.isDeviceSecure() && !app.isUnlocked()) {
            content.setVisibility(View.INVISIBLE);
            if (confirmedAction == null) {
                unlockingScreen = true;
                authenticate(() -> content.setVisibility(View.VISIBLE));
            }
        } else content.setVisibility(View.VISIBLE);
    }
    /**
     * Android calls this as the screen leaves the foreground. I stop this screen's refresh
     * callbacks or conceal sensitive displayed data as implemented below, then call the parent
     * lifecycle method. Wallet mutations remain serialized by the shared service rather than
     * being discarded with the Activity.
     */
    @Override protected void onPause() {
        if (requiresUnlock()) findViewById(android.R.id.content).setVisibility(View.INVISIBLE);
        super.onPause();
    }
    /**
     * I load the XML layout through the parent Activity, then attach system-bar and keyboard
     * insets to its root while preserving the original padding. I register shared feedback
     * listeners after views exist. This keeps controls accessible under Android edge-to-edge
     * layouts.
     * @param layout int supplied by the calling workflow as described above
     */
    @Override public void setContentView(int layout) {
        super.setContentView(layout);
        View root = ((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        int left = root.getPaddingLeft(), top = root.getPaddingTop(), right = root.getPaddingRight(), bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
            return insets;
        });
        watchFeedback(R.id.errorText);
        watchFeedback(R.id.confirmationText);
        watchFeedback(R.id.restoredAddressText);
    }
    /**
     * I attach a TextWatcher to an optional feedback view so empty text hides its card and
     * nonempty text restores visibility. Android text callbacks change only presentation;
     * wallet decisions remain in the service.
     * @param id int supplied by the calling workflow as described above
     */
    private void watchFeedback(int id) {
        TextView message = findViewById(id);
        if (message == null) return;
        message.setVisibility(message.length() == 0 ? View.GONE : View.VISIBLE);
        message.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                message.setVisibility(value.length() == 0 ? View.GONE : View.VISIBLE);
            }
            @Override public void afterTextChanged(android.text.Editable value) { }
        });
    }
    /**
     * I receive a service Operation and a success Consumer, submit the operation to the wallet
     * worker, and return its completion to the Android main thread. Finished or destroyed
     * screens ignore results. I unwrap exception causes for a visible error and call the
     * success callback only after successful completion.
     * @param operation BitcoinService.Operation<T> supplied by the calling workflow as described above
     * @param success Consumer<T> supplied by the calling workflow as described above
     */
    protected <T> void work(BitcoinService.Operation<T> operation, Consumer<T> success) {
        service.submit(operation).whenComplete((result, error) -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (error != null) {
                Throwable cause = error;
                while (cause.getCause() != null) cause = cause.getCause();
                showError(cause.getMessage() == null ? "Operation failed. Please retry." : cause.getMessage());
            } else success.accept(result);
        }));
    }
    /**
     * I pass a failed-operation String to the shared feedback helper, which displays error
     * styling and readable text. This keeps validation, library, and storage failures visible
     * in the current XML screen.
     * @param message String supplied by the calling workflow as described above
     */
    protected void showError(String message) {
        feedback(message, true);
    }
    /**
     * I pass public progress or completion text to the feedback helper using informational
     * styling. Transaction relay messages distinguish pending confirmation from a mined
     * success.
     * @param message String supplied by the calling workflow as described above
     */
    protected void showMessage(String message) {
        feedback(message, false);
    }
    /**
     * I update the XML error/feedback TextView with the supplied message and boolean error
     * flag. Styling and visibility make new messages readable; if no feedback view exists,
     * Android Toast displays the text. This helper performs no wallet mutation.
     * @param message String supplied by the calling workflow as described above
     * @param problem boolean supplied by the calling workflow as described above
     */
    private void feedback(String message, boolean problem) {
        TextView error = findViewById(R.id.errorText);
        if (error != null) {
            error.setText(problem ? getString(R.string.feedback_error, message) : message);
            error.setTextColor(getColor(problem ? R.color.ui_error : R.color.ui_success));
            error.setBackgroundResource(problem ? R.drawable.bg_error : R.drawable.bg_feedback);
            error.setVisibility(View.VISIBLE);
        }
        else android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
    }
    /**
     * I receive the Runnable representing a reviewed sensitive action. Android KeyguardManager
     * verifies a device PIN, pattern, or password through an Activity Result launcher. An
     * unsecured device is rejected for financial actions. I run the retained callback only for
     * RESULT_OK; cancellation or recreation requires a fresh review rather than reconstructing
     * authorization.
     * @param action Runnable supplied by the calling workflow as described above
     */
    protected void authenticate(Runnable action) {
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (!keyguard.isDeviceSecure()) {
            onAuthenticationCancelled();
            showError("Set a device PIN, pattern, or password in Android Settings before spending or revealing recovery words.");
            return;
        }
        if (confirmedAction != null) return;
        confirmedAction = action;
        ((LegacyBtcApplication) getApplication()).setAuthenticating(true);
        authentication.launch(keyguard.createConfirmDeviceCredentialIntent(getString(R.string.security_prompt_title), getString(R.string.security_prompt_description)));
    }
    /**
     * I open the saved wallet on the service worker before launching WalletDashboardActivity
     * with its filename. Success finishes setup; loading failure displays an error without
     * pretending the wallet is available.
     * @param file String supplied by the calling workflow as described above
     */
    protected void dashboard(String file) {
        work(() -> service.openWallet(file), wallet -> {
            startActivity(new Intent(this, WalletDashboardActivity.class).putExtra("wallet_file", file));
            finish();
        });
    }
}
