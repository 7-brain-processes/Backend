package com.classroom.core.repository;

import com.classroom.core.model.PeerReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PeerReviewRepository extends JpaRepository<PeerReview, UUID> {

    Optional<PeerReview> findByAssignmentId(UUID assignmentId);

    @Query("SELECT pr FROM PeerReview pr " +
           "WHERE pr.assignment.revieweeSolution.id = :solutionId " +
           "ORDER BY pr.submittedAt ASC")
    List<PeerReview> findByRevieweeSolutionIdOrderBySubmittedAt(@Param("solutionId") UUID solutionId);
}
