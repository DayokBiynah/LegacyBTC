package com.example.legacybtc;
import org.bitcoinj.base.*;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.*;
import org.bitcoinj.script.*;
import java.util.Arrays;

/** Review is unsigned; signing occurs only in the authenticated service callback. */
public final class InheritanceSpender {
    private InheritanceSpender(){}
    public static Transaction prepare(InheritancePlan p,TransactionOutput funding,Address destination,long rate,boolean owner) {
        WalletPolicy.validateAddress(destination.toString());WalletPolicy.parseFeeRate(Long.toString(rate));
        if(!funding.getOutPointFor().getHash().equals(p.fundingTransaction().getTxId()) || funding.getIndex()!=p.getOutputIndex()
            || !funding.getValue().equals(p.getAmount()) || !Arrays.equals(funding.getScriptPubKey().getProgram(),InheritanceScript.outputScript(p).getProgram()))
            throw new IllegalArgumentException("Funding outpoint, amount or script differs from recovery terms.");
        // Conservative upper bound for one legacy P2SH input, two compressed keys, DER signature and one output.
        Coin fee=Coin.valueOf(Math.multiplyExact(rate,400));
        if(fee.isGreaterThan(WalletPolicy.MAX_TRANSACTION_FEE) || p.getAmount().subtract(fee).value<1000)
            throw new IllegalArgumentException("Output is too small or fee exceeds safety limit. Choose a lower fee rate.");
        Transaction tx=new Transaction();tx.setVersion(2);if(!owner)tx.setLockTime(p.getUnlockTime());
        TransactionInput input=tx.addInput(funding);tx.replaceInput(0,input.withSequence(0xfffffffeL));
        tx.addOutput(p.getAmount().subtract(fee),destination);return tx;
    }
    public static Transaction sign(InheritancePlan p,Transaction review,ECKey key,boolean owner) {
        String pub=owner?p.getOwner().getPublicKey():p.getBeneficiary().getPublicKey();
        if(key==null || !key.hasPrivKey() || !Hex.encode(key.getPubKey()).equals(pub))throw new IllegalArgumentException("Required private signing key unavailable.");
        checkTerms(p,review,owner);
        Transaction tx=Transaction.read(java.nio.ByteBuffer.wrap(review.serialize()));
        TransactionSignature sig=tx.calculateSignature(0,key,InheritanceScript.redeemScript(p),Transaction.SigHash.ALL,false);
        tx.replaceInput(0,tx.getInput(0).withScriptSig(new ScriptBuilder().data(sig.encodeToBitcoin()).smallNum(owner?1:0).data(InheritanceScript.redeemScript(p).getProgram()).build()));
        validate(p,tx,owner);return tx;
    }
    private static void checkTerms(InheritancePlan p,Transaction tx,boolean owner) {
        Transaction.verify(WalletPolicy.NETWORK,tx);
        if(tx.getInputs().size()!=1 || tx.getOutputs().size()!=1 ||
            !tx.getInput(0).getOutpoint().getHash().equals(p.fundingTransaction().getTxId()) || tx.getInput(0).getOutpoint().getIndex()!=p.getOutputIndex() ||
            tx.getLockTime()!=(owner?0:p.getUnlockTime()) || tx.getInput(0).getSequenceNumber()!=0xfffffffeL ||
            tx.getOutput(0).getValue().value<1000 || !tx.getOutput(0).getValue().isLessThan(p.getAmount()) ||
            p.getAmount().subtract(tx.getOutput(0).getValue()).isGreaterThan(WalletPolicy.MAX_TRANSACTION_FEE))
            throw new IllegalArgumentException("Spending transaction differs from inheritance terms.");
    }
    public static void validate(InheritancePlan p,Transaction tx,boolean owner) {
        checkTerms(p,tx,owner);
        java.util.List<ScriptChunk> chunks=tx.getInput(0).getScriptSig().getChunks();
        if(chunks.size()!=3 || chunks.get(1).opcode!=(owner?ScriptOpCodes.OP_1:ScriptOpCodes.OP_0) ||
            !Arrays.equals(chunks.get(2).data,InheritanceScript.redeemScript(p).getProgram()))
            throw new IllegalArgumentException("Incorrect inheritance branch or redeem script.");
        tx.getInput(0).getScriptSig().correctlySpends(tx,0,InheritanceScript.outputScript(p),Script.ALL_VERIFY_FLAGS);
        if(tx.getVsize()>400)throw new IllegalArgumentException("Spending size exceeds fee estimate.");
    }
}
