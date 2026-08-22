package com.resumepipeline.application;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutcomeHistoryRepository extends JpaRepository<OutcomeHistory, UUID> {

    @Query("select h from OutcomeHistory h where h.applicationId in " +
           "(select a.id from Application a where a.userId = :userId) order by h.applicationId, h.changedAt")
    List<OutcomeHistory> findAllByUserId(@Param("userId") UUID userId);
}
