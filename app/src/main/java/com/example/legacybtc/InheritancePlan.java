package com.example.legacybtc;

import org.bitcoinj.base.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * I encapsulate one inheritance arrangement with private identities, satoshi amount, creation
 * time, period, absolute deadline, and funding outpoint. Constructors reject unsafe terms.
 * Once funded, the output script is immutable and updates require an on-chain owner
 * cancellation followed by a replacement. Status is derived from current verification evidence
 * rather than a phone countdown. No field contains a private key.
 */
public final class InheritancePlan {
    public enum Status { DRAFT, UNVERIFIED, FUNDING_PENDING, TIMELOCKED, AVAILABLE, SPEND_PENDING, SPENT, CANCELLED }
    private final String id;
    private final Owner owner;
    private final Beneficiary beneficiary;
    private final Coin amount;
    private final long createdAt, periodSeconds, unlockTime;
    private String fundingHex = "";
    private int outputIndex = -1;
    private boolean cancelled;
    public InheritancePlan(Owner owner, Beneficiary beneficiary, Coin amount, long periodSeconds, long now) {
        this(UUID.randomUUID().toString(), owner, beneficiary, amount, periodSeconds, now);
    }
    InheritancePlan(String id, Owner owner, Beneficiary beneficiary, Coin amount, long periodSeconds, long now) {
        // I receive public identities, exact Coin satoshis and long second values from
        // validated setup fields or package decoding. These checks apply to both paths.
        if (!id.matches("[a-zA-Z0-9-]{1,64}")) throw new IllegalArgumentException("Invalid plan ID.");
        if (owner.getPublicKey().equals(beneficiary.getPublicKey())) throw new IllegalArgumentException("Choose a beneficiary key different from the owner key.");
        if (amount.value < 10_000 || amount.isGreaterThan(Coin.COIN.multiply(21_000_000)))
            throw new IllegalArgumentException("Inheritance amount must be at least 0.0001 BTC and within Bitcoin's supply.");
        if (periodSeconds < 3600 || periodSeconds > 10L * 366 * 86400)
            throw new IllegalArgumentException("Use an inheritance period from one hour to ten years.");
        if (now < 500_000_000L || now > 0xffffffffL - periodSeconds)
            throw new IllegalArgumentException("Timelock date is outside Bitcoin's supported range.");
        this.id = id; this.owner = owner; this.beneficiary = beneficiary; this.amount = amount;
        // Bitcoin interprets locktimes below 500,000,000 as heights; I require the timestamp
        // range and prevent unsigned 32-bit overflow before calculating the absolute deadline.
        this.periodSeconds = periodSeconds; this.createdAt = now; this.unlockTime = now + periodSeconds;
    }
    /**
     * I receive a Transaction and integer output index and compare that output's amount and
     * locking script with the plan. I store its serialized public bytes and index only once.
     * This binds recovery to one exact UTXO; a different funding output is rejected.
     * @param tx Transaction supplied by the calling workflow as described above
     * @param index int supplied by the calling workflow as described above
     */
    public void attachFunding(Transaction tx, int index) {
        if (!fundingHex.isEmpty()) throw new IllegalStateException("Funding is already attached.");
        if (index < 0 || index >= tx.getOutputs().size()
                || !tx.getOutput(index).getValue().equals(amount)
                || !java.util.Arrays.equals(tx.getOutput(index).getScriptPubKey().getProgram(), InheritanceScript.outputScript(this).getProgram()))
            throw new IllegalArgumentException("Funding output does not match this inheritance plan.");
        fundingHex = Hex.encode(tx.serialize()); outputIndex = index;
    }
    /**
     * I mark an unfunded draft cancelled through its controlled domain method and repository
     * persistence when called through the service. A funded plan is rejected because changing
     * local metadata cannot remove an on-chain lock. The owner must use the signed
     * cancellation branch for funded Bitcoin.
     */
    public void cancelDraft() {
        if (isFunded()) throw new IllegalStateException("Funded plans must be cancelled with an owner-signed Bitcoin transaction.");
        cancelled = true;
    }
    /**
     * I derive status from current evidence. A draft has no Bitcoin funding. Funded plans
     * require synchronized peers, a known non-conflicting funding transaction, confirmation
     * depth, unspent output, and no pending spend of that outpoint. I compare median chain
     * time strictly greater than the deadline for beneficiary availability. Offline or
     * conflicting evidence cannot grant recovery.
     * @param synced boolean supplied by the calling workflow as described above
     * @param confirmed boolean supplied by the calling workflow as described above
     * @param unspent boolean supplied by the calling workflow as described above
     * @param spendPending boolean supplied by the calling workflow as described above
     * @param medianTime long supplied by the calling workflow as described above
     * @return the plan state determined by current verification evidence
     */
    public Status checkInheritanceStatus(boolean synced, boolean confirmed, boolean unspent, boolean spendPending, long medianTime) {
        if (cancelled) return Status.CANCELLED;
        if (!isFunded()) return Status.DRAFT;
        if (!synced) return Status.UNVERIFIED;
        if (spendPending) return Status.SPEND_PENDING;
        if (!unspent) return Status.SPENT;
        if (!confirmed) return Status.FUNDING_PENDING;
        // BIP-113 finality is strict: the chain's median time must exceed nLockTime.
        return medianTime > unlockTime ? Status.AVAILABLE : Status.TIMELOCKED;
    }
    /**
     * I check whether the opened Wallet contains the owner or beneficiary public key and
     * return that subclass through the abstract User type. Subsequent canManage and canRecover
     * calls use runtime method dispatch. An unrelated wallet is rejected; the signing service
     * additionally requires a private key.
     * @param wallet Wallet supplied by the calling workflow as described above
     * @return the actual Owner or Beneficiary through a parent User reference
     */
    public User userFor(Wallet wallet) {
        if (wallet.findKeyFromPubKey(Hex.decode(owner.getPublicKey())) != null) return owner;
        if (wallet.findKeyFromPubKey(Hex.decode(beneficiary.getPublicKey())) != null) return beneficiary;
        throw new IllegalArgumentException("This wallet is neither the owner nor the beneficiary.");
    }
    public String getId() { return id; }
    public Owner getOwner() { return owner; }
    public Beneficiary getBeneficiary() { return beneficiary; }
    public Coin getAmount() { return amount; }
    public long getCreatedAt() { return createdAt; }
    public long getPeriodSeconds() { return periodSeconds; }
    public long getUnlockTime() { return unlockTime; }
    public String getFundingHex() { return fundingHex; }
    public int getOutputIndex() { return outputIndex; }
    public boolean isFunded() { return !fundingHex.isEmpty(); }
    public boolean isCancelled() { return cancelled; }
    /**
     * I require attached funding bytes and pass decoded hexadecimal data to Transaction.read
     * with a ByteBuffer. The returned public Transaction recreates the funding ID and output
     * used by recovery. It does not establish that peers confirmed the transaction; the
     * service checks wallet confidence separately.
     * @return the parsed public funding transaction, without confirmation proof
     */
    public Transaction fundingTransaction() {
        if (!isFunded()) throw new IllegalStateException("Fund the plan first.");
        return Transaction.read(ByteBuffer.wrap(Hex.decode(fundingHex)));
    }
}
