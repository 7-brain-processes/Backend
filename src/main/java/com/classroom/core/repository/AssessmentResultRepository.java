package com.classroom.core.repository;

import com.classroom.core.model.AssessmentResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentResultRepository extends JpaRepository<AssessmentResult, UUID> {

    Optional<AssessmentResult> findBySolutionId(UUID solutionId);

    List<AssessmentResult> findBySolutionPostId(UUID postId);
}
