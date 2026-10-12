package com.example.legacybtc;

import org.bitcoinj.base.Address;
import org.bitcoinj.base.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import static org.bitcoinj.script.ScriptOpCodes.*;

/**
 * This class is responsible for the Bitcoin-enforced inheritance script and signed spends.
 * I use bitcoinj ScriptBuilder and Transaction APIs rather than custom cryptography.
 * The plan locks one funded P2SH output: the owner can cancel with their key, while the
 * beneficiary needs their own key and an absolute timestamp CHECKLOCKTIMEVERIFY condition.
 * Bitcoin does not detect death. CLTV checks transaction fields; blockchain finality must
 * separately compare nLockTime with median time past before a beneficiary spend is relayed.
 */
public final class InheritanceScript {
    private InheritanceScript() { }
    /**
     * I receive a validated InheritancePlan from the repository. OP_IF chooses the owner
     * branch when the unlocking stack contains true; OP_CHECKSIG verifies its signature.
     * OP_ELSE selects the beneficiary branch, whose deadline is a timestamp greater than
     * 500,000,000. CHECKLOCKTIMEVERIFY requires a matching timestamp type, nLockTime at
     * least the deadline, and a non-final spending input. OP_DROP removes the deadline
     * before the beneficiary CHECKSIG. ENDIF joins the branches. This is absolute CLTV,
     * not relative CSV: the period does not restart when the funding confirms.
     * @param plan identities, covered amount, and immutable deadline
     * @return redeem script whose hash is committed by the funding output
     */
    public static Script redeemScript(InheritancePlan plan) {
        // IF owner CHECKSIG ELSE deadline CLTV DROP beneficiary CHECKSIG ENDIF.
        // The owner branch permits cancellation; the beneficiary branch cannot bypass the deadline.
        return new ScriptBuilder().op(OP_IF).data(Hex.decode(plan.getOwner().getPublicKey())).op(OP_CHECKSIG)
                .op(OP_ELSE).number(plan.getUnlockTime()).op(OP_CHECKLOCKTIMEVERIFY).op(OP_DROP)
                .data(Hex.decode(plan.getBeneficiary().getPublicKey())).op(OP_CHECKSIG).op(OP_ENDIF).build();
    }
    /**
     * I hash the validated redeem script with bitcoinj ScriptBuilder.createP2SHOutputScript
     * and return the locking Script. Funding pays this script hash; recovery must reveal the
     * original redeem script and satisfy its signature and timelock branch.
     * @param plan InheritancePlan supplied by the calling workflow as described above
     * @return the P2SH locking Script that commits to the redeem-script hash
     */
    public static Script outputScript(InheritancePlan plan) { return ScriptBuilder.createP2SHOutputScript(redeemScript(plan)); }
    /**
     * I convert the inheritance P2SH output script into its public address using this build's
     * BitcoinNetwork. The owner funds this destination and the beneficiary wallet watches it.
     * An address is public routing information; it does not authorize a spend.
     * @param plan InheritancePlan supplied by the calling workflow as described above
     * @return the selected-network public P2SH address for the inheritance output
     */
    public static Address address(InheritancePlan plan) { return outputScript(plan).getToAddress(WalletPolicy.NETWORK); }

    /**
     * This method receives the plan, its funding TransactionOutput, the authorized ECKey,
     * a parsed destination Address, a long fee rate, and a boolean selecting owner versus
     * beneficiary. I compare the key, amount, and script with the saved terms before
     * adding the exact funding outpoint as a transaction input. The fee is deducted from
     * the covered amount using a conservative 400-byte upper bound, and the remainder
     * becomes one output. I use SIGHASH_ALL to bind the signature to the reviewed output
     * and fee. The unlocking script pushes the signature, branch selector, and redeem
     * script; correctlySpends checks the signed script before returning the transaction.
     * This method neither commits nor broadcasts it. The service additionally checks
     * confirmed/unspent funding and median time, which script verification alone cannot
     * prove. The screen requests user confirmation and Android authentication to submit.
     * @param plan funded inheritance terms
     * @param funding exact output identified by the plan
     * @param key private signing key for the selected branch
     * @param destination selected wallet's receiving address
     * @param feeRate satoshis per virtual byte
     * @param ownerBranch true for cancellation; false for beneficiary recovery
     * @return signed transaction for review
     * @throws IllegalArgumentException if identity, script, amount, or fee is invalid
     */
    public static Transaction spend(InheritancePlan plan, TransactionOutput funding, ECKey key,
                                    Address destination, long feeRate, boolean ownerBranch) {
        WalletPolicy.validateAddress(destination.toString());
        String expected = ownerBranch ? plan.getOwner().getPublicKey() : plan.getBeneficiary().getPublicKey();
        if (!Hex.encode(key.getPubKey()).equals(expected) || !key.hasPrivKey())
            throw new IllegalArgumentException("This wallet does not hold the required signing key.");
        if (!funding.getValue().equals(plan.getAmount())
                || !java.util.Arrays.equals(funding.getScriptPubKey().getProgram(), outputScript(plan).getProgram()))
            throw new IllegalArgumentException("Recovery output does not match the plan.");
        WalletPolicy.parseFeeRate(Long.toString(feeRate));
        // 400 vbytes safely bounds this single-input P2SH transaction, including its redeem script.
        Coin fee = Coin.valueOf(Math.multiplyExact(feeRate, 400));
        Coin value = funding.getValue().subtract(fee);
        if (value.value < 1000) throw new IllegalArgumentException("The inheritance amount is too small for this recovery fee.");
        Transaction tx = new Transaction();
        tx.setVersion(2);
        if (!ownerBranch) tx.setLockTime(plan.getUnlockTime());
        TransactionInput input = tx.addInput(funding);
        // A final sequence disables nLockTime. Keep it non-final for the beneficiary branch.
        tx.replaceInput(0, input.withSequence(0xfffffffeL));
        tx.addOutput(value, destination);
        TransactionSignature signature = tx.calculateSignature(0, key, redeemScript(plan), Transaction.SigHash.ALL, false);
        Script unlocking = new ScriptBuilder().data(signature.encodeToBitcoin())
                .smallNum(ownerBranch ? 1 : 0).data(redeemScript(plan).getProgram()).build();
        tx.replaceInput(0, tx.getInput(0).withScriptSig(unlocking));
        tx.getInput(0).getScriptSig().correctlySpends(tx, 0, outputScript(plan), Script.ALL_VERIFY_FLAGS);
        return tx;
    }
}
