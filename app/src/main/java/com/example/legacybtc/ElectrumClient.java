package com.example.legacybtc;

import org.bitcoinj.base.Address;
import org.bitcoinj.script.ScriptBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

/** TLS JSON-RPC with bounded messages and an overall slow-response deadline. */
final class ElectrumClient implements ElectrumRpc {
    static final class TransportException extends IOException {
        TransportException(IOException cause) { super("Electrum RPC I/O failed", cause); }
    }
    private static final int TIMEOUT_MS = 12000, MAX_LINE = 4_000_000;
    private static final ScheduledExecutorService DEADLINES = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "electrum-deadlines"); thread.setDaemon(true); return thread;
    });
    private static final ExecutorService RESOLVER = new ThreadPoolExecutor(0,4,30,TimeUnit.SECONDS,
            new SynchronousQueue<>(),r -> { Thread t=new Thread(r,"electrum-dns"); t.setDaemon(true); return t; });
    private final SSLSocket socket;
    private final BufferedReader input;
    private final BufferedWriter output;
    private int nextId = 1;
    ElectrumClient(String hostname, int port) throws IOException {
        this(hostname,port,(SSLSocketFactory)SSLSocketFactory.getDefault());
    }
    ElectrumClient(String hostname, int port, SSLSocketFactory factory) throws IOException {
        if (hostname == null || !hostname.matches("[A-Za-z0-9.-]{1,253}") || port < 1 || port > 65535)
            throw new IllegalArgumentException("Invalid Electrum endpoint");
        Socket raw = new Socket();
        ScheduledFuture<?> deadline = DEADLINES.schedule(() -> closeQuietly(raw), 20, TimeUnit.SECONDS);
        SSLSocket candidate = null;
        try {
            Future<InetAddress[]> resolution = RESOLVER.submit(() -> InetAddress.getAllByName(hostname));
            InetAddress[] addresses;
            try { addresses = resolution.get(8,TimeUnit.SECONDS); }
            catch (Exception error) { resolution.cancel(true); throw new IOException("Electrum DNS lookup failed or timed out",error); }
            if (addresses.length == 0) throw new UnknownHostException(hostname);
            // Prefer IPv4 on mobile networks without a usable IPv6 route.
            InetAddress address = addresses[0];
            for (InetAddress candidateAddress : addresses) if (candidateAddress instanceof Inet4Address) { address=candidateAddress; break; }
            raw.connect(new InetSocketAddress(address, port), TIMEOUT_MS);
            candidate = (SSLSocket) factory.createSocket(raw, hostname, port, true);
            candidate.setSoTimeout(TIMEOUT_MS);
            SSLParameters parameters = candidate.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            List<String> protocols = new ArrayList<>();
            for (String protocol : candidate.getSupportedProtocols())
                if (protocol.equals("TLSv1.2") || protocol.equals("TLSv1.3")) protocols.add(protocol);
            parameters.setProtocols(protocols.toArray(new String[0]));
            candidate.setSSLParameters(parameters); candidate.startHandshake();
            socket = candidate;
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException error) {
            closeQuietly(candidate); closeQuietly(raw); throw error;
        } finally { deadline.cancel(false); }
    }
    @Override public synchronized Object request(String method, JSONArray params) throws Exception {
        return exchange(method, Collections.singletonList(params), false).get(0);
    }
    @Override public synchronized List<Object> batch(String method, List<JSONArray> params) throws Exception {
        if (params.isEmpty()) return Collections.emptyList();
        if (params.size() > 50) throw new IllegalArgumentException("At most 50 requests per batch");
        return exchange(method, params, true);
    }
    private List<Object> exchange(String method, List<JSONArray> params, boolean batch) throws Exception {
        if (!method.matches("[a-z_.]+")) throw new IllegalArgumentException("Invalid RPC method");
        JSONArray requests = new JSONArray();
        Map<Integer, Integer> positions = new HashMap<>();
        for (JSONArray arguments : params) {
            int id = nextId++; positions.put(id, positions.size());
            requests.put(new JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", method).put("params", arguments));
        }
        ScheduledFuture<?> deadline = DEADLINES.schedule(() -> closeQuietly(socket), 25, TimeUnit.SECONDS);
        try {
            output.write(batch ? requests.toString() : requests.getJSONObject(0).toString()); output.write('\n'); output.flush();
            Object[] results = new Object[params.size()];
            int remaining = results.length, notifications = 0;
            while (remaining > 0) {
                String line = readLineLimited();
                if (line == null) throw new EOFException("Electrum disconnected");
                JSONArray replies = line.trim().startsWith("[") ? new JSONArray(line) : new JSONArray().put(new JSONObject(line));
                for (int i = 0; i < replies.length(); i++) {
                    JSONObject reply = replies.getJSONObject(i);
                    if (!reply.has("id") || reply.isNull("id")) {
                        if (++notifications > 100) throw new IOException("Excessive Electrum notifications");
                        continue;
                    }
                    Integer position = positions.remove(reply.getInt("id"));
                    if (position == null) throw new IOException("Unexpected or duplicate Electrum response ID");
                    if (reply.has("error") && !reply.isNull("error")) throw new IOException("Electrum rejected " + method + ": " + reply.get("error"));
                    if (!reply.has("result")) throw new IOException("Missing Electrum result");
                    results[position] = reply.get("result"); remaining--;
                }
            }
            return Arrays.asList(results);
        } catch (IOException error) { throw new TransportException(error); }
        finally { deadline.cancel(false); }
    }
    JSONObject getBalance(String hash) throws Exception { return (JSONObject) request("blockchain.scripthash.get_balance", new JSONArray().put(hash)); }
    JSONArray getHistory(String hash) throws Exception { return (JSONArray) request("blockchain.scripthash.get_history", new JSONArray().put(hash)); }
    JSONArray listUnspent(String hash) throws Exception { return (JSONArray) request("blockchain.scripthash.listunspent", new JSONArray().put(hash)); }
    String getTransaction(String txid) throws Exception { return (String) request("blockchain.transaction.get", new JSONArray().put(txid).put(false)); }
    static String scriptHash(Address address) { return scriptHash(ScriptBuilder.createOutputScript(address).getProgram()); }
    static String scriptHash(byte[] script) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(script);
            for (int i = 0; i < hash.length / 2; i++) { byte b = hash[i]; hash[i] = hash[hash.length-1-i]; hash[hash.length-1-i] = b; }
            return Hex.encode(hash);
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private String readLineLimited() throws IOException {
        StringBuilder buffer = new StringBuilder(); int c;
        while ((c = input.read()) != -1) {
            if (c == '\n') return buffer.toString();
            if (buffer.length() >= MAX_LINE) throw new IOException("Oversized Electrum reply");
            buffer.append((char)c);
        }
        return buffer.length() == 0 ? null : buffer.toString();
    }
    private static void closeQuietly(Closeable connection) { if (connection != null) try { connection.close(); } catch (IOException ignored) { } }
    @Override public void close() throws IOException { socket.close(); }
}


