package com.example.legacybtc;

import org.junit.Test;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.script.Script;
import static org.junit.Assert.*;

/** Offline cryptographic tests; all keys and funding outputs are freshly generated test data. */
public class InheritanceScriptTest {
    private final ECKey ownerKey = new ECKey(), beneficiaryKey = new ECKey();
    private InheritancePlan plan() {
        new org.bitcoinj.core.Context();
        Owner owner = new Owner("Owner", Hex.encode(ownerKey.getPubKey()), ownerKey.toAddress(ScriptType.P2WPKH, WalletPolicy.NETWORK).toString());
        Beneficiary beneficiary = new Beneficiary("Beneficiary", Hex.encode(beneficiaryKey.getPubKey()), beneficiaryKey.toAddress(ScriptType.P2WPKH, WalletPolicy.NETWORK).toString());
        return new InheritancePlan(owner, beneficiary, Coin.CENT, 3600, 1_800_000_000L);
    }
    private Transaction funding(InheritancePlan plan) {
        Transaction tx = new Transaction(); tx.addOutput(plan.getAmount(), InheritanceScript.outputScript(plan)); return tx;
    }
    @Test public void beneficiarySignatureRequiresMatchingTimelockAndNonfinalSequence() {
        InheritancePlan plan = plan();
        Transaction funding = funding(plan);
        Transaction claim = InheritanceScript.spend(plan, funding.getOutput(0), beneficiaryKey,
                beneficiaryKey.toAddress(ScriptType.P2WPKH, WalletPolicy.NETWORK), 5, false);
        assertEquals(plan.getUnlockTime(), claim.getLockTime());
        assertTrue("Review must not mark an inheritance output spent", funding.getOutput(0).isAvailableForSpending());
        assertEquals(plan.getAmount().subtract(Coin.valueOf(2000)), claim.getOutput(0).getValue());
        claim.setLockTime(plan.getUnlockTime() - 1);
        resignBeneficiary(plan, claim); // A valid signature must still fail the timelock rule.
        assertThrows(org.bitcoinj.script.ScriptException.class,
                () -> claim.getInput(0).getScriptSig().correctlySpends(claim, 0, InheritanceScript.outputScript(plan), Script.ALL_VERIFY_FLAGS));
        claim.setLockTime(plan.getUnlockTime());
        claim.replaceInput(0, claim.getInput(0).withSequence(0xffffffffL));
        resignBeneficiary(plan, claim);
        assertThrows(org.bitcoinj.script.ScriptException.class,
                () -> claim.getInput(0).getScriptSig().correctlySpends(claim, 0, InheritanceScript.outputScript(plan), Script.ALL_VERIFY_FLAGS));
    }
    private void resignBeneficiary(InheritancePlan plan, Transaction tx) {
        org.bitcoinj.crypto.TransactionSignature signature = tx.calculateSignature(0, beneficiaryKey,
                InheritanceScript.redeemScript(plan), Transaction.SigHash.ALL, false);
        tx.replaceInput(0, tx.getInput(0).withScriptSig(new org.bitcoinj.script.ScriptBuilder()
                .data(signature.encodeToBitcoin()).smallNum(0).data(InheritanceScript.redeemScript(plan).getProgram()).build()));
    }
    @Test public void inheritedOutputsCannotBeSpentByNormalWalletPayments() throws Exception {
        InheritancePlan plan = plan();
        org.bitcoinj.wallet.Wallet wallet = WalletPolicy.createWallet();
        wallet.addWatchedAddress(InheritanceScript.address(plan));
        Transaction funding = new Transaction();
        funding.addInput(org.bitcoinj.base.Sha256Hash.of(new byte[]{7}), 0, org.bitcoinj.script.ScriptBuilder.createEmpty());
        funding.addOutput(plan.getAmount(), InheritanceScript.outputScript(plan));
        wallet.commitTx(funding);
        funding.getConfidence().setConfidenceType(org.bitcoinj.core.TransactionConfidence.ConfidenceType.BUILDING);
        funding.getConfidence().setDepthInBlocks(6);
        assertEquals(Coin.ZERO, WalletPolicy.getBalance(wallet));
        assertEquals(Coin.ZERO, WalletPolicy.getOutputBalance(wallet, true));
        assertEquals(Coin.ZERO, WalletPolicy.getOutputBalance(wallet, false));
        assertThrows(IllegalArgumentException.class, () -> BitcoinPayments.prepare(wallet,
                ownerKey.toAddress(ScriptType.P2WPKH, WalletPolicy.NETWORK).toString(), "0.001", "5"));
    }
    @Test public void ownerCanCancelAndUnrelatedKeysCannotSpend() {
        InheritancePlan plan = plan();
        Transaction tx = InheritanceScript.spend(plan, funding(plan).getOutput(0), ownerKey,
                ownerKey.toAddress(ScriptType.P2WPKH, WalletPolicy.NETWORK), 5, true);
        assertEquals(0, tx.getLockTime());
        assertThrows(IllegalArgumentException.class, () -> InheritanceScript.spend(plan, funding(plan).getOutput(0), new ECKey(),
                ownerKey.toAddress(ScriptType.P2WPKH, WalletPolicy.NETWORK), 5, false));
    }
    @Test public void statusAndDynamicBindingEnforceRecoveryConditions() {
        InheritancePlan plan = plan(); plan.attachFunding(funding(plan), 0);
        assertEquals(InheritancePlan.Status.UNVERIFIED, plan.checkInheritanceStatus(false, true, true, false, Long.MAX_VALUE));
        assertEquals(InheritancePlan.Status.FUNDING_PENDING, plan.checkInheritanceStatus(true, false, true, false, Long.MAX_VALUE));
        assertEquals(InheritancePlan.Status.TIMELOCKED, plan.checkInheritanceStatus(true, true, true, false, plan.getUnlockTime()));
        assertEquals(InheritancePlan.Status.AVAILABLE, plan.checkInheritanceStatus(true, true, true, false, plan.getUnlockTime() + 1));
        assertEquals(InheritancePlan.Status.SPENT, plan.checkInheritanceStatus(true, true, false, false, Long.MAX_VALUE));
        User user = plan.getBeneficiary();
        assertTrue(user.canRecover(plan, InheritancePlan.Status.AVAILABLE));
        assertFalse(user.canRecover(plan, InheritancePlan.Status.TIMELOCKED));
        user = plan.getOwner();
        assertFalse(user.canRecover(plan, InheritancePlan.Status.AVAILABLE));
        assertTrue(user.canManage(plan));
        assertNotEquals(plan.getOwner().describeRole(), plan.getBeneficiary().describeRole());
    }
}
