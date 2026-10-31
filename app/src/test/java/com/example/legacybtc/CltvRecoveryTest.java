package com.example.legacybtc;
import org.junit.Test;
import static org.junit.Assert.*;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.core.*;
import org.bitcoinj.base.*;
import org.bitcoinj.script.ScriptBuilder;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

public class CltvRecoveryTest {
    static final class Fixture {
        final Wallet owner,beneficiary;final InheritancePlan plan;final Transaction funding;
        Fixture(){
            Context.propagate(new Context());owner=WalletPolicy.createWallet();beneficiary=WalletPolicy.createWallet();
            org.bitcoinj.crypto.ECKey o=owner.currentReceiveKey(),b=beneficiary.currentReceiveKey();
            plan=new InheritancePlan(new Owner("Owner",Hex.encode(o.getPubKey()),o.toAddress(ScriptType.P2WPKH,WalletPolicy.NETWORK).toString()),
                new Beneficiary("Beneficiary",Hex.encode(b.getPubKey()),b.toAddress(ScriptType.P2WPKH,WalletPolicy.NETWORK).toString()),Coin.CENT,60,1800000000);
            funding=new Transaction();funding.addInput(Sha256Hash.of(new byte[]{22}),0,ScriptBuilder.createEmpty());
            funding.addOutput(plan.getAmount(),InheritanceScript.outputScript(plan));plan.attachFunding(funding,0);
        }
        InheritanceOutputVerifier.Evidence evidence(long median,boolean unspent,Transaction spender,boolean confirmedSpend)throws Exception {
            Block header=ElectrumHeaderStore.parameters().getGenesisBlock();
            return new InheritanceOutputVerifier.Evidence(funding,new StoredBlock(header,header.getWork(),1),101,median,unspent,spender,confirmedSpend);
        }
    }
    @Test public void beneficiaryOwnershipIsImmediateEvenOfflineAndSurvivesWalletSwitch()throws Exception {
        Fixture f=new Fixture();InheritancePlan imported=RecoveryDocument.parse(RecoveryDocument.export(f.plan));
        InheritanceState b=InheritanceState.evaluate(imported,f.beneficiary,null,false,"Verification unavailable");
        assertTrue(b.beneficiaryKey);assertEquals(InheritanceState.Truth.UNKNOWN,b.mature);assertEquals(InheritanceState.Truth.UNKNOWN,b.confirmed);assertFalse(b.claim);
        assertFalse(InheritanceState.evaluate(imported,f.owner,null,false,"offline").beneficiaryKey);
        assertTrue(InheritanceState.evaluate(imported,f.beneficiary,null,false,"offline").beneficiaryKey);
        assertTrue(b.conditions().contains("Beneficiary key matches this wallet: YES"));
    }
    @Test public void strictMtpBoundaryRefreshAndOwnerCancellationIndependence()throws Exception {
        Fixture f=new Fixture();
        InheritanceState at=InheritanceState.evaluate(f.plan,f.beneficiary,f.evidence(f.plan.getUnlockTime(),true,null,false),false,"");
        assertEquals(InheritanceState.Truth.NO,at.mature);assertFalse(at.claim);
        InheritanceState after=InheritanceState.evaluate(f.plan,f.beneficiary,f.evidence(f.plan.getUnlockTime()+1,true,null,false),false,"");
        assertTrue(after.claim);
        assertTrue(InheritanceState.evaluate(f.plan,f.owner,f.evidence(f.plan.getUnlockTime()-1,true,null,false),false,"").cancel);
        assertTrue(InheritanceState.evaluate(f.plan,f.owner,f.evidence(f.plan.getUnlockTime()+1,true,null,false),false,"").cancel);
    }
    @Test public void recoveryFileDiskRoundTripAndRepeatedExportPreserveExactOutpoint()throws Exception {
        Fixture f=new Fixture();String first=RecoveryDocument.export(f.plan);
        java.nio.file.Path file=java.nio.file.Files.createTempFile("legacybtc-public-recovery-",".json");
        try {
            try(java.io.OutputStream output=java.nio.file.Files.newOutputStream(file)){output.write(first.getBytes(StandardCharsets.UTF_8));}
            InheritancePlan read=RecoveryDocument.parse(new String(java.nio.file.Files.readAllBytes(file),StandardCharsets.UTF_8));
            assertEquals(f.funding.getTxId(),read.fundingTransaction().getTxId());assertEquals(f.plan.getOutputIndex(),read.getOutputIndex());
            assertArrayEquals(InheritanceScript.redeemScript(f.plan).getProgram(),InheritanceScript.redeemScript(read).getProgram());
            assertEquals(read.fundingTransaction().getTxId(),RecoveryDocument.parse(RecoveryDocument.export(read)).fundingTransaction().getTxId());
        }finally{java.nio.file.Files.delete(file);}
    }
    @Test public void rejectCorruptionWrongNetworkAndMismatchedScripts()throws Exception {
        Fixture f=new Fixture();JSONObject envelope=new JSONObject(RecoveryDocument.export(f.plan));
        envelope.put("payload",envelope.getString("payload")+" ");
        assertThrows(IllegalArgumentException.class,()->RecoveryDocument.parse(envelope.toString()));
        JSONObject legacy=InheritanceRepository.encode(f.plan).put("network","other");
        assertThrows(IllegalArgumentException.class,()->RecoveryDocument.parse(legacy.toString()));
        JSONObject script=InheritanceRepository.encode(f.plan).put("redeemScript","00");
        assertThrows(IllegalArgumentException.class,()->RecoveryDocument.parse(script.toString()));
    }
    @Test public void reviewIsUnsignedAndAuthenticatedSigningProducesValidBeneficiaryAndOwnerBranches()throws Exception {
        Fixture f=new Fixture();
        for(boolean owner:new boolean[]{false,true}){
            Wallet wallet=owner?f.owner:f.beneficiary;
            Transaction review=InheritanceSpender.prepare(f.plan,f.funding.getOutput(0),wallet.currentReceiveAddress(),2,owner);
            assertEquals(0,review.getInput(0).getScriptBytes().length);
            Transaction signed=InheritanceSpender.sign(f.plan,review,wallet.currentReceiveKey(),owner);
            InheritanceSpender.validate(f.plan,signed,owner);
            assertEquals(owner?0:f.plan.getUnlockTime(),signed.getLockTime());assertEquals(0xfffffffeL,signed.getInput(0).getSequenceNumber());
            assertTrue(signed.getOutput(0).isMine(wallet));
        }
    }
    @Test public void spendJournalSurvivesRestartPreventsReplacementAndIsNotExported()throws Exception {
        Fixture f=new Fixture();Transaction signed=InheritanceScript.spend(f.plan,f.funding.getOutput(0),f.beneficiary.currentReceiveKey(),f.beneficiary.currentReceiveAddress(),2,false);
        f.plan.attachSpend(signed,false);
        InheritancePlan reopened=InheritanceRepository.decode(new JSONObject(InheritanceRepository.encode(f.plan).toString()));
        assertArrayEquals(signed.serialize(),reopened.spendTransaction().serialize());
        Transaction other=InheritanceScript.spend(f.plan,f.funding.getOutput(0),f.owner.currentReceiveKey(),f.owner.currentReceiveAddress(),2,true);
        assertThrows(IllegalStateException.class,()->reopened.attachSpend(other,true));
        assertFalse(RecoveryDocument.parse(RecoveryDocument.export(reopened)).hasSpend());
        assertFalse(InheritanceState.evaluate(reopened,f.beneficiary,f.evidence(f.plan.getUnlockTime()+1,true,null,false),false,"").claim);
    }
    @Test public void confirmedCancellationAndClaimBlockFurtherSpending()throws Exception {
        for(boolean owner:new boolean[]{true,false}){
            Fixture f=new Fixture();Wallet signer=owner?f.owner:f.beneficiary;
            Transaction tx=InheritanceScript.spend(f.plan,f.funding.getOutput(0),signer.currentReceiveKey(),signer.currentReceiveAddress(),2,owner);
            InheritanceState state=InheritanceState.evaluate(f.plan,f.beneficiary,f.evidence(f.plan.getUnlockTime()+1,false,tx,true),false,"");
            assertFalse(state.claim);assertFalse(state.cancel);assertEquals(owner?InheritancePlan.Status.CANCELLED:InheritancePlan.Status.SPENT,state.status);
        }
    }
    @Test public void publicOnlyKeyIsNotSigningOwnershipAndFeesAndInputsAreBound()throws Exception {
        Fixture f=new Fixture();Wallet watch=WalletPolicy.createWallet();
        watch.importKey(org.bitcoinj.crypto.ECKey.fromPublicOnly(f.beneficiary.currentReceiveKey().getPubKey()));
        assertFalse(InheritanceState.controls(watch,f.plan.getBeneficiary().getPublicKey()));
        assertThrows(IllegalArgumentException.class,()->InheritanceSpender.prepare(f.plan,f.funding.getOutput(0),f.beneficiary.currentReceiveAddress(),100000,false));
        Transaction wrong=new Transaction();wrong.addInput(Sha256Hash.of(new byte[]{99}),0,ScriptBuilder.createEmpty());wrong.addOutput(f.plan.getAmount(),InheritanceScript.outputScript(f.plan));
        assertThrows(IllegalArgumentException.class,()->InheritanceSpender.prepare(f.plan,wrong.getOutput(0),f.beneficiary.currentReceiveAddress(),2,false));
    }
    @Test public void legacyVersionOneRecoveryStillRecognizesBeneficiary()throws Exception {
        Fixture f=new Fixture();InheritancePlan legacy=RecoveryDocument.parse(InheritanceRepository.encode(f.plan).toString());
        assertTrue(InheritanceState.controls(f.beneficiary,legacy.getBeneficiary().getPublicKey()));
    }
    @Test public void conflictingFundingNeverEnablesEitherBranch()throws Exception {
        Fixture f=new Fixture();f.owner.addWatchedAddress(InheritanceScript.address(f.plan));f.owner.commitTx(f.funding);
        f.funding.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.IN_CONFLICT);
        InheritanceState state=InheritanceState.evaluate(f.plan,f.owner,null,false,"verification unavailable");
        assertEquals(InheritancePlan.Status.CONFLICTING,state.status);assertFalse(state.cancel);assertFalse(state.claim);
        assertEquals(InheritanceState.Truth.UNKNOWN,state.unspent);
    }
    @Test public void beneficiaryCommitAndRepeatedProtobufReloadPreserveSpendAndConfirmedBalance()throws Exception {
        Fixture f=new Fixture();Wallet wallet=f.beneficiary;
        wallet.addWatchedAddress(InheritanceScript.address(f.plan),java.time.Instant.EPOCH);
        Block parent=new Block(1,Sha256Hash.ZERO_HASH,f.funding.getTxId(),java.time.Instant.ofEpochSecond(1800000000),0x1d00ffffL,0,null);
        wallet.receiveFromBlock(f.funding,new StoredBlock(parent,java.math.BigInteger.ONE,100),AbstractBlockChain.NewBlockType.BEST_CHAIN,0);
        Transaction claim=InheritanceScript.spend(f.plan,f.funding.getOutput(0),wallet.currentReceiveKey(),wallet.currentReceiveAddress(),2,false);
        claim.getConfidence().setSource(TransactionConfidence.Source.SELF);wallet.commitTx(claim);
        for(int round=0;round<3;round++){
            java.io.ByteArrayOutputStream stream=new java.io.ByteArrayOutputStream();wallet.saveToFileStream(stream);
            byte[] bytes=stream.toByteArray();
            try {wallet=Wallet.loadFromFileStream(new java.io.ByteArrayInputStream(bytes));}finally{java.util.Arrays.fill(bytes,(byte)0);}
            assertArrayEquals(claim.serialize(),wallet.getTransaction(claim.getTxId()).serialize());
            assertTrue(InheritanceState.controls(wallet,f.plan.getBeneficiary().getPublicKey()));
        }
        Block header=new Block(1,parent.getHash(),claim.getTxId(),java.time.Instant.ofEpochSecond(f.plan.getUnlockTime()+1),0x1d00ffffL,0,null);
        Transaction saved=wallet.getTransaction(claim.getTxId());
        wallet.receiveFromBlock(saved,new StoredBlock(header,java.math.BigInteger.TWO,101),AbstractBlockChain.NewBlockType.BEST_CHAIN,0);
        saved.getConfidence().setDepthInBlocks(1);
        assertEquals(claim.getOutput(0).getValue(),WalletPolicy.getBalance(wallet));
    }
}
