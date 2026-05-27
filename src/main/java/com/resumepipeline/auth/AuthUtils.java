package com.resumepipeline.auth;

import org.springframework.security.core.Authentication;

import java.util.UUID;

public final class AuthUtils {

    private AuthUtils() {}

    /** Extract the current user's UUID from the Spring Security Authentication principal. */
    public static UUID userId(Authentication auth) {
        return ((AppUserPrincipal) auth.getPrincipal()).getUserId();
    }
}
