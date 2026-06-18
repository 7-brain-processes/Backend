package com.classroom.core.service;

import com.classroom.core.model.*;
import com.classroom.core.repository.CriterionRepository;
import com.classroom.core.repository.PeerReviewAssignmentRepository;
import com.classroom.core.repository.PeerReviewConfigRepository;
import com.classroom.core.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PeerReviewDeadlineJobTest {

    @Mock
    private PeerReviewConfigRepository peerReviewConfigRepository;
    @Mock
    private PeerReviewAssignmentRepository peerReviewAssignmentRepository;
    @Mock
    private PeerReviewService peerReviewService;
    @Mock
    private PostRepository postRepository;
    @Mock
    private CriterionRepository criterionRepository;

    @InjectMocks
    private PeerReviewDeadlineJob job;

    @BeforeEach
    void setUp() {
        when(postRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void shouldCloseRound1WhenDeadlinePassed() {
        UUID postId = UUID.randomUUID();
        PeerReviewConfig config = configWithDeadlines(
                Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(3600));
        config.getCriterion().getGradingConfig().getPost().setId(postId);

        when(peerReviewConfigRepository.findAll()).thenReturn(List.of(config));

        job.processDeadlines();

        verify(peerReviewService).closeRound1(postId);
        verify(peerReviewService, never()).closeRound2(postId);
    }

    @Test
    void shouldCloseRound2WhenDeadlinePassed() {
        UUID postId = UUID.randomUUID();
        PeerReviewConfig config = configWithDeadlines(
                Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(3600));
        config.setRound1ClosedAt(Instant.now().minusSeconds(7200));
        config.getCriterion().getGradingConfig().getPost().setId(postId);

        when(peerReviewConfigRepository.findAll()).thenReturn(List.of(config));

        job.processDeadlines();

        verify(peerReviewService).closeRound2(postId);
    }

    @Test
    void shouldSkipAlreadyClosedRounds() {
        UUID postId = UUID.randomUUID();
        PeerReviewConfig config = configWithDeadlines(
                Instant.now().minusSeconds(3600),
                Instant.now().minusSeconds(1800));
        config.setRound1ClosedAt(Instant.now().minusSeconds(3500));
        config.setRound2ClosedAt(Instant.now().minusSeconds(1700));
        config.getCriterion().getGradingConfig().getPost().setId(postId);

        when(peerReviewConfigRepository.findAll()).thenReturn(List.of(config));

        job.processDeadlines();

        verify(peerReviewService, never()).closeRound1(postId);
        verify(peerReviewService, never()).closeRound2(postId);
    }

    @Test
    void shouldAutoDistributeRound1WhenPostDeadlinePassed() {
        UUID postId = UUID.randomUUID();
        UUID criterionId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();

        Post post = Post.builder().id(postId).type(PostType.TASK)
                .deadline(Instant.now().minusSeconds(3600)).build();
        GradingConfig gradingConfig = GradingConfig.builder().post(post).build();
        Criterion criterion = Criterion.builder().id(criterionId).type(CriterionType.PEER_REVIEW)
                .gradingConfig(gradingConfig).build();
        PeerReviewConfig config = PeerReviewConfig.builder()
                .id(configId)
                .criterion(criterion)
                .firstDeadline(Instant.now().plusSeconds(3600))
                .secondDeadline(Instant.now().plusSeconds(7200))
                .build();

        when(peerReviewConfigRepository.findAll()).thenReturn(List.of());
        when(postRepository.findAll()).thenReturn(List.of(post));
        when(criterionRepository.findByGradingConfigPostIdAndType(postId, CriterionType.PEER_REVIEW))
                .thenReturn(List.of(criterion));
        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(peerReviewAssignmentRepository.existsByPeerReviewConfigIdAndRound(configId, 1))
                .thenReturn(false);

        job.processDeadlines();

        verify(peerReviewService).distributeRound1(postId);
    }

    @Test
    void shouldSkipAutoDistributionWhenNoPeerReviewCriterion() {
        UUID postId = UUID.randomUUID();
        Post post = Post.builder().id(postId).type(PostType.TASK)
                .deadline(Instant.now().minusSeconds(3600)).build();

        when(peerReviewConfigRepository.findAll()).thenReturn(List.of());
        when(postRepository.findAll()).thenReturn(List.of(post));
        when(criterionRepository.findByGradingConfigPostIdAndType(postId, CriterionType.PEER_REVIEW))
                .thenReturn(List.of());

        job.processDeadlines();

        verify(peerReviewService, never()).distributeRound1(any());
    }

    @Test
    void shouldSkipAutoDistributionWhenAlreadyDistributed() {
        UUID postId = UUID.randomUUID();
        UUID criterionId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();

        Post post = Post.builder().id(postId).type(PostType.TASK)
                .deadline(Instant.now().minusSeconds(3600)).build();
        GradingConfig gradingConfig = GradingConfig.builder().post(post).build();
        Criterion criterion = Criterion.builder().id(criterionId).type(CriterionType.PEER_REVIEW)
                .gradingConfig(gradingConfig).build();
        PeerReviewConfig config = PeerReviewConfig.builder()
                .id(configId)
                .criterion(criterion)
                .firstDeadline(Instant.now().plusSeconds(3600))
                .secondDeadline(Instant.now().plusSeconds(7200))
                .build();

        when(peerReviewConfigRepository.findAll()).thenReturn(List.of());
        when(postRepository.findAll()).thenReturn(List.of(post));
        when(criterionRepository.findByGradingConfigPostIdAndType(postId, CriterionType.PEER_REVIEW))
                .thenReturn(List.of(criterion));
        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(peerReviewAssignmentRepository.existsByPeerReviewConfigIdAndRound(configId, 1))
                .thenReturn(true);

        job.processDeadlines();

        verify(peerReviewService, never()).distributeRound1(any());
    }

    @Test
    void shouldSkipAutoDistributionForMaterialPosts() {
        UUID postId = UUID.randomUUID();
        Post post = Post.builder().id(postId).type(PostType.MATERIAL)
                .deadline(Instant.now().minusSeconds(3600)).build();

        when(peerReviewConfigRepository.findAll()).thenReturn(List.of());
        when(postRepository.findAll()).thenReturn(List.of(post));

        job.processDeadlines();

        verify(peerReviewService, never()).distributeRound1(any());
        verify(criterionRepository, never()).findByGradingConfigPostIdAndType(any(), any());
    }

    private PeerReviewConfig configWithDeadlines(Instant first, Instant second) {
        Post post = Post.builder().id(UUID.randomUUID()).build();
        GradingConfig gradingConfig = GradingConfig.builder().post(post).build();
        return PeerReviewConfig.builder()
                .id(UUID.randomUUID())
                .criterion(Criterion.builder()
                        .id(UUID.randomUUID())
                        .gradingConfig(gradingConfig)
                        .build())
                .firstDeadline(first)
                .secondDeadline(second)
                .build();
    }
}
