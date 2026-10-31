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

public class InheritanceSetupActivity extends SelectedWalletActivity {
    private InheritanceService inheritance;
    private String planId;
    private Beneficiary beneficiary;
    private Long period;
    private boolean loadingPlans, funding, saving;
    private AlertDialog draftConfirmation;
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
        findViewById(R.id.newPlanButton).setOnClickListener(view -> {
            if (saving || funding) return;
            planId = null;
            for (int field : new int[]{R.id.beneficiaryNameInput,R.id.beneficiaryKeyInput,R.id.beneficiaryAddressInput,R.id.inheritanceAmountInput,R.id.periodInput})
                ((EditText)findViewById(field)).setText("");
            ((TextView)findViewById(R.id.planSummaryText)).setText(R.string.no_plan);
            findViewById(R.id.savePlanButton).setEnabled(true);
        });
        findViewById(R.id.updatePlanButton).setOnClickListener(view -> savePlan(true));
        findViewById(R.id.fundPlanButton).setOnClickListener(view -> fundPlan());
        findViewById(R.id.cancelPlanButton).setOnClickListener(view -> {
            if (planId == null) { showError("Select a saved plan first."); return; }
            work(() -> inheritance.getPlan(planId), plan -> {
                if (plan.isFunded()) openRecovery(plan.getId(), true);
                else draftConfirmation = new AlertDialog.Builder(this).setTitle(R.string.cancel_plan).setMessage(R.string.cancel_draft_warning)
                        .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.cancel_plan, (d, which) ->
                                work(() -> { inheritance.cancelDraft(plan.getId()); return true; }, ignored -> { planId = null; refresh(); })).show();
            });
        });
        findViewById(R.id.recoveryButton).setOnClickListener(view -> openRecovery(planId, false));
        findViewById(R.id.backButton).setOnClickListener(view -> finish()); loadWallet();
    }
    private String input(int id) { return ((EditText) findViewById(id)).getText().toString(); }
    private Beneficiary readBeneficiary() { return inheritance.addBeneficiary(input(R.id.beneficiaryNameInput), input(R.id.beneficiaryKeyInput), input(R.id.beneficiaryAddressInput)); }
    private void savePlan(boolean update) {
        if (!walletReady || saving) return;
        if (!update && planId != null) { showError("Use Update Plan for the selected draft, or New plan for another beneficiary."); return; }
        try {
            beneficiary = readBeneficiary(); period = inheritance.setInheritancePeriod(input(R.id.periodInput));
            String name = input(R.id.ownerNameInput), amount = input(R.id.inheritanceAmountInput);
            if (update && planId == null) throw new IllegalArgumentException("Select a plan to update.");
            Beneficiary validated = beneficiary; long seconds = period;
            String selectedId = planId;
            saving = true;
            service.submit(() -> update ? inheritance.updatePlan(selectedId, validated, amount, seconds)
                    : inheritance.createInheritancePlan(name, validated, amount, seconds)).whenComplete((plan,error) -> runOnUiThread(() -> {
                saving = false;
                if (isDestroyed() || isFinishing()) return;
                if (error != null) { showError(rootMessage(error)); return; }
                planId = plan.getId(); displayPlan(plan); refresh();
            }));
        } catch (Exception error) { showError(error.getMessage()); }
    }
    private void displayPlan(InheritancePlan plan) {
        ((Button)findViewById(R.id.fundPlanButton)).setText(plan.isFunded()?"Retry exact saved funding":getString(R.string.fund_plan));
        findViewById(R.id.savePlanButton).setEnabled(false);
        ((TextView) findViewById(R.id.planSummaryText)).setText(plan.getBeneficiary().getName()+" � "+plan.getAmount().toPlainString()+" BTC\nDelay: "+plan.getPeriodSeconds()/60+" minutes\nWall-clock deadline: "+DateFormat.getDateTimeInstance().format(new Date(plan.getUnlockTime()*1000))+"\n"+(plan.isFunded()?(plan.isFundingAccepted()?"Funding accepted by Electrum; verify confirmations in Status.":"Funding signed locally; relay/confirmation not established."):plan.isCancelled()?"Draft cancelled":"Unfunded draft"));
    }
    private void fundPlan() {
        if (!walletReady || funding) return;
        if (planId == null) { showError("Save a plan before funding it."); return; }
        funding = true;
        String id = planId, feeRate = input(R.id.feeInput);
        service.submit(()->inheritance.getPlan(id)).thenCompose(plan->{
            if(plan.isFunded()){runOnUiThread(()->{funding=false;retryFunding(id);});return java.util.concurrent.CompletableFuture.<org.bitcoinj.wallet.SendRequest>completedFuture(null);}
            return service.submit(() -> inheritance.prepareFunding(id,feeRate));
        }).whenComplete((request, error) -> runOnUiThread(() -> {
            if (isDestroyed() || isFinishing()) return;
            if (error != null) { funding = false; showError(rootMessage(error)); return; }
            if(request==null)return;
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
                                if (isDestroyed() || isFinishing()) return;
                                if (failure != null) { showError(rootMessage(failure)); return; }
                                service.submit(() -> service.broadcast(tx)).thenCompose(future -> future).thenCompose(sent -> service.submit(() -> { inheritance.recordFundingAcceptance(id,sent); return sent; })).whenComplete((relayed, problem) -> runOnUiThread(() ->
                                        {if(problem!=null)service.submit(()->{inheritance.recordFundingRelayFailure(id,problem);return true;});showMessage(problem == null ? "Funding relayed. Export the plan and wait for confirmation." : "Relay not confirmed. Saved funding retained; retry exact transaction or check verified status.");}));
                                openRecovery(id, false);
                            }));
                        })).create();
                dialog.setOnCancelListener(d -> funding = false); dialog.show();
            });
        }));
    }
    private void retryFunding(String id) {
        new AlertDialog.Builder(this).setTitle("Retry saved funding").setMessage("Only the exact saved transaction will be restored and relayed. No replacement or second funding is created.")
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.confirm_send,(d,w)->authenticate(()->work(()->inheritance.restoreFunding(id),tx->{
                service.submit(()->service.broadcast(tx)).thenCompose(f->f).whenComplete((sent,error)->{
                    service.submit(()->{if(error==null)inheritance.recordFundingAcceptance(id,sent);else inheritance.recordFundingRelayFailure(id,error);return true;});
                    runOnUiThread(()->showMessage(error==null?"Funding accepted; await verified confirmation.":"Relay uncertain/rejected; inspect status. Exact transaction preserved."));
                });
            }))).show();
    }
    private void openRecovery(String id, boolean cancel) {
        if (!walletReady) return;
        startActivity(new Intent(this, BeneficiaryRecoveryActivity.class).putExtra("wallet_file", service.requireWallet().getFileName())
                .putExtra("plan_id", id).putExtra("cancel_requested", cancel));
    }
    @Override protected void refresh() {
        if (loadingPlans) return;
        loadingPlans = true;
        service.submit(inheritance::getPlans).whenComplete((plans, error) -> runOnUiThread(() -> {
            loadingPlans = false;
            if (isDestroyed() || isFinishing()) return;
            if (error != null) { showError(rootMessage(error)); return; }
            LinearLayout container = findViewById(R.id.plansContainer); container.removeAllViews();
            if (plans.isEmpty()) container.addView(WalletUi.empty(container, R.string.plan_empty));
            for (InheritancePlan plan : plans) {
                android.view.View row = WalletUi.plan(container, plan, () -> {
                    planId = plan.getId(); displayPlan(plan);
                    ((EditText) findViewById(R.id.ownerNameInput)).setText(plan.getName());
                    ((EditText) findViewById(R.id.beneficiaryNameInput)).setText(plan.getBeneficiary().getName());
                    ((EditText) findViewById(R.id.beneficiaryKeyInput)).setText(plan.getBeneficiary().getPublicKey());
                    ((EditText) findViewById(R.id.beneficiaryAddressInput)).setText(plan.getBeneficiary().getAddress());
                    ((EditText) findViewById(R.id.inheritanceAmountInput)).setText(plan.getAmount().toPlainString());
                    ((EditText) findViewById(R.id.periodInput)).setText((plan.getPeriodSeconds()/60)+" minutes");
                });
                container.addView(row);
                if (plan.getId().equals(planId)) displayPlan(plan);
            }
        }));
    }
    private String rootMessage(Throwable error) { while (error.getCause() != null) error = error.getCause(); return error.getMessage() == null ? "Inheritance operation failed." : error.getMessage(); }
    @Override protected void onOperationFailed() { funding = false; }
    @Override protected void onAuthenticationCancelled() { funding = false; }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putString("plan_id", planId); }
}
