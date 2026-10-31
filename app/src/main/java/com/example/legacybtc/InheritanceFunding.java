package com.example.legacybtc;
import org.bitcoinj.core.*;
import org.bitcoinj.wallet.SendRequest;
import java.time.Instant;
/** Funding coordinator reuses the ordinary wallet review/sign/persist APIs. */
final class InheritanceFunding {
 private final BitcoinService bitcoin;private final InheritanceRepository repository;
 InheritanceFunding(BitcoinService b,InheritanceRepository r){bitcoin=b;repository=r;}
 private String file(){return bitcoin.requireWallet().getFileName();}
    public SendRequest prepareFunding(String id, String feeRate) throws Exception {
        bitcoin.requireReady();
        InheritancePlan plan = repository.find(file(),id);
        if (plan.isFunded() || plan.isCancelled()) throw new IllegalStateException("Choose an unfunded active draft.");
        if (!plan.userFor(bitcoin.requireWallet().getWallet()).canManage(plan)) throw new IllegalStateException("Only the owner can fund this plan.");
        if (Instant.now().getEpochSecond() >= plan.getUnlockTime()) throw new IllegalStateException("The draft deadline passed. Update the inheritance period first.");
        bitcoin.requireWallet().getWallet().addWatchedAddress(InheritanceScript.address(plan), Instant.ofEpochSecond(plan.getCreatedAt()));
        return bitcoin.preparePayment(InheritanceScript.address(plan).toString(), plan.getAmount().toPlainString(), feeRate);
    }
    public Transaction fundPlan(String id, SendRequest reviewed) throws Exception {
        InheritancePlan plan = repository.find(file(),id);
        if (plan.isFunded() || plan.isCancelled() || !plan.userFor(bitcoin.requireWallet().getWallet()).canManage(plan))
            throw new IllegalStateException("Plan changed. Review funding again.");
        // Authentication may take time. Do not fund a draft whose deadline passed during review.
        if (Instant.now().getEpochSecond() >= plan.getUnlockTime())
            throw new IllegalStateException("The draft deadline passed. Update the inheritance period and review again.");
        int index = -1;
        for (int i = 0; i < reviewed.tx.getOutputs().size(); i++) {
            TransactionOutput output = reviewed.tx.getOutput(i);
            if (output.getValue().equals(plan.getAmount()) && java.util.Arrays.equals(output.getScriptPubKey().getProgram(), InheritanceScript.outputScript(plan).getProgram())) { index = i; break; }
        }
        if (index == -1) throw new IllegalStateException("Reviewed transaction no longer matches the plan.");
        Transaction tx = bitcoin.signReviewed(reviewed);
        tx.getConfidence().setSource(TransactionConfidence.Source.SELF);
        tx.setMemo("LegacyBTC inheritance funding");
        plan.attachFunding(tx, index);
        repository.save(file(), plan); // Save recovery data before committing or broadcasting the funding.
        bitcoin.requireWallet().getWallet().commitTx(tx);
        bitcoin.persist();
        return tx;
    }
}
