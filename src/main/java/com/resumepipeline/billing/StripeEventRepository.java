package com.resumepipeline.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StripeEventRepository extends JpaRepository<StripeEvent, String> {

    /** Records an event id; 0 means it was already there, so another delivery has it. */
    @Modifying
    @Query(value = "INSERT INTO stripe_event (id, type, processed_at) VALUES (:id, :type, now()) ON CONFLICT (id) DO NOTHING",
            nativeQuery = true)
    int claim(@Param("id") String id, @Param("type") String type);
}
