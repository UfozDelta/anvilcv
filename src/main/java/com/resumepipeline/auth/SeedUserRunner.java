package com.resumepipeline.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Ensures the seed user (fixed UUID 00000000-0000-0000-0000-000000000001) exists with
 * up-to-date credentials from config. Idempotent — safe to run on every startup.
 * The seed user owns all data rows created before multi-user migration (Phase 3).
 */
@Component
public class SeedUserRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedUserRunner.class);
    static final UUID SEED_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String email;
    private final String rawPassword;

    public SeedUserRunner(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${auth.seed.username}") String username,
            @Value("${auth.seed.email}") String email,
            @Value("${auth.seed.password}") String rawPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.email = email;
        this.rawPassword = rawPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        String hash = passwordEncoder.encode(rawPassword);
        if (rawPassword == null || rawPassword.isBlank()) {
            log.warn("auth.seed.password is blank — seed user password will be empty string. " +
                     "Set SEED_PASSWORD env var or activate the 'local' Spring profile.");
        }

        userRepository.findById(SEED_USER_ID).ifPresentOrElse(
                user -> {
                    user.setUsername(username);
                    user.setEmail(email);
                    user.setPasswordHash(hash);
                    userRepository.save(user);
                    log.info("Seed user ready — username: {}", username);
                },
                () -> {
                    AppUser u = new AppUser(username, email, hash);
                    userRepository.save(u);
                }
        );
    }
}
