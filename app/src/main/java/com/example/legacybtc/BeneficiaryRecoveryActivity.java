package com.example.legacybtc;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.Intent;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import org.bitcoinj.core.Transaction;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;

/**
 * I display funded plan terms and derive eligibility from the verified chain and the opened
 * wallet role. Import and Export use Android document pickers for public JSON recovery
 * packages. Claims start disabled and remain unavailable before the deadline, while offline,
 * or without the beneficiary key. A reviewed claim or cancellation requires device
 * authentication before commitment and relay.
 */
public class BeneficiaryRecoveryActivity extends SelectedWalletActivity {
    private InheritanceService inheritance;
    private String planId;
    private boolean refreshing, reviewing;
    private ActivityResultLauncher<String> exportDocument;
    private ActivityResultLauncher<String[]> importDocument;
    /**
     * Android calls this lifecycle method when creating or recreating the component. I
     * initialize the parent behavior, connect Java listeners to the XML controls or register
     * application callbacks, and restore only the workflow state explicitly handled below.
     * Button callbacks capture input on the UI thread and delegate expensive wallet work to
     * the service. Errors are displayed instead of silently continuing.
     * @param state Bundle supplied by the calling workflow as described above
     */
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_recovery);
        inheritance = new InheritanceService(this);
        planId = state == null ? getIntent().getStringExtra("plan_id") : state.getString("plan_id");
        exportDocument = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
            if (uri == null || planId == null) return;
            String id = planId;
            work(() -> {
                byte[] data = inheritance.exportPlan(id).getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                    if (output == null) throw new IllegalStateException("Could not open the selected document.");
                    output.write(data);
                }
                return true;
            }, ignored -> showMessage("Recovery Package exported. Give it to your beneficiary and keep a backup. It contains public plan information, not private keys."));
        });
        importDocument = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            work(() -> {
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IllegalStateException("Could not read the selected document.");
                    byte[] buffer = new byte[4096]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (data.size() + count > 512_000) throw new IllegalArgumentException("Recovery package is too large.");
                        data.write(buffer, 0, count);
                    }
                }
                return inheritance.importPlan(new String(data.toByteArray(), StandardCharsets.UTF_8));
            }, plan -> { planId = plan.getId(); showMessage("Plan imported. " + WalletPolicy.NETWORK_LABEL + " verification is in progress. Claim stays disabled until verified."); refresh(); });
        });
        findViewById(R.id.checkStatusButton).setOnClickListener(view -> refresh());
        findViewById(R.id.claimButton).setOnClickListener(view -> reviewRecovery(false));
        findViewById(R.id.cancelFundedButton).setOnClickListener(view -> reviewRecovery(true));
        findViewById(R.id.exportPlanButton).setOnClickListener(view -> {
            if (planId != null) exportDocument.launch("LegacyBTC-" + WalletPolicy.NETWORK_LABEL + "-" + planId + ".json");
        });
        findViewById(R.id.importPlanButton).setOnClickListener(view -> { if (walletReady) importDocument.launch(new String[]{"application/json", "text/plain", "application/octet-stream"}); });
        findViewById(R.id.rescanButton).setOnClickListener(view -> {
            if (!walletReady) return;
            new AlertDialog.Builder(this).setTitle(R.string.rescan_history).setMessage(R.string.rescan_warning)
                    .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.check_status, (d, which) ->
                            work(() -> { service.rescan(); return true; }, ignored -> refresh())).show();
        });
        findViewById(R.id.viewDetailsButton).setOnClickListener(view -> {
            if (planId != null) work(() -> inheritance.exportPlan(planId), details -> new AlertDialog.Builder(this)
                    .setTitle(R.string.view_details).setMessage(details).setPositiveButton(R.string.back, null).show());
        });
        findViewById(R.id.backButton).setOnClickListener(view -> finish());
        findViewById(R.id.claimButton).setEnabled(false);
        findViewById(R.id.cancelFundedButton).setEnabled(false);
        loadWallet();
    }
    /**
     * I read the selected wallet's public data and update this screen's displayed address,
     * balance, plan status, or history. The conditions below avoid redundant or overlapping
     * work. Expensive repository/script operations use the worker, and their results return to
     * the main thread. Refresh does not authorize a payment or claim.
     */
    @Override protected void refresh() {
        if (refreshing || !walletReady) return;
        refreshing = true;
        service.submit(() -> {
            java.util.ArrayList<InheritancePlan> plans = inheritance.getPlans();
            InheritancePlan selected = null;
            for (InheritancePlan plan : plans) if (plan.getId().equals(planId)) selected = plan;
            if (selected == null && !plans.isEmpty()) selected = plans.get(plans.size() - 1);
            return new StatusView(plans, selected, selected == null ? null : inheritance.checkInheritanceStatus(selected));
        }).whenComplete((result, error) -> runOnUiThread(() -> {
            refreshing = false;
            if (isDestroyed()) return;
            findViewById(R.id.claimButton).setEnabled(false);
            findViewById(R.id.cancelFundedButton).setEnabled(false);
            findViewById(R.id.exportPlanButton).setEnabled(false);
            ((TextView) findViewById(R.id.recoveryStateText)).setText(R.string.recovery_locked_heading);
            ((TextView) findViewById(R.id.recoveryConditionText)).setText(R.string.recovery_locked_helper);
            if (error != null) { showError(rootMessage(error)); return; }
            LinearLayout list = findViewById(R.id.plansContainer); list.removeAllViews();
            if (result.plans.isEmpty()) list.addView(WalletUi.empty(list, R.string.plan_empty));
            for (InheritancePlan plan : result.plans) {
                list.addView(WalletUi.plan(list, plan, () -> { planId = plan.getId(); refresh(); }));
            }
            if (result.plan == null) { ((TextView) findViewById(R.id.planStatusText)).setText(R.string.no_plan_import); return; }
            InheritancePlan plan = result.plan; planId = plan.getId();
            try {
                User user = plan.userFor(service.requireWallet().getWallet());
                boolean available = user.canRecover(plan, result.status);
                ((TextView) findViewById(R.id.planStatusText)).setText(getString(R.string.role_status_value, user.describeRole(), WalletUi.status(result.status)));
                findViewById(R.id.recoveryDetailsSection).setVisibility(android.view.View.VISIBLE);
                findViewById(R.id.recoveryBeneficiarySection).setVisibility(android.view.View.VISIBLE);
                ((TextView) findViewById(R.id.recoveryAmountText)).setText(getString(R.string.btc_amount, plan.getAmount().toPlainString()));
                ((TextView) findViewById(R.id.recoveryPeriodText)).setText(getString(R.string.recovery_period_value, Long.toString(plan.getPeriodSeconds() / 3600)));
                ((TextView) findViewById(R.id.recoveryDeadlineText)).setText(getString(R.string.recovery_deadline_value, DateFormat.getDateTimeInstance().format(new Date(plan.getUnlockTime() * 1000))));
                ((TextView) findViewById(R.id.recoveryEligibilityText)).setText(getString(R.string.recovery_eligibility_value,
                        getString(result.status == InheritancePlan.Status.AVAILABLE ? R.string.yes : R.string.no), getString(available ? R.string.yes : R.string.no)));
                ((TextView) findViewById(R.id.recoveryBeneficiaryText)).setText(plan.getBeneficiary().getName());
                ((TextView) findViewById(R.id.recoveryAddressText)).setText(plan.getBeneficiary().getAddress());
                ((TextView) findViewById(R.id.recoveryPublicKeyText)).setText(plan.getBeneficiary().getPublicKey());
                ((TextView) findViewById(R.id.recoveryFundingText)).setText(plan.isFunded() ? plan.fundingTransaction().getTxId().toString() : getString(R.string.not_funded));
                findViewById(R.id.claimButton).setEnabled(available && !reviewing);
                ((TextView) findViewById(R.id.recoveryStateText)).setText(available ? R.string.recovery_available_heading : R.string.recovery_locked_heading);
                ((TextView) findViewById(R.id.recoveryConditionText)).setText(available ? R.string.recovery_available_helper : R.string.recovery_locked_helper);
                findViewById(R.id.cancelFundedButton).setEnabled(user.canManage(plan) && !reviewing
                        && (result.status == InheritancePlan.Status.AVAILABLE || result.status == InheritancePlan.Status.TIMELOCKED));
                findViewById(R.id.exportPlanButton).setEnabled(plan.isFunded());
            } catch (Exception e) { showError(e.getMessage()); }
        }));
    }
    /**
     * I capture the selected plan and fee, then ask the worker to construct a claim or
     * cancellation candidate. I display destination, received amount, deducted fee, and
     * network before requesting authentication. Submission rechecks eligibility and saves the
     * transaction before relay. Cancellation of review or authentication clears the guard.
     * @param cancel boolean supplied by the calling workflow as described above
     */
    private void reviewRecovery(boolean cancel) {
        if (!walletReady || planId == null || reviewing) return;
        reviewing = true;
        String id = planId, fee = ((EditText) findViewById(R.id.feeInput)).getText().toString();
        service.submit(() -> inheritance.prepareRecovery(id, fee, cancel)).whenComplete((tx, error) -> runOnUiThread(() -> {
            if (isDestroyed()) return;
            if (error != null) { reviewing = false; showError(rootMessage(error)); return; }
            work(() -> inheritance.getPlan(id), plan -> {
                String message = "Bitcoin " + WalletPolicy.NETWORK_LABEL + "\n" + (cancel ? "Cancel and return funds to the owner's wallet." : "Claim into the beneficiary's wallet.")
                        + "\nRecipient: " + tx.getOutput(0).getScriptPubKey().getToAddress(WalletPolicy.NETWORK)
                        + "\nReceived amount: " + tx.getOutput(0).getValue().toPlainString() + " BTC"
                        + "\nFee deducted: " + plan.getAmount().subtract(tx.getOutput(0).getValue()).toPlainString() + " BTC";
                AlertDialog dialog = new AlertDialog.Builder(this).setTitle(cancel ? R.string.cancel_plan : R.string.claim_recover)
                        .setMessage(message).setNegativeButton(R.string.cancel, (d, which) -> reviewing = false)
                        .setPositiveButton(R.string.confirm_send, (d, which) -> authenticate(() ->
                                service.submit(() -> inheritance.recoverInheritance(id, tx, cancel)).whenComplete((saved, failure) -> runOnUiThread(() -> {
                                    reviewing = false;
                                    if (failure != null) { showError(rootMessage(failure)); return; }
                                    ((TextView) findViewById(R.id.confirmationText)).setText(getString(R.string.saved_pending_id, saved.getTxId().toString()));
                                    refresh();
                                    service.submit(() -> service.broadcast(saved)).thenCompose(future -> future).whenComplete((sent, problem) -> runOnUiThread(() ->
                                            showMessage(problem == null ? "Transaction relayed. Awaiting confirmation." : "Relay not confirmed. Retry the saved transaction from History.")));
                                })))).create();
                dialog.setOnCancelListener(d -> reviewing = false); dialog.show();
            });
        }));
    }
    private static final class StatusView {
        final java.util.ArrayList<InheritancePlan> plans; final InheritancePlan plan; final InheritancePlan.Status status;
        StatusView(java.util.ArrayList<InheritancePlan> plans, InheritancePlan plan, InheritancePlan.Status status) {
            this.plans = plans; this.plan = plan; this.status = status;
        }
    }
    /**
     * I unwrap nested asynchronous exceptions to their underlying cause and return a user-
     * facing message, with a fallback when the cause has no text. The interface displays this
     * String and leaves the operation incomplete.
     * @param error Throwable supplied by the calling workflow as described above
     * @return the underlying exception message or a readable fallback
     */
    private String rootMessage(Throwable error) { while (error.getCause() != null) error = error.getCause(); return error.getMessage() == null ? "Could not verify the inheritance plan." : error.getMessage(); }
    @Override protected void onAuthenticationCancelled() { reviewing = false; }
    /**
     * I receive the Bundle supplied by Android before recreation. I save only explicit
     * workflow identifiers and flags, such as a wallet filename, selected plan ID, or
     * submitted transaction ID. Mnemonic words and signing keys are not placed in this Bundle.
     * New Activity instances reconstruct their screen through the existing service.
     * @param state Bundle supplied by the calling workflow as described above
     */
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putString("plan_id", planId); }
}
