package com.resumepipeline.bullet;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BulletMeasureDiagnosticRepository extends JpaRepository<BulletMeasureDiagnostic, UUID> {

    long countByMeasuredTrue();

    long countByMeasuredTrueAndAgreeFalse();

    List<BulletMeasureDiagnostic> findByMeasuredTrueAndAgreeFalseOrderByCreatedAtDesc(Limit limit);

    /** Fill-ratio sample for the histogram — capped since the admin panel only needs a shape,
     *  not every row ever written. */
    List<BulletMeasureDiagnostic> findByMeasuredTrueOrderByCreatedAtDesc(Limit limit);
}
