package com.example.legacybtc;

import org.bitcoinj.core.*;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** Independently verifies an external plan outpoint; never trusts local wallet history or package confirmations. */
final class InheritanceOutputVerifier {
    static final class VerificationFailure extends IOException { VerificationFailure(String message){super(message);} }
    static final class Evidence {
        final Transaction funding, spender;
        final StoredBlock block;
        final int tip;
        final long median;
        final boolean unspent, spendConfirmed;
        Evidence(Transaction funding,StoredBlock block,int tip,long median,boolean unspent,Transaction spender,boolean spendConfirmed) {
            this.funding=funding;this.block=block;this.tip=tip;this.median=median;this.unspent=unspent;this.spender=spender;this.spendConfirmed=spendConfirmed;
        }
    }
    static Evidence verify(InheritancePlan plan,ElectrumRpc a,ElectrumRpc b,ElectrumSync.Headers headers,long median) throws Exception {
        if(!plan.isFunded()) throw new VerificationFailure("No funding transaction attached.");
        String id=plan.fundingTransaction().getTxId().toString();
        String script=ElectrumClient.scriptHash(InheritanceScript.outputScript(plan).getProgram());
        Map<String,Integer> left=history(a,script,headers.tip().getHeight()), right=history(b,script,headers.tip().getHeight());
        if(!left.equals(right)) throw new VerificationFailure("Electrum witnesses disagree about inheritance history.");
        if(!left.containsKey(id)) throw new VerificationFailure("Funding is not present in verified script history; signed bytes alone are not funding.");
        Transaction funding=ElectrumSync.readTransaction(id,a.request("blockchain.transaction.get",new JSONArray().put(id).put(false)));
        if(plan.getOutputIndex()<0 || plan.getOutputIndex()>=funding.getOutputs().size()
                || !funding.getOutput(plan.getOutputIndex()).getValue().equals(plan.getAmount())
                || !Arrays.equals(funding.getOutput(plan.getOutputIndex()).getScriptPubKey().getProgram(),InheritanceScript.outputScript(plan).getProgram()))
            throw new VerificationFailure("Funding amount or script does not match the package.");
        StoredBlock block=proof(a,id,left.get(id),headers);
        Map<String,Long> ua=unspent(a,script,left), ub=unspent(b,script,left);
        if(!ua.equals(ub)) throw new VerificationFailure("Electrum witnesses disagree about inheritance UTXOs.");
        String outpoint=id+":"+plan.getOutputIndex(); boolean unspent=ua.containsKey(outpoint);
        if(unspent && ua.get(outpoint)!=plan.getAmount().value) throw new VerificationFailure("Inheritance UTXO amount mismatch.");
        Transaction spender=null; boolean spendConfirmed=false;
        for(Map.Entry<String,Integer> item:left.entrySet()) {
            if(item.getKey().equals(id)) continue;
            Transaction tx=ElectrumSync.readTransaction(item.getKey(),a.request("blockchain.transaction.get",new JSONArray().put(item.getKey()).put(false)));
            for(TransactionInput input:tx.getInputs()) if(input.getOutpoint().getHash().equals(funding.getTxId()) && input.getOutpoint().getIndex()==plan.getOutputIndex()) {
                if(spender!=null) throw new VerificationFailure("Conflicting inheritance spends; verification required.");
                input.getScriptSig().correctlySpends(tx,tx.getInputs().indexOf(input),InheritanceScript.outputScript(plan),org.bitcoinj.script.Script.ALL_VERIFY_FLAGS);
                spender=tx;spendConfirmed=proof(a,item.getKey(),item.getValue(),headers)!=null;
            }
        }
        if(unspent && spender!=null || !unspent && spender==null) throw new VerificationFailure("Inheritance history and unspent output disagree.");
        return new Evidence(funding,block,headers.tip().getHeight(),median,unspent,spender,spendConfirmed);
    }
    private static Map<String,Integer> history(ElectrumRpc rpc,String script,int tip) throws Exception {
        Object raw=rpc.request("blockchain.scripthash.get_history",new JSONArray().put(script));
        if(!(raw instanceof JSONArray) || ((JSONArray)raw).length()>ElectrumSync.MAX_HISTORY) throw new VerificationFailure("Invalid inheritance history.");
        Map<String,Integer> result=new LinkedHashMap<>(); JSONArray values=(JSONArray)raw;
        for(int i=0;i<values.length();i++) {
            JSONObject value=values.getJSONObject(i); String id=ElectrumProof.txid(value.getString("tx_hash")); int h=value.getInt("height");
            if(h< -1 || h>tip || result.put(id,h)!=null) throw new VerificationFailure("Invalid inheritance history height.");
        }
        return result;
    }
    private static StoredBlock proof(ElectrumRpc rpc,String id,int height,ElectrumSync.Headers headers) throws Exception {
        if(height<=0)return null;
        StoredBlock block=headers.at(height);
        Object raw=rpc.request("blockchain.transaction.get_merkle",new JSONArray().put(id).put(height));
        if(!(raw instanceof JSONObject))throw new VerificationFailure("Missing inheritance inclusion proof.");
        try{ElectrumProof.verifyMerkle(id,(JSONObject)raw,height,block.getHeader().getMerkleRoot());}
        catch(IOException invalid){throw new VerificationFailure("Funding/spend transaction has no valid Merkle inclusion proof.");}
        return block;
    }
    private static Map<String,Long> unspent(ElectrumRpc rpc,String script,Map<String,Integer> history) throws Exception {
        Object raw=rpc.request("blockchain.scripthash.listunspent",new JSONArray().put(script));
        if(!(raw instanceof JSONArray) || ((JSONArray)raw).length()>ElectrumSync.MAX_HISTORY)throw new VerificationFailure("Invalid inheritance UTXO response.");
        Map<String,Long> result=new TreeMap<>(); JSONArray values=(JSONArray)raw;
        for(int i=0;i<values.length();i++) {
            JSONObject v=values.getJSONObject(i); String id=ElectrumProof.txid(v.getString("tx_hash")); long n=v.getLong("tx_pos"), amount=v.getLong("value");
            if(n<0 || n>Integer.MAX_VALUE || amount<0 || !history.containsKey(id) || v.getInt("height")!=Math.max(0,history.get(id))
                    || result.put(id+":"+n,amount)!=null)throw new VerificationFailure("Inconsistent inheritance UTXO.");
        }
        return result;
    }
}
