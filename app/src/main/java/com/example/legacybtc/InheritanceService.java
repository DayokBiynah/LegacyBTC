package com.example.legacybtc;

import android.content.Context;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import java.util.ArrayList;
import java.time.Instant;

public final class InheritanceService {
    private final BitcoinService bitcoin;
    private final InheritanceRepository repository;
    public InheritanceService(Context context) {
        this(BitcoinService.get(context), new InheritanceRepository(context));
    }
    InheritanceService(BitcoinService bitcoin, InheritanceRepository repository) { this.bitcoin = bitcoin; this.repository = repository; }
    private String file() { return bitcoin.requireWallet().getFileName(); }
    public ArrayList<InheritancePlan> getPlans() throws Exception { return repository.getPlans(file()); }
    public InheritancePlan getPlan(String id) throws Exception { return repository.find(file(), id); }
    public Beneficiary addBeneficiary(String name,String key,String address) {
        ECKey publicKey=User.parsePublicKey(key);
        return new Beneficiary(name.trim().isEmpty()?"Beneficiary":name,key,address.trim().isEmpty()?publicKey.toAddress(ScriptType.P2WPKH,WalletPolicy.NETWORK).toString():address);
    }
    public long setInheritancePeriod(String hours) {
        return InheritanceTime.seconds(hours,Instant.now().getEpochSecond());
    }
    public InheritancePlan createInheritancePlan(String ownerName, Beneficiary beneficiary, String amount, long period) throws Exception {
        ECKey key = bitcoin.requireWallet().getWallet().currentReceiveKey();
        Owner owner = new Owner(ownerName.trim().isEmpty()?"Owner":ownerName, Hex.encode(key.getPubKey()), key.toAddress(ScriptType.P2WPKH, WalletPolicy.NETWORK).toString());
        InheritancePlan plan = new InheritancePlan(owner, beneficiary, WalletPolicy.parseAmount(amount), period, Instant.now().getEpochSecond());
        plan.setName(ownerName);
        repository.save(file(), plan);
        return plan;
    }
    public InheritancePlan updatePlan(String id, Beneficiary beneficiary, String amount, long period) throws Exception {
        InheritancePlan old = getPlan(id);
        User role = old.userFor(bitcoin.requireWallet().getWallet());
        if (!role.canManage(old)) throw new IllegalStateException("Only the owner can update a plan.");
        if (old.isFunded()) throw new IllegalStateException("A funded Bitcoin script is immutable. Cancel it on-chain, then save and fund a new plan.");
        if (old.isCancelled()) throw new IllegalStateException("Save a new plan instead of updating a cancelled plan.");
        InheritancePlan updated = new InheritancePlan(old.getId(), old.getOwner(), beneficiary,
                WalletPolicy.parseAmount(amount), period, Instant.now().getEpochSecond());
        updated.setName(old.getName());
        repository.save(file(), updated);
        return updated;
    }
    public SendRequest prepareFunding(String id,String fee)throws Exception{return new InheritanceFunding(bitcoin,repository).prepareFunding(id,fee);}
    public Transaction fundPlan(String id,SendRequest reviewed)throws Exception{return new InheritanceFunding(bitcoin,repository).fundPlan(id,reviewed);}
    public InheritanceState state(InheritancePlan plan)throws Exception {
        Wallet wallet=bitcoin.requireWallet().getWallet();
        boolean pending=false;
        if(plan.isFunded())for(Transaction tx:wallet.getTransactions(true))
            if(tx.getConfidence().getConfidenceType()==TransactionConfidence.ConfidenceType.PENDING)
                for(org.bitcoinj.core.TransactionInput input:tx.getInputs())
                    if(input.getOutpoint().getHash().equals(plan.fundingTransaction().getTxId()) && input.getOutpoint().getIndex()==plan.getOutputIndex())pending=true;
        InheritanceOutputVerifier.Evidence evidence=bitcoin.getNetwork().isReady()?bitcoin.getNetwork().inheritanceEvidence(plan):null;
        String reason=!bitcoin.getNetwork().isReady()?bitcoin.getStatus():plan.isFunded()?bitcoin.getNetwork().inheritanceVerificationMessage(plan):"No funding.";
        return InheritanceState.evaluate(plan,wallet,evidence,pending,reason);
    }
    public InheritancePlan.Status checkInheritanceStatus(InheritancePlan plan)throws Exception {return state(plan).status;}
    public boolean canClaim(InheritancePlan plan,InheritancePlan.Status ignored) {try{return state(plan).claim;}catch(Exception e){return false;}}
    public boolean timelockSatisfied(InheritancePlan plan) {try{return state(plan).mature==InheritanceState.Truth.YES;}catch(Exception e){return false;}}
    public String statusReason(InheritancePlan plan)throws Exception {return state(plan).reason;}
    private Transaction verifiedFunding(InheritancePlan plan) throws Exception {
        InheritanceOutputVerifier.Evidence evidence=bitcoin.getNetwork().inheritanceEvidence(plan);
        if(evidence==null || evidence.block==null) throw new IllegalStateException("Wait for independent funding proof and UTXO verification.");
        Wallet wallet=bitcoin.requireWallet().getWallet();
        Transaction funding=wallet.getTransaction(evidence.funding.getTxId());
        if(funding==null) {
            funding=Transaction.read(java.nio.ByteBuffer.wrap(evidence.funding.serialize()));
            wallet.addWatchedAddress(InheritanceScript.address(plan),Instant.EPOCH);
            wallet.receiveFromBlock(funding,evidence.block,org.bitcoinj.core.AbstractBlockChain.NewBlockType.BEST_CHAIN,0);
            funding.getConfidence().setDepthInBlocks(evidence.tip-evidence.block.getHeight()+1);
            bitcoin.persist();
        }
        return funding;
    }
    public void recordFundingAcceptance(String id,Transaction tx) throws Exception {
        InheritancePlan plan=getPlan(id);
        if(!plan.fundingTransaction().getTxId().equals(tx.getTxId())) throw new IllegalArgumentException("Funding TXID changed.");
        plan.markFundingAccepted(); repository.save(file(),plan);
    }

