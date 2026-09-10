package com.resumepipeline.bullet;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BulletMeasureDiagnosticRepository extends JpaRepository<BulletMeasureDiagnostic, UUID> {

    long countByMeasuredTrue();

    long countByMeasuredTrueAndAgreeFalse();

    /** Most recent measured bullets, flagged or not — the admin panel's "recent bullets" list. */
    List<BulletMeasureDiagnostic> findByMeasuredTrueOrderByCreatedAtDesc(Limit limit);
}
