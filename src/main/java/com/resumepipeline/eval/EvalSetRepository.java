package com.resumepipeline.eval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvalSetRepository extends JpaRepository<EvalSet, UUID> {
    List<EvalSet> findAllByOrderByCreatedAtDesc();
    List<EvalSet> findByStatus(String status);
    Optional<EvalSet> findByLabel(String label);
}
