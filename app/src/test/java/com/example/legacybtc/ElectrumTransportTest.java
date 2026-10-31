package com.example.legacybtc;

import org.json.*;
import org.junit.Test;
import javax.net.ssl.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class ElectrumTransportTest {
    private SSLContext context() throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = getClass().getResourceAsStream("/electrum-localhost-test.p12")) { store.load(input,"test-only".toCharArray()); }
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); keys.init(store,"test-only".toCharArray());
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); trust.init(store);
        SSLContext context = SSLContext.getInstance("TLS"); context.init(keys.getKeyManagers(),trust.getTrustManagers(),null); return context;
    }
    private static class Fixture implements AutoCloseable {
        interface Response { String reply(String line) throws Exception; }
        final SSLServerSocket server;
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        Future<?> served;
        Fixture(SSLContext context) throws Exception { server = (SSLServerSocket)context.getServerSocketFactory().createServerSocket(0,1,java.net.InetAddress.getLoopbackAddress()); }
        void respond(Response response) {
            served = worker.submit(() -> {
                try (SSLSocket socket=(SSLSocket)server.accept()) {
                    socket.setSoTimeout(5000); socket.startHandshake();
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                    String line=input.readLine();
                    if (line!=null) { socket.getOutputStream().write((response.reply(line)+"\n").getBytes(StandardCharsets.UTF_8)); socket.getOutputStream().flush(); }
                } catch (Exception expectedOnRejectedConnection) { }
            });
        }
        public void close() throws Exception { server.close(); worker.shutdownNow(); worker.awaitTermination(5,TimeUnit.SECONDS); }
    }
    @Test public void trustedTlsRoutesOutOfOrderBatchAndIgnoresNotifications() throws Exception {
        SSLContext context=context();
        try (Fixture fixture=new Fixture(context)) {
            fixture.respond(line -> {
                JSONArray requests=new JSONArray(line), replies=new JSONArray();
                for(int i=requests.length()-1;i>=0;i--) replies.put(new JSONObject().put("id",requests.getJSONObject(i).getInt("id")).put("result",i));
                return new JSONObject().put("method","server.ping").put("params",new JSONArray())+"\n"+replies;
            });
            try (ElectrumClient client=new ElectrumClient("localhost",fixture.server.getLocalPort(),context.getSocketFactory())) {
                List<Object> result=client.batch("server.ping",Arrays.asList(new JSONArray(),new JSONArray()));
                assertEquals(0,((Number)result.get(0)).intValue()); assertEquals(1,((Number)result.get(1)).intValue());
            }
        }
    }
    @Test public void untrustedCertificateAndWrongHostnameFailClosed() throws Exception {
        SSLContext context=context();
        try(Fixture fixture=new Fixture(context)) {
            fixture.respond(line->"{}");
            assertThrows(IOException.class,()->new ElectrumClient("localhost",fixture.server.getLocalPort()));
        }
        try(Fixture fixture=new Fixture(context)) {
            fixture.respond(line->"{}");
            assertThrows(IOException.class,()->new ElectrumClient("127.0.0.1",fixture.server.getLocalPort(),context.getSocketFactory()));
        }
    }
    @Test public void wrongResponseIdAndRpcErrorsAreRejected() throws Exception {
        SSLContext context=context();
        for(String response:new String[]{"{\"id\":99,\"result\":true}","{\"id\":1,\"error\":{\"code\":1,\"message\":\"rejected\"}}"}) {
            try(Fixture fixture=new Fixture(context)) {
                fixture.respond(line->response);
                try(ElectrumClient client=new ElectrumClient("localhost",fixture.server.getLocalPort(),context.getSocketFactory())) {
                    assertThrows(IOException.class,()->client.request("server.ping",new JSONArray()));
                }
            }
        }
    }
}
