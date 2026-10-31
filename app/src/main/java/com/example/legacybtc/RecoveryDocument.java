package com.example.legacybtc;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Public, versioned recovery file. SHA256 detects corruption, not a malicious sender. */
public final class RecoveryDocument {
    public static final int MAX_BYTES=512_000;
    private RecoveryDocument(){}
    public static String export(InheritancePlan plan)throws Exception {
        if(!plan.isFunded())throw new IllegalArgumentException("Save funding before exporting recovery information.");
        JSONObject terms=InheritanceRepository.encode(plan);
        terms.remove("fundingRelay");terms.remove("spendHex");terms.remove("spendOwner");terms.remove("fundingAccepted");
        terms.put("unlockTimestamp",plan.getUnlockTime()).put("outputScript",Hex.encode(InheritanceScript.outputScript(plan).getProgram()));
        String payload=terms.toString();
        String text=new JSONObject().put("format","LegacyBTC-CLTV").put("version",2).put("payload",payload).put("sha256",digest(payload)).toString(2);
        parse(text);return text;
    }
    public static InheritancePlan parse(String text)throws Exception {
        if(text.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("Recovery file exceeds 512000 bytes.");
        JSONObject envelope=new JSONObject(text),data;
        if(envelope.optInt("version")==1 && !envelope.has("format"))data=envelope;
        else {
            if(!"LegacyBTC-CLTV".equals(envelope.getString("format")) || envelope.getInt("version")!=2)throw new IllegalArgumentException("Unsupported recovery-file format/version.");
            String payload=envelope.getString("payload");
            if(!digest(payload).equals(envelope.getString("sha256")))throw new IllegalArgumentException("Recovery-file integrity check failed.");
            data=new JSONObject(payload);
        }
        data.remove("fundingRelay");data.remove("spendHex");data.remove("spendOwner");data.remove("fundingAccepted");
        InheritancePlan plan=InheritanceRepository.decode(data);
        if(!plan.isFunded())throw new IllegalArgumentException("Recovery file has no funding outpoint.");
        if(data.has("unlockTimestamp") && data.getLong("unlockTimestamp")!=plan.getUnlockTime())throw new IllegalArgumentException("Unlock timestamp mismatch.");
        if(data.has("outputScript") && !data.getString("outputScript").equals(Hex.encode(InheritanceScript.outputScript(plan).getProgram())))throw new IllegalArgumentException("Output script mismatch.");
        return plan;
    }
    private static String digest(String text)throws Exception{return Hex.encode(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
}
