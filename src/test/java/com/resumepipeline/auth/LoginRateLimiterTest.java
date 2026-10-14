package com.resumepipeline.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimiterTest {

    @Test
    void blocksAfterTenFailuresAndSuccessResets() {
        LoginRateLimiter l = new LoginRateLimiter();
        for (int i = 0; i < 9; i++) l.recordFailure("1.2.3.4");
        assertFalse(l.isBlocked("1.2.3.4"));
        l.recordFailure("1.2.3.4");
        assertTrue(l.isBlocked("1.2.3.4"));
        assertFalse(l.isBlocked("5.6.7.8"));
        l.recordSuccess("1.2.3.4");
        assertFalse(l.isBlocked("1.2.3.4"));
    }
}
