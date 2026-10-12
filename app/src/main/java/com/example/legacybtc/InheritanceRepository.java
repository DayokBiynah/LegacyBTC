package com.example.legacybtc;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.bitcoinj.base.Coin;
import org.bitcoinj.core.Transaction;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.util.ArrayList;

/**
 * I store each wallet's plans in an encrypted JSON array and export only public recovery
 * information. The package includes the exact funding transaction, output index, identities,
 * amount, and deadline needed to reconstruct the redeem script. A wallet mnemonic does not
 * recreate these custom terms. I validate the package network and funding output before
 * accepting it; imported information is not proof of blockchain confirmation.
 */
public final class InheritanceRepository {
    private final Context context;
    public InheritanceRepository(Context context) { this.context = context.getApplicationContext(); }
    private String file(String walletFile) {
        WalletStorageManager.validateFileName(walletFile);
        return "inheritance_" + walletFile;
    }
    /**
     * I load this wallet's encrypted JSON plan collection through the repository and decode
     * each entry into a validated InheritancePlan. An absent file returns an empty ArrayList.
     * Corrupt or wrong-network data raises an error rather than displaying invented recovery
     * eligibility.
     * @param walletFile String supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return the wallet-specific ArrayList of decoded validated plans
     */
    public ArrayList<InheritancePlan> getPlans(String walletFile) throws Exception {
        ArrayList<InheritancePlan> plans = new ArrayList<>();
        String file = file(walletFile);
        if (!new File(context.getFilesDir(), file).exists() && !new File(context.getFilesDir(), file + ".bak").exists()) return plans;
        JSONArray array = new JSONArray(new String(WalletStorageManager.readEncrypted(context, file), StandardCharsets.UTF_8));
        for (int i = 0; i < array.length(); i++) plans.add(decode(array.getJSONObject(i)));
        return plans;
    }
    /**
     * I loop over the wallet's decoded plans and compare each ID with the selected String. A
     * match returns the InheritancePlan; no match raises IllegalArgumentException so callers
     * cannot accidentally use another plan.
     * @param walletFile String supplied by the calling workflow as described above
     * @param id String supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return the saved plan matching the requested ID
     */
    public InheritancePlan find(String walletFile, String id) throws Exception {
        for (InheritancePlan plan : getPlans(walletFile)) if (plan.getId().equals(id)) return plan;
        throw new IllegalArgumentException("Inheritance plan not found.");
    }
    /**
     * I load the encrypted collection, loop for an existing plan ID, and replace that entry or
     * append a new plan. I encode the complete collection as JSON and pass its UTF-8 bytes to
     * atomic encrypted storage. Success preserves all other plans; failure propagates without
     * reporting a saved draft.
     * @param walletFile String supplied by the calling workflow as described above
     * @param replacement InheritancePlan supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     */
    public void save(String walletFile, InheritancePlan replacement) throws Exception {
        ArrayList<InheritancePlan> plans = getPlans(walletFile);
        boolean found = false;
        for (int i = 0; i < plans.size(); i++) {
            if (plans.get(i).getId().equals(replacement.getId())) { plans.set(i, replacement); found = true; break; }
        }
        if (!found) plans.add(replacement);
        JSONArray array = new JSONArray();
        for (InheritancePlan plan : plans) array.put(encode(plan));
        WalletStorageManager.writeEncrypted(context, file(walletFile), array.toString().getBytes(StandardCharsets.UTF_8));
    }
    /**
     * I create a JSONObject containing version, build network, plan ID, public identities,
     * exact satoshis, timestamps, funding bytes, output index, and draft cancellation state.
     * The result is encrypted locally or exported as public recovery information; it contains
     * no mnemonic or private key.
     * @param plan InheritancePlan supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return public hexadecimal text or a JSONObject representation as described above
     */
    public static JSONObject encode(InheritancePlan plan) throws Exception {
        JSONObject data = new JSONObject();
        data.put("version", 1).put("network", WalletPolicy.NETWORK_ID).put("id", plan.getId());
        data.put("owner", encodeUser(plan.getOwner())).put("beneficiary", encodeUser(plan.getBeneficiary()));
        data.put("satoshis", plan.getAmount().value).put("createdAt", plan.getCreatedAt())
                .put("periodSeconds", plan.getPeriodSeconds()).put("fundingHex", plan.getFundingHex())
                .put("outputIndex", plan.getOutputIndex()).put("cancelledDraft", plan.isCancelled());
        return data;
    }
    /**
     * I serialize only the User name, public key, and network address into a JSONObject for
     * the recovery package. The common User type lets both subclasses share the
     * representation. No ECKey private material or recovery phrase is included.
     * @param user User supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return a JSONObject containing only the name, public key and address
     */
    private static JSONObject encodeUser(User user) throws Exception {
        return new JSONObject().put("name", user.getName()).put("publicKey", user.getPublicKey()).put("address", user.getAddress());
    }
    /**
     * I receive a JSONObject from encrypted local data or an imported package. I validate
     * version and network before constructing the public identities and plan, then parse
     * bounded funding bytes and attach only an exact matching output. A malformed script,
     * amount, key, or timelock raises an error. Confirmation still requires independent chain
     * evidence.
     * @param data JSONObject supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return validated decoded bytes or an InheritancePlan as described above
     */
    public static InheritancePlan decode(JSONObject data) throws Exception {
        if (data.getInt("version") != 1 || !data.getString("network").equals(WalletPolicy.NETWORK_ID))
            throw new IllegalArgumentException("Use a LegacyBTC " + WalletPolicy.NETWORK_LABEL + " recovery package.");
        JSONObject owner = data.getJSONObject("owner"), beneficiary = data.getJSONObject("beneficiary");
        InheritancePlan plan = new InheritancePlan(data.getString("id"),
                new Owner(owner.getString("name"), owner.getString("publicKey"), owner.getString("address")),
                new Beneficiary(beneficiary.getString("name"), beneficiary.getString("publicKey"), beneficiary.getString("address")),
                Coin.valueOf(data.getLong("satoshis")), data.getLong("periodSeconds"), data.getLong("createdAt"));
        String funding = data.optString("fundingHex", "");
        if (!funding.isEmpty()) {
            if (funding.length() > 400_000) throw new IllegalArgumentException("Recovery transaction is too large.");
            Transaction tx = Transaction.read(ByteBuffer.wrap(Hex.decode(funding)));
            plan.attachFunding(tx, data.getInt("outputIndex"));
        } else if (data.optBoolean("cancelledDraft")) plan.cancelDraft();
        return plan;
    }
}
