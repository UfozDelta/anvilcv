package com.resumepipeline.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByUsername(String username);
    Optional<AppUser> findByEmail(String email);
    Optional<AppUser> findByStripeSubscriptionId(String subscriptionId);
    Optional<AppUser> findByStripeCustomerId(String customerId);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
