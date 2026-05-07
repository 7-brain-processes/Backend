package com.classroom.core.repository;

import com.classroom.core.model.Criterion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CriterionRepository extends JpaRepository<Criterion, UUID> {

    List<Criterion> findByGradingConfigIdOrderBySortOrderAsc(UUID gradingConfigId);
}
