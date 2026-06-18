package com.classroom.core.service;

import com.classroom.core.model.Criterion;
import com.classroom.core.model.CriterionType;
import com.classroom.core.model.PeerReviewConfig;
import com.classroom.core.model.Post;
import com.classroom.core.model.PostType;
import com.classroom.core.repository.CriterionRepository;
import com.classroom.core.repository.PeerReviewAssignmentRepository;
import com.classroom.core.repository.PeerReviewConfigRepository;
import com.classroom.core.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PeerReviewDeadlineJob {

    private final PeerReviewConfigRepository peerReviewConfigRepository;
    private final PeerReviewAssignmentRepository peerReviewAssignmentRepository;
    private final PeerReviewService peerReviewService;
    private final PostRepository postRepository;
    private final CriterionRepository criterionRepository;

    @Scheduled(fixedRateString = "${peer.review.job.interval-ms:300000}")
    @Transactional
    public void processDeadlines() {
        Instant now = Instant.now();
        List<PeerReviewConfig> configs = peerReviewConfigRepository.findAll();

        for (PeerReviewConfig config : configs) {
            try {
                if (config.getRound1ClosedAt() == null
                        && config.getFirstDeadline() != null
                        && now.isAfter(config.getFirstDeadline())) {
                    peerReviewService.closeRound1(config.getCriterion().getGradingConfig().getPost().getId());
                }

                if (config.getRound2ClosedAt() == null
                        && config.getSecondDeadline() != null
                        && now.isAfter(config.getSecondDeadline())) {
                    peerReviewService.closeRound2(config.getCriterion().getGradingConfig().getPost().getId());
                }
            } catch (Exception e) {
                log.error("Failed to process peer review deadlines for config {}", config.getId(), e);
            }
        }

        autoDistributeRound1(now);
    }

    private void autoDistributeRound1(Instant now) {
        List<Post> posts = postRepository.findAll().stream()
                .filter(p -> p.getType() == PostType.TASK)
                .filter(p -> p.getDeadline() != null)
                .filter(p -> now.isAfter(p.getDeadline()))
                .toList();

        for (Post post : posts) {
            try {
                List<Criterion> peerCriteria = criterionRepository
                        .findByGradingConfigPostIdAndType(post.getId(), CriterionType.PEER_REVIEW);
                if (peerCriteria.isEmpty()) {
                    continue;
                }

                PeerReviewConfig config = peerReviewConfigRepository
                        .findByCriterionId(peerCriteria.get(0).getId())
                        .orElse(null);
                if (config == null) {
                    continue;
                }

                boolean alreadyDistributed = peerReviewAssignmentRepository
                        .existsByPeerReviewConfigIdAndRound(config.getId(), 1);
                if (alreadyDistributed) {
                    continue;
                }

                peerReviewService.distributeRound1(post.getId());
            } catch (Exception e) {
                log.error("Failed to auto-distribute peer reviews for post {}", post.getId(), e);
            }
        }
    }
}