    public void recordFundingRelayFailure(String id,Throwable error)throws Exception {
        WalletDiagnostics.failure("inheritance funding relay",error);
        InheritancePlan p=getPlan(id);boolean rejected=false;
        for(Throwable cause=error;cause!=null;cause=cause.getCause())if(cause.getMessage()!=null && cause.getMessage().startsWith("Electrum rejected blockchain.transaction.broadcast:"))rejected=true;
        p.relayUncertain(rejected);repository.save(file(),p);
    }
    public Transaction restoreFunding(String id)throws Exception {
        bitcoin.requireVerifiedState();InheritancePlan p=getPlan(id);Wallet wallet=bitcoin.requireWallet().getWallet();
        if(!InheritanceState.controls(wallet,p.getOwner().getPublicKey()))throw new IllegalStateException("Owner private key required.");
        Transaction tx=p.fundingTransaction(),saved=wallet.getTransaction(tx.getTxId());
        if(saved==null){
            for(int i=0;i<tx.getInputs().size();i++){
                org.bitcoinj.core.TransactionInput input=tx.getInput(i);Transaction parent=wallet.getTransaction(input.getOutpoint().getHash());
                if(parent==null || input.getOutpoint().getIndex()>=parent.getOutputs().size())throw new IllegalStateException("Funding input parent unavailable; preserve journal.");
                TransactionOutput output=parent.getOutput(input.getOutpoint().getIndex());
                if(!output.isMine(wallet) || !output.isAvailableForSpending())throw new IllegalStateException("Funding input ownership or availability changed; do not create a replacement.");
                tx.replaceInput(i,new org.bitcoinj.core.TransactionInput(tx,input.getScriptBytes(),output.getOutPointFor(),input.getSequenceNumber(),output.getValue(),input.getWitness()));tx.getInput(i).verify();
            }
            Transaction.verify(WalletPolicy.NETWORK,tx);tx.getConfidence().setSource(TransactionConfidence.Source.SELF);wallet.commitTx(tx);
        }else{
            if(!java.util.Arrays.equals(saved.serialize(),tx.serialize()))throw new IllegalStateException("Funding bytes changed.");
            if(saved.getConfidence().getConfidenceType()!=TransactionConfidence.ConfidenceType.PENDING)throw new IllegalStateException("Funding is confirmed or conflicting. Check verified status instead of retrying.");
            saved.getConfidence().setSource(TransactionConfidence.Source.SELF);
        }
        bitcoin.persist();return saved==null?tx:saved;
    }
    public Transaction prepareRecovery(String id,String feeRate,boolean cancel)throws Exception {
        bitcoin.requireVerifiedState();InheritancePlan plan=getPlan(id);Wallet wallet=bitcoin.requireWallet().getWallet();
        InheritanceState view=state(plan);
        boolean owns=cancel?view.ownerKey:view.beneficiaryKey;
        if(!owns)throw new IllegalStateException(cancel?"Owner private key not controlled by this wallet.":"Beneficiary private key not controlled by this wallet.");
        Transaction review;
        if(plan.hasSpend()){
            if(plan.isSpendOwner()!=cancel)throw new IllegalStateException("A different spending branch is already journaled. Retry its saved TXID.");
            InheritanceOutputVerifier.Evidence e=bitcoin.getNetwork().inheritanceEvidence(plan);
            if(e==null || !e.unspent || e.block==null || e.spender!=null || !cancel && e.median<=plan.getUnlockTime())throw new IllegalStateException(view.reason);
            verifiedFunding(plan);review=plan.spendTransaction();
        } else {
            if(!(cancel?view.cancel:view.claim))throw new IllegalStateException(view.reason);
            Transaction funding=verifiedFunding(plan);
            review=InheritanceSpender.prepare(plan,funding.getOutput(plan.getOutputIndex()),wallet.currentReceiveAddress(),WalletPolicy.parseFeeRate(feeRate),cancel);
        }
        bitcoin.persist(); // Persist any newly issued destination key before presenting or journaling a payment.
        bitcoin.rememberRecovery(review);return review;
    }
    public Transaction recoverInheritance(String id,Transaction reviewed,boolean cancel)throws Exception {
        bitcoin.validateRecovery(reviewed);bitcoin.requireVerifiedState();
        InheritancePlan plan=getPlan(id);Wallet wallet=bitcoin.requireWallet().getWallet();InheritanceState view=state(plan);
        if(!(cancel?view.ownerKey:view.beneficiaryKey))throw new IllegalStateException("Required signing key unavailable.");
        Transaction signed;
        if(plan.hasSpend()) {
            if(plan.isSpendOwner()!=cancel || !java.util.Arrays.equals(reviewed.serialize(),plan.spendTransaction().serialize()))throw new IllegalStateException("Retry only the exact journaled transaction.");
            signed=plan.spendTransaction();
        } else {
            if(!(cancel?view.cancel:view.claim))throw new IllegalStateException(view.reason);
            ECKey key=wallet.findKeyFromPubKey(Hex.decode(cancel?plan.getOwner().getPublicKey():plan.getBeneficiary().getPublicKey()));
            if(!reviewed.getOutput(0).isMine(wallet))throw new IllegalStateException("Recovery destination is not controlled by this wallet.");
            signed=InheritanceSpender.sign(plan,reviewed,key,cancel);
            plan.attachSpend(signed,cancel);repository.save(file(),plan); // Durable BEFORE wallet commit or relay.
        }
        InheritanceSpender.validate(plan,signed,cancel);
        signed.setMemo(cancel?"LegacyBTC inheritance cancellation":"LegacyBTC inheritance recovery");
        signed.getConfidence().setSource(TransactionConfidence.Source.SELF);
        if(wallet.getTransaction(signed.getTxId())==null)wallet.commitTx(signed);
        bitcoin.persist();return signed;
    }
    public void cancelDraft(String id) throws Exception {
        InheritancePlan plan = getPlan(id);
        if (!plan.userFor(bitcoin.requireWallet().getWallet()).canManage(plan)) throw new IllegalStateException("Only the owner can cancel this draft.");
        plan.cancelDraft(); repository.save(file(), plan);
    }
    public String exportPlan(String id) throws Exception { return RecoveryDocument.export(getPlan(id)); }
    public InheritancePlan importPlan(String json) throws Exception {
        if (json.length() > 512_000) throw new IllegalArgumentException("Recovery package is too large.");
        InheritancePlan plan = RecoveryDocument.parse(json);
        if(!InheritanceState.controls(bitcoin.requireWallet().getWallet(),plan.getBeneficiary().getPublicKey()) && !InheritanceState.controls(bitcoin.requireWallet().getWallet(),plan.getOwner().getPublicKey()))
            throw new IllegalArgumentException("This wallet does not control the beneficiary or owner private key. Select the intended beneficiary wallet.");
        if (!plan.isFunded()) throw new IllegalArgumentException("Import a funded inheritance recovery package.");
        for (InheritancePlan existing : getPlans()) {
            if (existing.getId().equals(plan.getId())) {
                if (!existing.getFundingHex().equals(plan.getFundingHex())
                        || existing.getOutputIndex() != plan.getOutputIndex()
                        || !java.util.Arrays.equals(InheritanceScript.redeemScript(existing).getProgram(), InheritanceScript.redeemScript(plan).getProgram()))
                    throw new IllegalArgumentException("Plan ID conflicts with saved recovery data.");
                plan=existing; // Preserve local spend journal and accepted funding on repeated import.
                // A prior import may have stopped after saving the plan. Always finish watching/discovery.
                break;
            }
        }
        repository.save(file(), plan);
        // An imported package does not prove its funding height or the owner's clock accuracy.
        // watches from genesis so checkpoints cannot skip the actual funding transaction.
        bitcoin.requireWallet().getWallet().addWatchedAddress(InheritanceScript.address(plan), Instant.EPOCH);
        bitcoin.persist();
        bitcoin.rescan();
        return plan;
    }
}


