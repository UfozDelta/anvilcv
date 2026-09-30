package com.resumepipeline.github;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/** Builds GitHub-shaped tarballs (top dir + pax long paths) for tests. */
final class TarGz {

    private TarGz() {}

    static byte[] of(Map<String, String> files) throws IOException {
        Map<String, byte[]> bytes = new LinkedHashMap<>();
        files.forEach((k, v) -> bytes.put(k, v.getBytes(StandardCharsets.UTF_8)));
        return ofBytes(bytes);
    }

    static byte[] ofBytes(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        header(raw, "pax_global_header", 0, 'g');
        header(raw, "me-app-abc1234/", 0, '5');
        for (var e : files.entrySet()) {
            String path = "me-app-abc1234/" + e.getKey();
            if (path.length() > 100) {
                byte[] pax = ("99 path=" + path + "\n").getBytes(StandardCharsets.UTF_8);
                header(raw, "PaxHeader", pax.length, 'x');
                body(raw, pax);
                path = "truncated-name";
            }
            header(raw, path, e.getValue().length, '0');
            body(raw, e.getValue());
        }
        raw.write(new byte[1024]);
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream z = new GZIPOutputStream(gz)) { z.write(raw.toByteArray()); }
        return gz.toByteArray();
    }

    private static void header(ByteArrayOutputStream raw, String name, int size, char type) throws IOException {
        byte[] h = new byte[512];
        byte[] n = name.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(n, 0, h, 0, Math.min(100, n.length));
        byte[] sz = String.format("%011o", size).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(sz, 0, h, 124, sz.length);
        h[156] = (byte) type;
        raw.write(h);
    }

    private static void body(ByteArrayOutputStream raw, byte[] data) throws IOException {
        raw.write(data);
        raw.write(new byte[(512 - data.length % 512) % 512]);
    }
}
