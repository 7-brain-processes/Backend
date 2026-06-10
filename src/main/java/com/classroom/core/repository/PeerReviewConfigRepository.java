package com.classroom.core.repository;

import com.classroom.core.model.PeerReviewConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PeerReviewConfigRepository extends JpaRepository<PeerReviewConfig, UUID> {

    Optional<PeerReviewConfig> findByCriterionId(UUID criterionId);
}
