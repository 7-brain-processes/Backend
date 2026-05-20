package com.classroom.core.repository;

import com.classroom.core.model.VersionedCriterion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VersionedCriterionRepository extends JpaRepository<VersionedCriterion, UUID> {

    List<VersionedCriterion> findByConfigVersionIdOrderBySortOrderAsc(UUID configVersionId);
}
