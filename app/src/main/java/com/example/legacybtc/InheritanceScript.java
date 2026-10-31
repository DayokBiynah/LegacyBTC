package com.example.legacybtc;
import org.bitcoinj.base.Address;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.script.*;
import static org.bitcoinj.script.ScriptOpCodes.*;

/** Script identity is deliberately byte-compatible with existing funded P2SH outputs. */
public final class InheritanceScript {
    private InheritanceScript(){}
    public static Script redeemScript(InheritancePlan p) {
        return new ScriptBuilder().op(OP_IF).data(Hex.decode(p.getOwner().getPublicKey())).op(OP_CHECKSIG)
            .op(OP_ELSE).number(p.getUnlockTime()).op(OP_CHECKLOCKTIMEVERIFY).op(OP_DROP)
            .data(Hex.decode(p.getBeneficiary().getPublicKey())).op(OP_CHECKSIG).op(OP_ENDIF).build();
    }
    public static Script outputScript(InheritancePlan p){return ScriptBuilder.createP2SHOutputScript(redeemScript(p));}
    public static Address address(InheritancePlan p){return outputScript(p).getToAddress(WalletPolicy.NETWORK);}
    public static Transaction spend(InheritancePlan p,TransactionOutput funding,ECKey key,Address destination,long feeRate,boolean owner) {
        return InheritanceSpender.sign(p,InheritanceSpender.prepare(p,funding,destination,feeRate,owner),key,owner);
    }
}
