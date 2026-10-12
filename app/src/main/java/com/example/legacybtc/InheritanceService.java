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

/**
 * I connect the owner and beneficiary interfaces to real funded Bitcoin scripts. Saving a
 * draft records public terms but reserves no coins. Funding creates the P2SH output; recovery
 * and cancellation spend that exact outpoint with the appropriate key. I use User references
 * for runtime dispatch of Owner and Beneficiary permissions, then check chain confidence and
 * median time again before committing a reviewed spend.
 */
public final class InheritanceService {
    private final BitcoinService bitcoin;
    private final InheritanceRepository repository;
    public InheritanceService(Context context) {
        this(BitcoinService.get(context), new InheritanceRepository(context));
    }
    InheritanceService(BitcoinService bitcoin, InheritanceRepository repository) { this.bitcoin = bitcoin; this.repository = repository; }
    private String file() { return bitcoin.requireWallet().getFileName(); }
    /**
     * I load this wallet's encrypted JSON plan collection through the repository and decode
     * each entry into a validated InheritancePlan. An absent file returns an empty ArrayList.
     * Corrupt or wrong-network data raises an error rather than displaying invented recovery
     * eligibility.
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return the wallet-specific ArrayList of decoded validated plans
     */
    public ArrayList<InheritancePlan> getPlans() throws Exception { return repository.getPlans(file()); }
    /**
     * I receive the selected plan ID from the interface and ask the repository to find it
     * within this wallet's encrypted plan collection. A missing ID raises an input error. The
     * returned validated plan is used for funding, status, export, or recovery checks.
     * @param id String supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return the validated saved plan identified by the requested ID
     */
    public InheritancePlan getPlan(String id) throws Exception { return repository.find(file(), id); }
    /**
     * I receive name, compressed public-key hex, and receiving-address Strings from the setup
     * fields and construct a Beneficiary. The User constructor verifies key validity, network,
     * and address/key correspondence. The returned public identity is used in draft creation
     * and contains no signing secret.
     * @param name String supplied by the calling workflow as described above
     * @param key String supplied by the calling workflow as described above
     * @param address String supplied by the calling workflow as described above
     * @return a validated public Beneficiary identity without a private key
     */
    public Beneficiary addBeneficiary(String name, String key, String address) { return new Beneficiary(name, key, address); }
    /**
     * I parse whole hours from the setup field into a long, enforce one hour through ten
     * years, and convert to seconds for the plan constructor. Invalid or excessive values
     * produce a user-facing error. The eventual deadline starts when the draft is saved or
     * updated, not when a block confirms.
     * @param hours String supplied by the calling workflow as described above
     * @return the validated inheritance period in seconds
     */
    public long setInheritancePeriod(String hours) {
        try {
            long value = Long.parseLong(hours.trim());
            if (value >= 1 && value <= 87_840) return value * 3600;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Enter an inheritance period of 1–87840 hours.");
    }
    /**
     * I read the opened wallet's current receiving public key for the Owner identity, combine
     * it with the validated Beneficiary, exact Coin amount, and period, and save an encrypted
     * draft. The returned plan supplies the UI summary. This step creates no funding output
     * and reserves no wallet balance.
     * @param ownerName String supplied by the calling workflow as described above
     * @param beneficiary Beneficiary supplied by the calling workflow as described above
     * @param amount String supplied by the calling workflow as described above
     * @param period long supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return the encrypted saved draft, without a funded Bitcoin output
     */
    public InheritancePlan createInheritancePlan(String ownerName, Beneficiary beneficiary, String amount, long period) throws Exception {
        ECKey key = bitcoin.requireWallet().getWallet().currentReceiveKey();
        Owner owner = new Owner(ownerName, Hex.encode(key.getPubKey()), key.toAddress(ScriptType.P2WPKH, WalletPolicy.NETWORK).toString());
        InheritancePlan plan = new InheritancePlan(owner, beneficiary, WalletPolicy.parseAmount(amount), period, Instant.now().getEpochSecond());
        repository.save(file(), plan);
        return plan;
    }
    /**
     * I load the selected plan and invoke canManage through a User reference, letting Java
     * dispatch to the actual role. Only an active unfunded owner draft can be replaced with
     * newly validated terms and a fresh deadline. Funded terms are immutable; their owner must
     * spend the output and create a replacement.
     * @param id String supplied by the calling workflow as described above
     * @param beneficiary Beneficiary supplied by the calling workflow as described above
     * @param amount String supplied by the calling workflow as described above
     * @param period long supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return the replacement unfunded draft with the same ID and new deadline
     */
    public InheritancePlan updatePlan(String id, Beneficiary beneficiary, String amount, long period) throws Exception {
        InheritancePlan old = getPlan(id);
        User role = old.userFor(bitcoin.requireWallet().getWallet());
        if (!role.canManage(old)) throw new IllegalStateException("Only the owner can update a plan.");
        if (old.isFunded()) throw new IllegalStateException("A funded Bitcoin script is immutable. Cancel it on-chain, then save and fund a new plan.");
        if (old.isCancelled()) throw new IllegalStateException("Save a new plan instead of updating a cancelled plan.");
        InheritancePlan updated = new InheritancePlan(old.getId(), old.getOwner(), beneficiary,
                WalletPolicy.parseAmount(amount), period, Instant.now().getEpochSecond());
        repository.save(file(), updated);
        return updated;
    }
    /**
     * I require synchronized peers and an active owner-controlled unfunded plan whose calendar
     * deadline has not passed. I watch its P2SH address and prepare an unsigned ordinary
     * payment to that script for the exact covered amount. The returned SendRequest provides
     * the fee and total for a separate funding review.
     * @param id String supplied by the calling workflow as described above
     * @param feeRate String supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return the unsigned request paying the plan script and covered amount
     */
    public SendRequest prepareFunding(String id, String feeRate) throws Exception {
        bitcoin.requireReady();
        InheritancePlan plan = getPlan(id);
        if (plan.isFunded() || plan.isCancelled()) throw new IllegalStateException("Choose an unfunded active draft.");
        if (!plan.userFor(bitcoin.requireWallet().getWallet()).canManage(plan)) throw new IllegalStateException("Only the owner can fund this plan.");
        if (Instant.now().getEpochSecond() >= plan.getUnlockTime()) throw new IllegalStateException("The draft deadline passed. Update the inheritance period first.");
        bitcoin.requireWallet().getWallet().addWatchedAddress(InheritanceScript.address(plan), Instant.ofEpochSecond(plan.getCreatedAt()));
        return bitcoin.preparePayment(InheritanceScript.address(plan).toString(), plan.getAmount().toPlainString(), feeRate);
    }
    /**
     * I recheck ownership, draft state, deadline, and the exact funding output against the
     * reviewed SendRequest. After signing, I attach and encrypt public recovery data before
     * committing and encrypting the pending wallet transaction. Failure propagates;
     * broadcasting remains a separate explicit UI action.
     * @param id String supplied by the calling workflow as described above
     * @param reviewed SendRequest supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return the signed funding transaction saved as pending
     */
    public Transaction fundPlan(String id, SendRequest reviewed) throws Exception {
        InheritancePlan plan = getPlan(id);
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
    /**
     * I derive status from current evidence. A draft has no Bitcoin funding. Funded plans
     * require synchronized peers, a known non-conflicting funding transaction, confirmation
     * depth, unspent output, and no pending spend of that outpoint. I compare median chain
     * time strictly greater than the deadline for beneficiary availability. Offline or
     * conflicting evidence cannot grant recovery.
     * @param plan InheritancePlan supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return the plan state determined by current verification evidence
     */
    public InheritancePlan.Status checkInheritanceStatus(InheritancePlan plan) throws Exception {
        if (!plan.isFunded()) return plan.checkInheritanceStatus(false, false, true, false, 0);
        if (!bitcoin.getNetwork().isReady()) return InheritancePlan.Status.UNVERIFIED;
        Wallet wallet = bitcoin.requireWallet().getWallet();
        Transaction funding = wallet.getTransaction(plan.fundingTransaction().getTxId());
        if (funding == null) return InheritancePlan.Status.UNVERIFIED;
        TransactionConfidence.ConfidenceType confidence = funding.getConfidence().getConfidenceType();
        if (confidence == TransactionConfidence.ConfidenceType.DEAD || confidence == TransactionConfidence.ConfidenceType.IN_CONFLICT)
            return InheritancePlan.Status.UNVERIFIED;
        boolean confirmed = confidence == TransactionConfidence.ConfidenceType.BUILDING && funding.getConfidence().getDepthInBlocks() > 0;
        TransactionOutput output = funding.getOutput(plan.getOutputIndex());
        boolean pending = false;
        for (Transaction tx : wallet.getTransactions(true)) {
            for (org.bitcoinj.core.TransactionInput input : tx.getInputs()) {
                if (input.getOutpoint().getHash().equals(funding.getTxId()) && input.getOutpoint().getIndex() == plan.getOutputIndex()
                        && tx.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING) pending = true;
            }
        }
        return plan.checkInheritanceStatus(true, confirmed, output.isAvailableForSpending(), pending, bitcoin.getNetwork().medianTimePast());
    }
    /**
     * I receive a plan ID, fee-rate String, and cancellation boolean. The service checks the
     * opened wallet's dynamically dispatched role and verified status, finds the required
     * private ECKey, and passes the exact funding output to InheritanceScript.spend. The
     * returned signed transaction is only a review candidate; no commitment or relay occurs
     * here.
     * @param id String supplied by the calling workflow as described above
     * @param feeRate String supplied by the calling workflow as described above
     * @param cancel boolean supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return a signed review candidate that has not been committed or relayed
     */
    public Transaction prepareRecovery(String id, String feeRate, boolean cancel) throws Exception {
        bitcoin.requireReady();
        InheritancePlan plan = getPlan(id);
        User role = plan.userFor(bitcoin.requireWallet().getWallet());
        InheritancePlan.Status status = checkInheritanceStatus(plan);
        if (cancel) {
            if (!role.canManage(plan) || (status != InheritancePlan.Status.TIMELOCKED && status != InheritancePlan.Status.AVAILABLE))
                throw new IllegalStateException("Cancellation requires a confirmed, unspent owner-controlled plan.");
        } else if (!role.canRecover(plan, status)) throw new IllegalStateException("Recovery is unavailable until the funding is confirmed, the timelock is satisfied, and this wallet holds the beneficiary key.");
        Wallet wallet = bitcoin.requireWallet().getWallet();
        ECKey key = wallet.findKeyFromPubKey(Hex.decode(role.getPublicKey()));
        Transaction funding = wallet.getTransaction(plan.fundingTransaction().getTxId());
        return InheritanceScript.spend(plan, funding.getOutput(plan.getOutputIndex()), key, wallet.currentReceiveAddress(),
                WalletPolicy.parseFeeRate(feeRate), cancel);
    }
    /**
     * I recheck plan eligibility at submission after the review and authentication delay. I
     * require the reviewed input outpoint, locktime, non-final sequence, recipient ownership,
     * output count, and bounded fee to match the plan. Script verification binds the
     * beneficiary or owner signature to that exact spend. I commit and encrypt it before the
     * screen requests relay.
     * @param id String supplied by the calling workflow as described above
     * @param reviewed Transaction supplied by the calling workflow as described above
     * @param cancel boolean supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return the authenticated reviewed spend committed and encrypted locally
     */
    public Transaction recoverInheritance(String id, Transaction reviewed, boolean cancel) throws Exception {
        InheritancePlan plan = getPlan(id);
        Transaction expected = prepareRecovery(id, "1", cancel);
        if (reviewed.getInputs().size() != 1 || reviewed.getOutputs().size() != 1
                || !expected.getInput(0).getOutpoint().equals(reviewed.getInput(0).getOutpoint())
                || reviewed.getLockTime() != expected.getLockTime()
                || reviewed.getInput(0).getSequenceNumber() == 0xffffffffL
                || reviewed.getOutput(0).getValue().value < 1000
                || !reviewed.getOutput(0).getValue().isLessThan(plan.getAmount())
                || plan.getAmount().subtract(reviewed.getOutput(0).getValue()).isGreaterThan(WalletPolicy.MAX_TRANSACTION_FEE)
                || !reviewed.getOutput(0).isMine(bitcoin.requireWallet().getWallet()))
            throw new IllegalStateException("Recovery transaction does not match the plan or selected wallet.");
        // Signatures bind the recipient and fee; verify the exact reviewed transaction again.
        reviewed.getInput(0).getScriptSig().correctlySpends(reviewed, 0, InheritanceScript.outputScript(plan), org.bitcoinj.script.Script.ALL_VERIFY_FLAGS);
        Wallet wallet = bitcoin.requireWallet().getWallet();
        reviewed.setMemo(cancel ? "LegacyBTC inheritance cancellation" : "LegacyBTC inheritance recovery");
        reviewed.getConfidence().setSource(TransactionConfidence.Source.SELF);
        wallet.commitTx(reviewed);
        bitcoin.persist();
        return reviewed;
    }
    /**
     * I mark an unfunded draft cancelled through its controlled domain method and repository
     * persistence when called through the service. A funded plan is rejected because changing
     * local metadata cannot remove an on-chain lock. The owner must use the signed
     * cancellation branch for funded Bitcoin.
     * @param id String supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     */
    public void cancelDraft(String id) throws Exception {
        InheritancePlan plan = getPlan(id);
        if (!plan.userFor(bitcoin.requireWallet().getWallet()).canManage(plan)) throw new IllegalStateException("Only the owner can cancel this draft.");
        plan.cancelDraft(); repository.save(file(), plan);
    }
    /**
     * I retrieve the selected plan and encode its public terms and funding data as formatted
     * JSON for the document picker or details dialog. The result has no private keys or
     * mnemonic. It must still be backed up because seed recovery alone cannot reconstruct this
     * custom script.
     * @param id String supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return formatted public JSON recovery information without signing secrets
     */
    public String exportPlan(String id) throws Exception { return InheritanceRepository.encode(getPlan(id)).toString(2); }
    /**
     * I receive public JSON text from Android's document picker, bound its size, and decode
     * its network and exact funding terms. I require this wallet to match an owner or
     * beneficiary identity. A duplicate ID must match existing recovery data. I save the
     * imported plan, watch its script, and replay history; import alone never establishes
     * confirmation.
     * @param json String supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return the validated imported or matching saved funded plan
     */
    public InheritancePlan importPlan(String json) throws Exception {
        if (json.length() > 512_000) throw new IllegalArgumentException("Recovery package is too large.");
        InheritancePlan plan = InheritanceRepository.decode(new org.json.JSONObject(json));
        plan.userFor(bitcoin.requireWallet().getWallet());
        if (!plan.isFunded()) throw new IllegalArgumentException("Import a funded inheritance recovery package.");
        for (InheritancePlan existing : getPlans()) {
            if (existing.getId().equals(plan.getId())) {
                if (!existing.getFundingHex().equals(plan.getFundingHex())
                        || existing.getOutputIndex() != plan.getOutputIndex()
                        || !java.util.Arrays.equals(InheritanceScript.redeemScript(existing).getProgram(), InheritanceScript.redeemScript(plan).getProgram()))
                    throw new IllegalArgumentException("Plan ID conflicts with saved recovery data.");
                return existing;
            }
        }
        repository.save(file(), plan);
        // An imported package does not prove its funding height or the owner's clock accuracy.
        // I watch from genesis so checkpoints cannot skip the actual funding transaction.
        bitcoin.requireWallet().getWallet().addWatchedAddress(InheritanceScript.address(plan), Instant.EPOCH);
        bitcoin.persist();
        bitcoin.rescan();
        return plan;
    }
}
