package com.resumepipeline.jd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JdFetcherTest {

    @Test
    void rejectsNonHttpSchemes() {
        assertThrows(IllegalArgumentException.class, () -> JdFetcher.requirePublicHttp("file:///etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> JdFetcher.requirePublicHttp("ftp://8.8.8.8/x"));
        assertThrows(IllegalArgumentException.class, () -> JdFetcher.requirePublicHttp("not a url"));
    }

    @Test
    void rejectsLoopbackPrivateAndLinkLocalHosts() {
        for (String u : new String[] {
                "http://localhost/x", "http://127.0.0.1:8080/", "http://0.0.0.0/",
                "http://169.254.169.254/latest/meta-data", "http://10.0.0.5/", "http://100.64.0.1/", "http://192.168.1.1/",
                "http://172.16.0.1/", "http://[::1]/", "http://[fd00::1]/"}) {
            assertThrows(IllegalArgumentException.class, () -> JdFetcher.requirePublicHttp(u), u);
        }
    }

    @Test
    void allowsPublicIpLiteral() {
        assertDoesNotThrow(() -> JdFetcher.requirePublicHttp("https://8.8.8.8/job"));
    }
}
