package com.example.legacybtc;

import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.crypto.ECKey;

/** Independent ownership and verified-chain conditions. No phone-time eligibility. */
public final class InheritanceState {
    public enum Truth { YES, NO, UNKNOWN }
    public final boolean beneficiaryKey, ownerKey, claim, cancel;
    public final Truth confirmed, unspent, mature;
    public final InheritancePlan.Status status;
    public final String reason;
    public String fundingPhase(InheritancePlan p){
        if(!p.isFunded())return "DRAFT";
        if(status==InheritancePlan.Status.CONFLICTING)return "CONFLICTING";
        if(status==InheritancePlan.Status.CANCELLED || status==InheritancePlan.Status.SPENT)return "SPENT";
        if(confirmed==Truth.YES)return "CONFIRMED";
        if(confirmed==Truth.NO)return "UNCONFIRMED";
        return p.getFundingRelay()+"; blockchain verification unavailable";
    }
    private InheritanceState(boolean b,boolean o,Truth c,Truth u,Truth m,boolean claim,boolean cancel,InheritancePlan.Status status,String reason) {
        beneficiaryKey=b;ownerKey=o;confirmed=c;unspent=u;mature=m;this.claim=claim;this.cancel=cancel;this.status=status;this.reason=reason;
    }
    public static boolean controls(Wallet wallet,String pub) {
        ECKey key=wallet.findKeyFromPubKey(Hex.decode(pub));return key!=null && key.hasPrivKey();
    }
    static InheritanceState evaluate(InheritancePlan p,Wallet wallet,InheritanceOutputVerifier.Evidence e,boolean pending,String unavailable) {
        boolean b=controls(wallet,p.getBeneficiary().getPublicKey()),o=controls(wallet,p.getOwner().getPublicKey());
        if(!p.isFunded())return new InheritanceState(b,o,Truth.NO,Truth.UNKNOWN,Truth.UNKNOWN,false,false,p.isCancelled()?InheritancePlan.Status.CANCELLED:InheritancePlan.Status.DRAFT,"Draft: no funding transaction.");
        org.bitcoinj.core.Transaction local=wallet.getTransaction(p.fundingTransaction().getTxId());
        if(local!=null && (e==null || e.block==null) && (local.getConfidence().getConfidenceType()==org.bitcoinj.core.TransactionConfidence.ConfidenceType.DEAD || local.getConfidence().getConfidenceType()==org.bitcoinj.core.TransactionConfidence.ConfidenceType.IN_CONFLICT))
            return new InheritanceState(b,o,Truth.UNKNOWN,Truth.UNKNOWN,Truth.UNKNOWN,false,false,InheritancePlan.Status.CONFLICTING,"Wallet reports conflicting funding. Verify its winning spend independently; no branch is enabled.");
        if(e==null)return new InheritanceState(b,o,Truth.UNKNOWN,Truth.UNKNOWN,Truth.UNKNOWN,false,false,unavailable.contains("Conflicting inheritance")?InheritancePlan.Status.CONFLICTING:InheritancePlan.Status.UNVERIFIED,unavailable);
        boolean confirmed=e.block!=null, immature=confirmed && e.funding.isCoinBase() && e.tip-e.block.getHeight()+1<100;
        boolean mature=e.median>p.getUnlockTime(); // BIP113 strict finality.
        boolean busy=pending || p.hasSpend() || e.spender!=null && !e.spendConfirmed;
        InheritancePlan.Status status;String why;
        if(e.spender!=null && e.spendConfirmed) {
            boolean owner=false;
            for(org.bitcoinj.core.TransactionInput input:e.spender.getInputs())
                if(input.getOutpoint().getHash().equals(e.funding.getTxId()) && input.getOutpoint().getIndex()==p.getOutputIndex()) {
                    java.util.List<org.bitcoinj.script.ScriptChunk> chunks=input.getScriptSig().getChunks();
                    owner=chunks.size()==3 && chunks.get(1).opcode==org.bitcoinj.script.ScriptOpCodes.OP_1;
                }
            status=owner?InheritancePlan.Status.CANCELLED:InheritancePlan.Status.SPENT;why="Confirmed "+(owner?"owner cancellation":"beneficiary claim")+": "+e.spender.getTxId();
        } else if(busy){status=InheritancePlan.Status.SPEND_PENDING;why="A spend is saved or pending. Retry only its exact TXID; no second payment is permitted.";}
        else if(!confirmed || immature){status=InheritancePlan.Status.FUNDING_PENDING;why=immature?"Coinbase funding needs 100 confirmations.":"Funding observed but unconfirmed.";}
        else if(!e.unspent){status=InheritancePlan.Status.UNVERIFIED;why="Output availability inconsistent; fresh verification required.";}
        else {status=mature?InheritancePlan.Status.AVAILABLE:InheritancePlan.Status.TIMELOCKED;why=mature?"Blockchain timelock satisfied.":"Blockchain MTP must exceed deadline. Owner recovery does not require maturity.";}
        boolean free=confirmed && !immature && e.unspent && !busy && e.spender==null;
        if(!b)why+=" Beneficiary private key does not match this wallet.";
        return new InheritanceState(b,o,confirmed?Truth.YES:Truth.NO,e.unspent?Truth.YES:Truth.NO,mature?Truth.YES:Truth.NO,free && mature && b,free && o,status,
            "Verified MTP: "+java.time.Instant.ofEpochSecond(e.median)+"; deadline: "+java.time.Instant.ofEpochSecond(p.getUnlockTime())+". "+why);
    }
    public String conditions() {
        return "Beneficiary key matches this wallet: "+(beneficiaryKey?"YES":"NO")+"\nOwner key matches this wallet: "+(ownerKey?"YES":"NO")+
            "\nFunding confirmed: "+confirmed+"\nInheritance output unspent: "+unspent+"\nTimelock satisfied: "+mature+"\nReady to claim: "+(claim?"YES":"NO");
    }
}
