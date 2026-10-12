package com.example.legacybtc;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Button;
import androidx.appcompat.app.AlertDialog;
import java.text.DateFormat;
import java.util.Date;

/**
 * I collect the owner name, beneficiary public key and matching address, covered BTC amount,
 * and period in hours. Add Beneficiary and Set Period validate fields; Save and Update persist
 * draft terms. Funding is a distinct reviewed and authenticated Bitcoin payment. Funded
 * scripts cannot be edited locally. Cancel routes funded plans to an owner-signed on-chain
 * cancellation.
 */
public class InheritanceSetupActivity extends SelectedWalletActivity {
    private InheritanceService inheritance;
    private String planId;
    private Beneficiary beneficiary;
    private Long period;
    private boolean loadingPlans, funding;
    /**
     * Android calls this lifecycle method when creating or recreating the component. I
     * initialize the parent behavior, connect Java listeners to the XML controls or register
     * application callbacks, and restore only the workflow state explicitly handled below.
     * Button callbacks capture input on the UI thread and delegate expensive wallet work to
     * the service. Errors are displayed instead of silently continuing.
     * @param state Bundle supplied by the calling workflow as described above
     */
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_inheritance_setup);
        inheritance = new InheritanceService(this);
        planId = state == null ? null : state.getString("plan_id");
        findViewById(R.id.addBeneficiaryButton).setOnClickListener(view -> {
            try { beneficiary = readBeneficiary(); showMessage("Beneficiary validated. Save the plan to keep this information."); }
            catch (Exception e) { showError(e.getMessage()); }
        });
        findViewById(R.id.setPeriodButton).setOnClickListener(view -> {
            try { period = inheritance.setInheritancePeriod(input(R.id.periodInput)); showMessage("Period validated. The calendar deadline starts when you save or update the draft."); }
            catch (Exception e) { showError(e.getMessage()); }
        });
        findViewById(R.id.savePlanButton).setOnClickListener(view -> savePlan(false));
        findViewById(R.id.updatePlanButton).setOnClickListener(view -> savePlan(true));
        findViewById(R.id.fundPlanButton).setOnClickListener(view -> fundPlan());
        findViewById(R.id.cancelPlanButton).setOnClickListener(view -> {
            if (planId == null) { showError("Select a saved plan first."); return; }
            work(() -> inheritance.getPlan(planId), plan -> {
                if (plan.isFunded()) openRecovery(plan.getId(), true);
                else new AlertDialog.Builder(this).setTitle(R.string.cancel_plan).setMessage(R.string.cancel_draft_warning)
                        .setNegativeButton(R.string.back, null).setPositiveButton(R.string.cancel_plan, (d, which) ->
                                work(() -> { inheritance.cancelDraft(planId); return true; }, ignored -> { planId = null; refresh(); })).show();
            });
        });
        findViewById(R.id.recoveryButton).setOnClickListener(view -> openRecovery(planId, false));
        findViewById(R.id.backButton).setOnClickListener(view -> finish()); loadWallet();
    }
    /**
     * I read the EditText identified by the supplied Android resource ID and return its
     * current String. Callers capture this value before submitting worker operations, then run
     * domain validation. Reading text alone does not establish a valid amount, address, or
     * period.
     * @param id int supplied by the calling workflow as described above
     * @return the current EditText String captured for subsequent validation
     */
    private String input(int id) { return ((EditText) findViewById(id)).getText().toString(); }
    /**
     * I gather the latest name, compressed-key hex, and address Strings from setup fields and
     * pass them to InheritanceService.addBeneficiary. Constructor validation prevents saving a
     * mismatched address/key identity. The returned Beneficiary contains only public
     * information.
     * @return the latest validated public beneficiary from setup fields
     */
    private Beneficiary readBeneficiary() { return inheritance.addBeneficiary(input(R.id.beneficiaryNameInput), input(R.id.beneficiaryKeyInput), input(R.id.beneficiaryAddressInput)); }
    /**
     * I validate the latest beneficiary, period, and amount fields at the time of saving
     * rather than relying on earlier validation buttons. The update boolean chooses an
     * existing owner draft versus a new draft. The worker saves the terms and the main-thread
     * callback selects and displays the resulting plan.
     * @param update boolean supplied by the calling workflow as described above
     */
    private void savePlan(boolean update) {
        if (!walletReady) return;
        try {
            beneficiary = readBeneficiary(); period = inheritance.setInheritancePeriod(input(R.id.periodInput));
            String name = input(R.id.ownerNameInput), amount = input(R.id.inheritanceAmountInput);
            if (update && planId == null) throw new IllegalArgumentException("Select a plan to update.");
            Beneficiary validated = beneficiary; long seconds = period;
            work(() -> update ? inheritance.updatePlan(planId, validated, amount, seconds)
                    : inheritance.createInheritancePlan(name, validated, amount, seconds), plan -> {
                planId = plan.getId(); displayPlan(plan); refresh();
            });
        } catch (Exception error) { showError(error.getMessage()); }
    }
    /**
     * I format public plan terms into the setup summary using BTC text, integer hours, and an
     * Android locale date/time. The local draft/funded label describes saved terms; chain-
     * verified claim eligibility remains on the recovery screen.
     * @param plan InheritancePlan supplied by the calling workflow as described above
     */
    private void displayPlan(InheritancePlan plan) {
        ((TextView) findViewById(R.id.planSummaryText)).setText(getString(R.string.plan_summary_ui,
                plan.getBeneficiary().getName(), plan.getAmount().toPlainString(), Long.toString(plan.getPeriodSeconds() / 3600),
                DateFormat.getDateTimeInstance().format(new Date(plan.getUnlockTime() * 1000)),
                getString(plan.isFunded() ? R.string.plan_funded : plan.isCancelled() ? R.string.plan_cancelled : R.string.plan_draft)));
    }
    /**
     * I retain the selected plan ID while the worker prepares funding. I show beneficiary,
     * amount, fee, total, deadline, and network in a confirmation dialog. Authentication gates
     * commitment; failures leave a visible error. Success opens status/recovery so the public
     * package can be exported while confirmation is pending.
     */
    private void fundPlan() {
        if (!walletReady || funding) return;
        if (planId == null) { showError("Save a plan before funding it."); return; }
        funding = true;
        String id = planId, feeRate = input(R.id.feeInput);
        service.submit(() -> inheritance.prepareFunding(id, feeRate)).whenComplete((request, error) -> runOnUiThread(() -> {
            if (isDestroyed()) return;
            if (error != null) { funding = false; showError(rootMessage(error)); return; }
            work(() -> inheritance.getPlan(id), plan -> {
                String review = "Bitcoin " + WalletPolicy.NETWORK_LABEL + "\nBeneficiary: " + plan.getBeneficiary().getName() + "\nBeneficiary address: " + plan.getBeneficiary().getAddress()
                        + "\nAmount: " + plan.getAmount().toPlainString() + " BTC\nFee: " + request.tx.getFee().toPlainString()
                        + " BTC\nTotal: " + plan.getAmount().add(request.tx.getFee()).toPlainString() + " BTC\nUnlock: "
                        + DateFormat.getDateTimeInstance().format(new Date(plan.getUnlockTime() * 1000))
                        + "\n\nExport the recovery package after funding. The owner may cancel; the beneficiary may claim after the timelock.";
                AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.fund_plan).setMessage(review)
                        .setNegativeButton(R.string.cancel, (d, which) -> funding = false)
                        .setPositiveButton(R.string.confirm_send, (d, which) -> authenticate(() -> {
                            service.submit(() -> inheritance.fundPlan(id, request)).whenComplete((tx, failure) -> runOnUiThread(() -> {
                                funding = false;
                                if (failure != null) { showError(rootMessage(failure)); return; }
                                service.submit(() -> service.broadcast(tx)).thenCompose(future -> future).whenComplete((relayed, problem) -> runOnUiThread(() ->
                                        showMessage(problem == null ? "Funding relayed. Export the plan and wait for confirmation." : "Funding saved as pending. Retry the same transaction from History.")));
                                openRecovery(id, false);
                            }));
                        })).create();
                dialog.setOnCancelListener(d -> funding = false); dialog.show();
            });
        }));
    }
    /**
     * I receive the selected plan ID and a boolean describing the requested cancellation
     * navigation. I pass the wallet filename and plan ID in an Android Intent to
     * BeneficiaryRecoveryActivity. No signing data is included, and the recovery screen still
     * requires verified eligibility and authenticated submission.
     * @param id String supplied by the calling workflow as described above
     * @param cancel boolean supplied by the calling workflow as described above
     */
    private void openRecovery(String id, boolean cancel) {
        if (!walletReady) return;
        startActivity(new Intent(this, BeneficiaryRecoveryActivity.class).putExtra("wallet_file", service.requireWallet().getFileName())
                .putExtra("plan_id", id).putExtra("cancel_requested", cancel));
    }
    /**
     * I read the selected wallet's public data and update this screen's displayed address,
     * balance, plan status, or history. The conditions below avoid redundant or overlapping
     * work. Expensive repository/script operations use the worker, and their results return to
     * the main thread. Refresh does not authorize a payment or claim.
     */
    @Override protected void refresh() {
        if (loadingPlans) return;
        loadingPlans = true;
        service.submit(inheritance::getPlans).whenComplete((plans, error) -> runOnUiThread(() -> {
            loadingPlans = false;
            if (isDestroyed()) return;
            if (error != null) { showError(rootMessage(error)); return; }
            LinearLayout container = findViewById(R.id.plansContainer); container.removeAllViews();
            if (plans.isEmpty()) container.addView(WalletUi.empty(container, R.string.plan_empty));
            for (InheritancePlan plan : plans) {
                android.view.View row = WalletUi.plan(container, plan, () -> {
                    planId = plan.getId(); displayPlan(plan);
                    ((EditText) findViewById(R.id.ownerNameInput)).setText(plan.getOwner().getName());
                    ((EditText) findViewById(R.id.beneficiaryNameInput)).setText(plan.getBeneficiary().getName());
                    ((EditText) findViewById(R.id.beneficiaryKeyInput)).setText(plan.getBeneficiary().getPublicKey());
                    ((EditText) findViewById(R.id.beneficiaryAddressInput)).setText(plan.getBeneficiary().getAddress());
                    ((EditText) findViewById(R.id.inheritanceAmountInput)).setText(plan.getAmount().toPlainString());
                    ((EditText) findViewById(R.id.periodInput)).setText(String.format(java.util.Locale.ROOT, "%d", plan.getPeriodSeconds() / 3600));
                });
                container.addView(row);
                if (plan.getId().equals(planId)) displayPlan(plan);
            }
        }));
    }
    /**
     * I unwrap nested asynchronous exceptions to their underlying cause and return a user-
     * facing message, with a fallback when the cause has no text. The interface displays this
     * String and leaves the operation incomplete.
     * @param error Throwable supplied by the calling workflow as described above
     * @return the underlying exception message or a readable fallback
     */
    private String rootMessage(Throwable error) { while (error.getCause() != null) error = error.getCause(); return error.getMessage() == null ? "Inheritance operation failed." : error.getMessage(); }
    @Override protected void onAuthenticationCancelled() { funding = false; }
    /**
     * I receive the Bundle supplied by Android before recreation. I save only explicit
     * workflow identifiers and flags, such as a wallet filename, selected plan ID, or
     * submitted transaction ID. Mnemonic words and signing keys are not placed in this Bundle.
     * New Activity instances reconstruct their screen through the existing service.
     * @param state Bundle supplied by the calling workflow as described above
     */
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putString("plan_id", planId); }
}
