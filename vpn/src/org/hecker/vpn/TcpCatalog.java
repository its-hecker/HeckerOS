// SPDX-License-Identifier: Apache-2.0
package org.hecker.vpn;

import android.net.Network;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.composer.Composer;
import org.yaml.snakeyaml.parser.ParserImpl;
import org.yaml.snakeyaml.reader.StreamReader;
import org.yaml.snakeyaml.resolver.Resolver;

/** Explicit public catalog; copy only sanitized HTTP outbound fields, never rules or listeners. */
final class TcpCatalog {
    static final String SOURCE = "https://raw.githubusercontent.com/iampedii/whitedns-sub/main/mihomo.yaml";
    static final int LIMIT = 2 * 1024 * 1024;
    static JSONArray parse(String yaml) throws Exception {
        if (yaml.length() > LIMIT) throw new IllegalArgumentException("TCP catalog is too large");
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(16); options.setCodePointLimit(LIMIT);
        Object document;
        try {
            // Load only scalar/list/map tags. Avoid Yaml's dumping/JavaBean path,
            // which otherwise brings desktop java.beans APIs into an Android APK.
            SafeConstructor constructor = new SafeConstructor(options);
            constructor.setComposer(new Composer(new ParserImpl(new StreamReader(yaml), options), new Resolver(), options));
            document = constructor.getSingleData(Object.class);
        }
        catch (RuntimeException bad) { throw new IllegalArgumentException("The TCP source returned an invalid catalog"); }
        if (!(document instanceof Map) || !(((Map<?, ?>) document).get("proxies") instanceof List))
            throw new IllegalArgumentException("The TCP source has no proxy list");
        List<?> rows = (List<?>) ((Map<?, ?>) document).get("proxies");
        if (rows.size() > 4096) throw new IllegalArgumentException("The TCP source returned too many entries");
        JSONArray safe = new JSONArray(); java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (Object raw : rows) {
            if (!(raw instanceof Map)) continue;
            Map<?, ?> row = (Map<?, ?>) raw;
            if (!"http".equals(row.get("type"))) continue;
            try {
                ProxyConfig config = ProxyConfig.catalog(row);
                String name = row.get("name") instanceof String ? (String) row.get("name") : "Public TCP proxy";
                // Provider labels can contain emoji; remove invisible direction/control characters.
                name = name.codePoints().filter(c -> !Character.isISOControl(c) && Character.getType(c) != Character.FORMAT)
                        .limit(80).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString().trim();
                if (name.isEmpty()) name = "Public TCP proxy";
                String json = config.json();
                String id = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
                if (ids.add(id)) safe.put(new JSONObject().put("id", id).put("name", name).put("tls", config.tls).put("config", json));
            } catch (IllegalArgumentException invalid) { /* Unsupported or insecure TLS entries are not offered. */ }
            if (safe.length() >= 256) break;
        }
        if (safe.length() == 0) throw new IllegalArgumentException("No supported TCP proxies were returned. HTTPS entries must pass certificate verification.");
        return safe;
    }
    static JSONArray fetch(Network network) throws Exception {
        if (network == null) throw new IllegalArgumentException("Connect to a network before refreshing TCP servers");
        HttpURLConnection connection = (HttpURLConnection) network.openConnection(new URL(SOURCE));
        connection.setConnectTimeout(12000); connection.setReadTimeout(12000); connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "HeckerVPN/1");
        try {
            if (connection.getResponseCode() != 200) throw new IllegalArgumentException("The free TCP source is unavailable. Retry later or import a proxy URL.");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count; long deadline = System.nanoTime() + 30_000_000_000L;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > LIMIT || System.nanoTime() > deadline) throw new IllegalArgumentException("TCP catalog download limit exceeded");
                    bytes.write(buffer, 0, count);
                }
                return parse(bytes.toString(StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); }
    }
}
