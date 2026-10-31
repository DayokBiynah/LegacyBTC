package com.example.legacybtc;
import android.content.Context;
import android.app.job.JobScheduler;
import android.app.job.JobInfo;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONObject;

/** Read-only quarantine: never creates, signs, schedules, relays or rewrites old payment records. */
final class LegacyScheduledPayments {
    static void disableJobs(Context context) {
        JobScheduler jobs=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(jobs!=null)for(JobInfo job:jobs.getAllPendingJobs())
            if(job.getService().getPackageName().equals(context.getPackageName()) && job.getService().getClassName().startsWith("androidx.work."))jobs.cancel(job.getId());
    }
    static java.util.List<JSONObject> records(Context context,String wallet)throws Exception {
        String prefix="automatic_"+WalletPolicy.NETWORK_ID+"_";Set<String> names=new TreeSet<>();
        String[] files=context.getFilesDir().list();if(files!=null)for(String name:files){
            if(name.endsWith(".bak"))name=name.substring(0,name.length()-4);
            if(name.startsWith(prefix) && name.endsWith(".enc"))names.add(name);
        }
        java.util.List<JSONObject> result=new ArrayList<>();
        for(String name:names){byte[] bytes=WalletStorageManager.readEncrypted(context,name);
            try {
                JSONObject record=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
                if(record.getInt("version")!=1 || !WalletPolicy.NETWORK_ID.equals(record.getString("network")))throw new IllegalStateException("Unknown retired schedule format; originals preserved.");
                if(wallet.equals(record.getString("wallet")))result.add(record);
            }finally{Arrays.fill(bytes,(byte)0);}
        }
        return result;
    }
    static void checkBeforeSpending(Context context,BitcoinWallet wallet)throws Exception {
        for(JSONObject record:records(context,wallet.getFileName())){
            String hex=record.optString("txHex","");if(hex.isEmpty())continue;
            if(!WalletPolicy.walletIdentity(wallet.getWallet()).equals(record.getString("identity")) || hex.length()>2000000)throw new IllegalStateException("Retired signed journal identity/size invalid.");
            java.nio.ByteBuffer bytes=java.nio.ByteBuffer.wrap(Hex.decode(hex));
            org.bitcoinj.core.Transaction tx=org.bitcoinj.core.Transaction.read(bytes);
            if(bytes.hasRemaining() || !tx.getTxId().toString().equals(record.getString("txid")))throw new IllegalStateException("Retired signed journal TXID invalid.");
            org.bitcoinj.core.Transaction saved=wallet.getWallet().getTransaction(tx.getTxId());
            if(saved==null || !Arrays.equals(saved.serialize(),tx.serialize()))throw new IllegalStateException("Retired automatic payment has signed TXID "+tx.getTxId()+" outside wallet history. Journal retained; resolve possible broadcast before spending.");
        }
    }
    static String summary(Context context,String wallet)throws Exception {
        java.util.List<JSONObject> values=records(context,wallet);if(values.isEmpty())return "";
        StringBuilder text=new StringBuilder("Retired automatic plans: DISABLED. Encrypted originals retained.\n");
        for(JSONObject v:values)text.append(v.optString("id")).append(" — ").append(v.optString("state")).append("; TXID: ").append(v.optString("txid","none")).append("\n");
        return text.toString();
    }
}
