package com.example.legacybtc;

import org.json.JSONArray;
import java.io.Closeable;
import java.util.List;

/** Injectable boundary; implementations never receive wallet credentials. */
interface ElectrumRpc extends Closeable {
    Object request(String method, JSONArray params) throws Exception;
    List<Object> batch(String method, List<JSONArray> params) throws Exception;
}
