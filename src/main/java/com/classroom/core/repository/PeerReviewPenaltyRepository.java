package com.classroom.core.repository;

import com.classroom.core.model.PeerReviewPenalty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PeerReviewPenaltyRepository extends JpaRepository<PeerReviewPenalty, UUID> {

    List<PeerReviewPenalty> findByPostIdAndUserId(UUID postId, UUID userId);

    List<PeerReviewPenalty> findByPostIdAndTeamId(UUID postId, UUID teamId);

    List<PeerReviewPenalty> findByPostId(UUID postId);
}
