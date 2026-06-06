package com.resumepipeline.api;

import com.resumepipeline.auth.AppUser;
import com.resumepipeline.auth.AppUserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * Builds an {@link Authentication} whose principal is a real {@link AppUserPrincipal},
 * which is what controllers expect ({@code AuthUtils.userId} casts the principal).
 * {@code @WithMockUser} cannot be used because its principal is a String.
 */
final class WebTestSecurity {

    private WebTestSecurity() {}

    /** A MockMvc post-processor that authenticates the request as the given user id. */
    static RequestPostProcessor user(UUID userId) {
        AppUser u = new AppUser("tester", "tester@x.com", "hash");
        setId(u, userId);
        AppUserPrincipal principal = new AppUserPrincipal(u);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, "hash", principal.getAuthorities());
        return authentication(auth);
    }

    private static void setId(AppUser u, UUID id) {
        try {
            Field f = AppUser.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set AppUser id", e);
        }
    }
}
