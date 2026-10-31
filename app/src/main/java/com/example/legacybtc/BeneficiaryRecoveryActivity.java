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

public class BeneficiaryRecoveryActivity extends SelectedWalletActivity {
    private InheritanceService inheritance;
    private String planId;
    private boolean refreshing, reviewing, refreshingNetwork, skipResumeSync;
    private String exportPlanId, exportWallet;
    private ActivityResultLauncher<String> exportDocument;
    private ActivityResultLauncher<String[]> importDocument;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_recovery);
        inheritance = new InheritanceService(this);
        planId = state == null ? getIntent().getStringExtra("plan_id") : state.getString("plan_id");
        exportPlanId=state==null?null:state.getString("export_id");
        exportWallet=state==null?null:state.getString("export_wallet");
        exportDocument = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
            if (uri == null || exportPlanId == null) return;
            String id = exportPlanId, wallet=exportWallet;
            work(() -> {
                if(!service.requireWallet().getFileName().equals(wallet))throw new IllegalStateException("Wallet changed during export. Return to the original wallet and retry.");
                byte[] data = inheritance.exportPlan(id).getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = getContentResolver().openOutputStream(uri,"wt")) {
                    if (output == null) throw new IllegalStateException("Could not open the selected document.");
                    output.write(data);output.flush();
                }
                ByteArrayOutputStream check=new ByteArrayOutputStream();
                try(InputStream input=getContentResolver().openInputStream(uri)){
                    if(input==null)throw new IllegalStateException("Written recovery file could not be reopened for verification. Keep the plan and retry export.");
                    byte[] buffer=new byte[4096];int count;while((count=input.read(buffer))!=-1){if(check.size()+count>RecoveryDocument.MAX_BYTES)throw new IllegalStateException("Written recovery file exceeds its expected size.");check.write(buffer,0,count);}
                }
                if(!java.util.Arrays.equals(data,check.toByteArray()))throw new IllegalStateException("Written recovery file differs from the validated package. Keep the plan and retry export.");
                RecoveryDocument.parse(new String(check.toByteArray(),StandardCharsets.UTF_8));
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
            if(planId!=null){String id=planId;work(()->{inheritance.exportPlan(id);return service.requireWallet().getFileName();},wallet->{exportPlanId=id;exportWallet=wallet;exportDocument.launch("LegacyBTC-"+WalletPolicy.NETWORK_LABEL+"-"+id+".json");});}
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
        if (getIntent().getBooleanExtra("cancel_requested",false))
            showMessage("Owner cancellation selected. Check the plan, then choose Cancel funded plan to review. Nothing has been cancelled.");
        loadWallet();
    }
    @Override protected void onWalletReady(){syncOnReturn();}
    @Override protected void onResume(){super.onResume();if(skipResumeSync){skipResumeSync=false;return;}syncOnReturn();}
    @Override protected void onPause(){if(((LegacyBtcApplication)getApplication()).isAuthenticating())skipResumeSync=true;super.onPause();}
    private void syncOnReturn(){
        if(!walletReady || reviewing || refreshingNetwork || ((LegacyBtcApplication)getApplication()).isAuthenticating())return;
        refreshingNetwork=true;
        service.submit(()->{service.reconnect();return true;}).whenComplete((ignored,error)->runOnUiThread(()->{
            refreshingNetwork=false;if(isDestroyed() || isFinishing())return;
            if(error!=null)showError(rootMessage(error));refresh();
        }));
    }
    @Override protected void refresh() {
        if (refreshing || !walletReady) return;
        refreshing = true;
        String feeText=((EditText)findViewById(R.id.feeInput)).getText().toString();
        service.submit(() -> {
            java.util.ArrayList<InheritancePlan> plans = inheritance.getPlans();
            InheritancePlan selected = null;
            for (InheritancePlan plan : plans) if (plan.getId().equals(planId)) selected = plan;
            if (selected == null && !plans.isEmpty()) selected = plans.get(plans.size() - 1);
            InheritanceState view=selected==null?null:inheritance.state(selected);
            String feeProblem="";
            if(selected!=null && !selected.hasSpend() && (view.claim || view.cancel))try {
                InheritanceSpender.prepare(selected,selected.fundingTransaction().getOutput(selected.getOutputIndex()),service.requireWallet().getWallet().currentReceiveAddress(),WalletPolicy.parseFeeRate(feeText),view.cancel);
            }catch(Exception invalid){feeProblem=invalid.getMessage();}
            return new StatusView(plans,selected,view,feeProblem);
        }).whenComplete((result, error) -> runOnUiThread(() -> {
            refreshing = false;
            if (isDestroyed() || isFinishing()) return;
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
                InheritanceState view=result.view;
                boolean retry=plan.hasSpend() && result.view.confirmed==InheritanceState.Truth.YES && result.view.unspent==InheritanceState.Truth.YES;
                boolean available = result.feeProblem.isEmpty() && result.view.claim || retry && !plan.isSpendOwner() && result.view.beneficiaryKey && result.view.mature==InheritanceState.Truth.YES;
                ((TextView) findViewById(R.id.planStatusText)).setText(getString(R.string.role_status_value, view.ownerKey?"Owner":view.beneficiaryKey?"Beneficiary":"No signing key", WalletUi.status(view.status)));
                findViewById(R.id.recoveryDetailsSection).setVisibility(android.view.View.VISIBLE);
                findViewById(R.id.recoveryBeneficiarySection).setVisibility(android.view.View.VISIBLE);
                ((TextView) findViewById(R.id.recoveryAmountText)).setText(getString(R.string.btc_amount, plan.getAmount().toPlainString()));
                ((TextView) findViewById(R.id.recoveryPeriodText)).setText("Delay: "+plan.getPeriodSeconds()/60+" minutes; consensus maturity uses MTP.");
                ((TextView) findViewById(R.id.recoveryDeadlineText)).setText(getString(R.string.recovery_deadline_value, DateFormat.getDateTimeInstance().format(new Date(plan.getUnlockTime() * 1000))));
                ((TextView) findViewById(R.id.recoveryEligibilityText)).setText((result.feeProblem.isEmpty()?view.conditions():view.conditions().replace("Ready to claim: YES","Ready to claim: NO"))+"\nFunding transaction: "+view.fundingPhase(plan)+(plan.hasSpend()?"\nSaved spending TXID: "+plan.spendTransaction().getTxId():""));
                ((TextView) findViewById(R.id.recoveryBeneficiaryText)).setText(plan.getBeneficiary().getName());
                ((TextView) findViewById(R.id.recoveryAddressText)).setText(plan.getBeneficiary().getAddress());
                ((TextView) findViewById(R.id.recoveryPublicKeyText)).setText(plan.getBeneficiary().getPublicKey());
                ((TextView) findViewById(R.id.recoveryFundingText)).setText(plan.isFunded() ? plan.fundingTransaction().getTxId().toString() : getString(R.string.not_funded));
                findViewById(R.id.claimButton).setEnabled(available && !reviewing);
                ((TextView) findViewById(R.id.recoveryStateText)).setText(available ? R.string.recovery_available_heading : R.string.recovery_locked_heading);
                ((TextView) findViewById(R.id.recoveryConditionText)).setText(view.reason+(result.feeProblem.isEmpty()?"":"\nAction unavailable: "+result.feeProblem));
                findViewById(R.id.cancelFundedButton).setEnabled(!reviewing && (view.cancel && result.feeProblem.isEmpty() || retry && plan.isSpendOwner() && view.ownerKey));
                ((Button)findViewById(R.id.claimButton)).setText(retry && !plan.isSpendOwner()?"Retry saved claim":getString(R.string.claim_recover));
                ((Button)findViewById(R.id.cancelFundedButton)).setText(retry && plan.isSpendOwner()?"Retry saved owner cancellation":"Cancel funded plan — Owner");
                findViewById(R.id.exportPlanButton).setEnabled(plan.isFunded());
            } catch (Exception e) { showError(e.getMessage()); }
        }));
    }
    private void reviewRecovery(boolean cancel) {
        if (!walletReady || planId == null || reviewing) return;
        reviewing = true;
        String id = planId, fee = ((EditText) findViewById(R.id.feeInput)).getText().toString();
        service.submit(() -> inheritance.prepareRecovery(id, fee, cancel)).whenComplete((tx, error) -> runOnUiThread(() -> {
            if (isDestroyed() || isFinishing()) return;
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
                                    if (isDestroyed() || isFinishing()) return;
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
        final java.util.ArrayList<InheritancePlan> plans;final InheritancePlan plan;final InheritanceState view;final String feeProblem;
        StatusView(java.util.ArrayList<InheritancePlan> plans,InheritancePlan plan,InheritanceState view,String feeProblem){this.plans=plans;this.plan=plan;this.view=view;this.feeProblem=feeProblem;}
    }
    private String rootMessage(Throwable error) { while (error.getCause() != null) error = error.getCause(); return error.getMessage() == null ? "Could not verify the inheritance plan." : error.getMessage(); }
    @Override protected void onOperationFailed() { reviewing = false; }
    @Override protected void onAuthenticationCancelled() { reviewing = false; }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putString("plan_id", planId);state.putString("export_id",exportPlanId);state.putString("export_wallet",exportWallet); }
}
