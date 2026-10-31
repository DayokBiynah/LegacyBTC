package com.example.legacybtc;
import android.content.Context;
import org.bitcoinj.core.Transaction;

/** Blocks ordinary spending if a local signed journal survived but its wallet commit did not. Never relays. */
final class InheritanceJournal {
    static void checkBeforeSpending(Context context,BitcoinWallet selected)throws Exception {
        for(InheritancePlan p:new InheritanceRepository(context).getPlans(selected.getFileName())) {
            Transaction funding=p.isFunded()?p.fundingTransaction():null;
            if(funding!=null && InheritanceState.controls(selected.getWallet(),p.getOwner().getPublicKey()) && selected.getWallet().getTransaction(funding.getTxId())==null) {
                // Imported externally funded outputs are not ordinary wallet input reservations.
                boolean ownInput=false;
                for(org.bitcoinj.core.TransactionInput input:funding.getInputs()){
                    Transaction parent=selected.getWallet().getTransaction(input.getOutpoint().getHash());
                    if(parent!=null && input.getOutpoint().getIndex()<parent.getOutputs().size() && parent.getOutput(input.getOutpoint().getIndex()).isMine(selected.getWallet()))ownInput=true;
                }
                if(ownInput)throw new IllegalStateException("Signed inheritance funding "+funding.getTxId()+" is journaled outside wallet history. Preserve its exact transaction and resolve persistence before spending.");
            }
            if(p.hasSpend() && selected.getWallet().getTransaction(p.spendTransaction().getTxId())==null)
                throw new IllegalStateException("Saved inheritance spend "+p.spendTransaction().getTxId()+" needs recovery from the inheritance screen before ordinary spending.");
        }
    }
}
