package com.classroom.core.repository;

import com.classroom.core.model.PeerReviewAssignment;
import com.classroom.core.model.PeerReviewAssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PeerReviewAssignmentRepository extends JpaRepository<PeerReviewAssignment, UUID> {

    List<PeerReviewAssignment> findByReviewerUserIdAndPeerReviewConfigId(UUID reviewerUserId, UUID configId);

    List<PeerReviewAssignment> findByRevieweeSolutionId(UUID solutionId);

    List<PeerReviewAssignment> findByPeerReviewConfigId(UUID configId);

    @Query("SELECT a FROM PeerReviewAssignment a WHERE a.reviewerUser.id = :userId " +
           "AND a.peerReviewConfig.criterion.gradingConfig.post.id = :postId")
    List<PeerReviewAssignment> findByReviewerUserIdAndPostId(@Param("userId") UUID userId,
                                                             @Param("postId") UUID postId);

    @Query("SELECT COUNT(a) FROM PeerReviewAssignment a WHERE a.revieweeSolution.id = :solutionId " +
           "AND a.status = :status")
    long countByRevieweeSolutionIdAndStatus(@Param("solutionId") UUID solutionId,
                                            @Param("status") PeerReviewAssignmentStatus status);

    boolean existsByReviewerUserIdAndRevieweeSolutionId(UUID reviewerUserId, UUID revieweeSolutionId);
}
