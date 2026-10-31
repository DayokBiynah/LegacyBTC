package com.example.legacybtc;

import org.bitcoinj.base.*;
import org.bitcoinj.core.*;
import org.bitcoinj.wallet.*;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.script.ScriptBuilder;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class OnChainLifecycleTest {
    private static void context(){new org.bitcoinj.core.Context();}
    private static Transaction income(Wallet wallet){Transaction t=new Transaction();t.addInput(Sha256Hash.of(new byte[]{17,19,23}),0,ScriptBuilder.createEmpty());t.addOutput(Coin.COIN,wallet.currentReceiveAddress());return t;}
    private static ElectrumSync.Headers headers(ElectrumSyncTest.Server server){return new ElectrumSync.Headers(){
        public StoredBlock at(int height){String id=server.heights.entrySet().stream().filter(e->e.getValue()==height).findFirst().map(Map.Entry::getKey).orElse(Sha256Hash.ZERO_HASH.toString());return new StoredBlock(new Block(1,Sha256Hash.ZERO_HASH,Sha256Hash.wrap(id),java.time.Instant.ofEpochSecond(1_800_000_000),0x1d00ffffL,0,null),java.math.BigInteger.ONE,height);}
        public StoredBlock tip(){return at(20);}
    };}
    private static InheritancePlan plan(Wallet owner,Wallet beneficiary){return new InheritancePlan(new Owner("Owner",owner.currentReceiveKey().getPublicKeyAsHex(),owner.currentReceiveAddress().toString()),new Beneficiary("Beneficiary",beneficiary.currentReceiveKey().getPublicKeyAsHex(),beneficiary.currentReceiveAddress().toString()),Coin.CENT,60,1_800_000_000);}
    private static void attach(InheritancePlan plan,Transaction funding){for(int i=0;i<funding.getOutputs().size();i++)if(Arrays.equals(funding.getOutput(i).getScriptPubKey().getProgram(),InheritanceScript.outputScript(plan).getProgram())){plan.attachFunding(funding,i);return;}throw new AssertionError();}
    private static Wallet fundOwner(Wallet owner,ElectrumSyncTest.Server server)throws Exception{Transaction in=income(owner);server.income(in,1);return new ElectrumSync(server,server,headers(server),new JSONObject(),()->true,s->{}).synchronize(owner);}
    @Test public void externalFundingPackageClaimAndConfirmationWorkWithoutBeneficiaryHistory()throws Exception{
        context();Wallet owner=WalletPolicy.createWallet(),beneficiary=WalletPolicy.createWallet();ElectrumSyncTest.Server server=new ElectrumSyncTest.Server();owner=fundOwner(owner,server);
        InheritancePlan plan=plan(owner,beneficiary);SendRequest funding=BitcoinPayments.prepare(owner,InheritanceScript.address(plan).toString(),"0.01","2");funding.signInputs=true;owner.signTransaction(funding);attach(plan,funding.tx);
        assertFalse(plan.isFundingAccepted());plan.markFundingAccepted();
        InheritancePlan imported=InheritanceRepository.decode(InheritanceRepository.encode(plan));
        assertSame(imported.getBeneficiary(),imported.userFor(beneficiary));assertNull(beneficiary.getTransaction(funding.tx.getTxId()));
        assertThrows(IllegalArgumentException.class,()->imported.userFor(WalletPolicy.createWallet()));
        server.income(funding.tx,2);
        InheritanceOutputVerifier.Evidence locked=InheritanceOutputVerifier.verify(imported,server,server,headers(server),plan.getUnlockTime());
        assertNotNull(locked.block);assertTrue(locked.unspent);
        assertEquals(InheritancePlan.Status.TIMELOCKED,plan.checkInheritanceStatus(true,true,true,false,locked.median));
        InheritanceOutputVerifier.Evidence mature=InheritanceOutputVerifier.verify(imported,server,server,headers(server),plan.getUnlockTime()+1);
        assertEquals(InheritancePlan.Status.AVAILABLE,plan.checkInheritanceStatus(true,true,mature.unspent,false,mature.median));
        Transaction claim=InheritanceScript.spend(plan,mature.funding.getOutput(plan.getOutputIndex()),beneficiary.currentReceiveKey(),beneficiary.currentReceiveAddress(),2,false);
        assertEquals(plan.getUnlockTime(),claim.getLockTime());assertEquals(0xfffffffeL,claim.getInput(0).getSequenceNumber());
        server.income(claim,0);InheritanceOutputVerifier.Evidence pending=InheritanceOutputVerifier.verify(plan,server,server,headers(server),mature.median);assertFalse(pending.spendConfirmed);
        assertEquals(InheritancePlan.Status.SPEND_PENDING,plan.checkInheritanceStatus(true,true,pending.unspent,true,mature.median));
        server.income(claim,3);InheritanceOutputVerifier.Evidence spent=InheritanceOutputVerifier.verify(plan,server,server,headers(server),mature.median);
        assertFalse(spent.unspent);assertTrue(spent.spendConfirmed);assertEquals(claim.getTxId(),spent.spender.getTxId());
        assertEquals(InheritancePlan.Status.SPENT,plan.checkInheritanceStatus(true,true,false,false,mature.median));
    }
    @Test public void ownerBranchWorksBeforeAndAfterMaturityAndConfirmedCancellationRemainsSpent()throws Exception{
        for(boolean after:new boolean[]{false,true}){
            context();Wallet owner=WalletPolicy.createWallet(),beneficiary=WalletPolicy.createWallet();ElectrumSyncTest.Server server=new ElectrumSyncTest.Server();owner=fundOwner(owner,server);InheritancePlan plan=plan(owner,beneficiary);
            SendRequest funding=BitcoinPayments.prepare(owner,InheritanceScript.address(plan).toString(),"0.01","1");funding.signInputs=true;owner.signTransaction(funding);attach(plan,funding.tx);server.income(funding.tx,2);
            long median=plan.getUnlockTime()+(after?1:-1);InheritanceOutputVerifier.Evidence e=InheritanceOutputVerifier.verify(plan,server,server,headers(server),median);
            Transaction cancel=InheritanceScript.spend(plan,e.funding.getOutput(plan.getOutputIndex()),owner.currentReceiveKey(),owner.currentReceiveAddress(),1,true);
            assertEquals(0,cancel.getLockTime());server.income(cancel,3);
            InheritanceOutputVerifier.Evidence done=InheritanceOutputVerifier.verify(plan,server,server,headers(server),median);
            assertFalse(done.unspent);assertTrue(done.spendConfirmed);assertFalse(beneficiaryRole(plan).canRecover(plan,plan.checkInheritanceStatus(true,true,false,false,median)));
        }
    }
    private static Beneficiary beneficiaryRole(InheritancePlan plan){return plan.getBeneficiary();}
    @Test public void invalidPackageProofAndWitnessMismatchCannotGrantClaim()throws Exception{
        context();Wallet owner=WalletPolicy.createWallet(),beneficiary=WalletPolicy.createWallet();InheritancePlan plan=plan(owner,beneficiary);
        Transaction fund=new Transaction();fund.addInput(Sha256Hash.of(new byte[]{5}),0,ScriptBuilder.createEmpty());fund.addOutput(plan.getAmount(),InheritanceScript.outputScript(plan));plan.attachFunding(fund,0);
        JSONObject wrong=InheritanceRepository.encode(plan).put("network","wrong");assertThrows(IllegalArgumentException.class,()->InheritanceRepository.decode(wrong));
        JSONObject script=InheritanceRepository.encode(plan).put("redeemScript","00");assertThrows(IllegalArgumentException.class,()->InheritanceRepository.decode(script));
        ElectrumSyncTest.Server a=new ElectrumSyncTest.Server(),b=new ElectrumSyncTest.Server();a.income(fund,1);
        assertThrows(java.io.IOException.class,()->InheritanceOutputVerifier.verify(plan,a,b,headers(a),Long.MAX_VALUE));
        a.utxos.clear();assertThrows(java.io.IOException.class,()->InheritanceOutputVerifier.verify(plan,a,a,headers(a),Long.MAX_VALUE));
    }
    @Test public void minutesAndLegacyHoursAndDatesAreValidated(){
        assertEquals(60,InheritanceTime.seconds("1 minute",1_800_000_000));assertEquals(7200,InheritanceTime.seconds("2",1_800_000_000));assertEquals(86400,InheritanceTime.seconds("1 day",1_800_000_000));
        for(String v:new String[]{"0 minutes","-1 hours","9999999999999999 days","invalid","2000-01-01 00:00"})assertThrows(IllegalArgumentException.class,()->InheritanceTime.seconds(v,1_800_000_000));
    }
}
