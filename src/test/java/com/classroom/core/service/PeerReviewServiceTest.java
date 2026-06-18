package com.classroom.core.service;

import com.classroom.core.dto.peerreview.PeerReviewAssignmentDto;
import com.classroom.core.dto.peerreview.PeerReviewConfigRequest;
import com.classroom.core.dto.peerreview.SubmitPeerReviewRequest;
import com.classroom.core.exception.BadRequestException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.*;
import com.classroom.core.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PeerReviewServiceTest {

    @Mock
    private PeerReviewConfigRepository peerReviewConfigRepository;
    @Mock
    private PeerReviewAssignmentRepository peerReviewAssignmentRepository;
    @Mock
    private PeerReviewRepository peerReviewRepository;
    @Mock
    private PeerReviewPenaltyRepository peerReviewPenaltyRepository;
    @Mock
    private CriterionRepository criterionRepository;
    @Mock
    private SolutionRepository solutionRepository;
    @Mock
    private CourseMemberRepository courseMemberRepository;
    @Mock
    private AssessmentResultRepository assessmentResultRepository;
    @Mock
    private GradingConfigRepository gradingConfigRepository;
    @Mock
    private GradingDtoMapper gradingDtoMapper;
    @Mock
    private GradingConfigVersionService gradingConfigVersionService;
    @Mock
    private GradingGuard guard;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PeerReviewService peerReviewService;

    private final UUID courseId = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID configId = UUID.randomUUID();
    private final UUID criterionId = UUID.randomUUID();

    @Test
    void createOrUpdateConfig_shouldRejectNonPeerReviewCriterion() {
        Criterion criterion = Criterion.builder()
                .id(criterionId)
                .type(CriterionType.POINTS)
                .build();

        PeerReviewConfigRequest request = configRequest();

        assertThatThrownBy(() -> peerReviewService.createOrUpdateConfig(criterion, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("only valid for PEER_REVIEW criteria");
    }

    @Test
    void createOrUpdateConfig_shouldRejectDeadlinesOutOfOrder() {
        Criterion criterion = Criterion.builder()
                .id(criterionId)
                .type(CriterionType.PEER_REVIEW)
                .build();

        PeerReviewConfigRequest request = PeerReviewConfigRequest.builder()
                .reviewersCount(1)
                .scoringStrategy(PeerReviewScoringStrategy.AVERAGE)
                .firstDeadline(Instant.now().plusSeconds(7200))
                .secondDeadline(Instant.now().plusSeconds(3600))
                .build();

        assertThatThrownBy(() -> peerReviewService.createOrUpdateConfig(criterion, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("firstDeadline must be before secondDeadline");
    }

    @Test
    void createOrUpdateConfig_shouldCreateNewConfig() {
        Criterion criterion = Criterion.builder()
                .id(criterionId)
                .type(CriterionType.PEER_REVIEW)
                .build();

        PeerReviewConfigRequest request = configRequest();

        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.empty());
        when(peerReviewConfigRepository.save(any(PeerReviewConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        peerReviewService.createOrUpdateConfig(criterion, request);

        verify(peerReviewConfigRepository).save(argThat(config ->
                config.getCriterion().equals(criterion)
                        && config.getReviewersCount().equals(request.getReviewersCount())
                        && config.getScoringStrategy().equals(request.getScoringStrategy())
                        && config.getRedistributionFactor().equals(request.getRedistributionFactor())));
    }

    @Test
    void createOrUpdateConfig_shouldUseDefaultRedistributionFactor() {
        Criterion criterion = Criterion.builder()
                .id(criterionId)
                .type(CriterionType.PEER_REVIEW)
                .build();

        PeerReviewConfigRequest request = PeerReviewConfigRequest.builder()
                .reviewersCount(2)
                .scoringStrategy(PeerReviewScoringStrategy.MAX)
                .firstDeadline(Instant.now().plusSeconds(3600))
                .secondDeadline(Instant.now().plusSeconds(7200))
                .build();

        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.empty());
        when(peerReviewConfigRepository.save(any(PeerReviewConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        peerReviewService.createOrUpdateConfig(criterion, request);

        verify(peerReviewConfigRepository).save(argThat(config -> config.getRedistributionFactor().equals(2)));
    }

    @Test
    void getConfigDto_shouldHideSecondDeadlineFromStudents() {
        Criterion criterion = peerReviewCriterion();
        PeerReviewConfig config = peerReviewConfig(criterion);

        when(criterionRepository.findByGradingConfigPostIdAndType(postId, CriterionType.PEER_REVIEW))
                .thenReturn(List.of(criterion));
        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(guard.isTeacher(courseId, userId)).thenReturn(false);

        peerReviewService.getConfigDto(courseId, postId, userId);

        verify(gradingDtoMapper).toPeerReviewConfigDto(config, false);
    }

    @Test
    void getConfigDto_shouldShowSecondDeadlineToTeachers() {
        Criterion criterion = peerReviewCriterion();
        PeerReviewConfig config = peerReviewConfig(criterion);

        when(criterionRepository.findByGradingConfigPostIdAndType(postId, CriterionType.PEER_REVIEW))
                .thenReturn(List.of(criterion));
        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(guard.isTeacher(courseId, userId)).thenReturn(true);

        peerReviewService.getConfigDto(courseId, postId, userId);

        verify(gradingDtoMapper).toPeerReviewConfigDto(config, true);
    }

    @Test
    void distributeRound1_shouldRejectWhenNoPeerReviewCriterion() {
        when(criterionRepository.findByGradingConfigPostIdAndType(postId, CriterionType.PEER_REVIEW))
                .thenReturn(List.of());

        assertThatThrownBy(() -> peerReviewService.distributeRound1(postId, courseId, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No PEER_REVIEW criterion configured");

        verify(guard).ensureTeacher(courseId, userId);
    }

    @Test
    void distributeRound1_shouldRejectWhenLessThanTwoSolutions() {
        Criterion criterion = peerReviewCriterion();
        PeerReviewConfig config = peerReviewConfig(criterion);

        when(criterionRepository.findByGradingConfigPostIdAndType(postId, CriterionType.PEER_REVIEW))
                .thenReturn(List.of(criterion));
        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(solutionRepository.findAllByPostIdAndStatusIn(postId, List.of(SolutionStatus.SUBMITTED, SolutionStatus.GRADED)))
                .thenReturn(List.of(solution(userId)));

        assertThatThrownBy(() -> peerReviewService.distributeRound1(postId, courseId, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Need at least 2 submitted solutions");
    }

    @Test
    void distributeRound1_shouldRejectWhenRound1HasCompletedReviews() {
        Criterion criterion = peerReviewCriterion();
        PeerReviewConfig config = peerReviewConfig(criterion);
        User studentA = user(UUID.randomUUID());
        User studentB = user(UUID.randomUUID());
        Solution solutionA = solution(studentA.getId());
        Solution solutionB = solution(studentB.getId());
        PeerReviewAssignment completedAssignment = PeerReviewAssignment.builder()
                .status(PeerReviewAssignmentStatus.COMPLETED)
                .build();

        when(criterionRepository.findByGradingConfigPostIdAndType(postId, CriterionType.PEER_REVIEW))
                .thenReturn(List.of(criterion));
        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(solutionRepository.findAllByPostIdAndStatusIn(postId, List.of(SolutionStatus.SUBMITTED, SolutionStatus.GRADED)))
                .thenReturn(List.of(solutionA, solutionB));
        when(peerReviewAssignmentRepository.findByPeerReviewConfigId(configId))
                .thenReturn(List.of(completedAssignment));

        assertThatThrownBy(() -> peerReviewService.distributeRound1(postId, courseId, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Round 1 already has completed reviews");
    }

    @Test
    void distributeRound1_shouldCreateAssignmentsWithoutSelfReviews() {
        Criterion criterion = peerReviewCriterion();
        PeerReviewConfig config = peerReviewConfig(criterion);
        User studentA = user(UUID.randomUUID());
        User studentB = user(UUID.randomUUID());
        Solution solutionA = solution(studentA.getId());
        Solution solutionB = solution(studentB.getId());

        when(criterionRepository.findByGradingConfigPostIdAndType(postId, CriterionType.PEER_REVIEW))
                .thenReturn(List.of(criterion));
        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(solutionRepository.findAllByPostIdAndStatusIn(postId, List.of(SolutionStatus.SUBMITTED, SolutionStatus.GRADED)))
                .thenReturn(List.of(solutionA, solutionB));
        when(peerReviewAssignmentRepository.findByPeerReviewConfigId(configId)).thenReturn(List.of());
        when(peerReviewAssignmentRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        peerReviewService.distributeRound1(postId, courseId, userId);

        verify(peerReviewAssignmentRepository).saveAll(argThat(assignments -> {
            List<PeerReviewAssignment> list = (List<PeerReviewAssignment>) assignments;
            return list.size() == 2
                    && list.stream().noneMatch(a ->
                    a.getReviewerUser().getId().equals(a.getRevieweeSolution().getStudent().getId()));
        }));
    }

    @Test
    void distributeRound1_manyToOne_shouldBalanceReviewerLoad() {
        Criterion criterion = peerReviewCriterion();
        PeerReviewConfig config = peerReviewConfig(criterion);
        config.setReviewersCount(2);
        User studentA = user(UUID.randomUUID());
        User studentB = user(UUID.randomUUID());
        User studentC = user(UUID.randomUUID());
        User studentD = user(UUID.randomUUID());
        Solution solutionA = solution(studentA.getId());
        Solution solutionB = solution(studentB.getId());
        Solution solutionC = solution(studentC.getId());
        Solution solutionD = solution(studentD.getId());

        when(criterionRepository.findByGradingConfigPostIdAndType(postId, CriterionType.PEER_REVIEW))
                .thenReturn(List.of(criterion));
        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(solutionRepository.findAllByPostIdAndStatusIn(postId, List.of(SolutionStatus.SUBMITTED, SolutionStatus.GRADED)))
                .thenReturn(List.of(solutionA, solutionB, solutionC, solutionD));
        when(peerReviewAssignmentRepository.findByPeerReviewConfigId(configId)).thenReturn(List.of());
        when(peerReviewAssignmentRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        peerReviewService.distributeRound1(postId, courseId, userId);

        verify(peerReviewAssignmentRepository).saveAll(argThat(assignments -> {
            List<PeerReviewAssignment> list = (List<PeerReviewAssignment>) assignments;
            if (list.size() != 8) {
                return false;
            }
            Map<UUID, Long> reviewerCounts = list.stream()
                    .collect(Collectors.groupingBy(a -> a.getReviewerUser().getId(), Collectors.counting()));
            return reviewerCounts.size() == 4
                    && reviewerCounts.values().stream().allMatch(c -> c == 2L)
                    && list.stream().noneMatch(a ->
                    a.getReviewerUser().getId().equals(a.getRevieweeSolution().getStudent().getId()));
        }));
    }

    @Test
    void distributeRound1_oneToOne_shouldCreateSingleAssignmentPerSolution() {
        Criterion criterion = peerReviewCriterion();
        PeerReviewConfig config = peerReviewConfig(criterion);
        config.setReviewMode(com.classroom.core.model.PeerReviewReviewMode.ONE_TO_ONE);
        User studentA = user(UUID.randomUUID());
        User studentB = user(UUID.randomUUID());
        User studentC = user(UUID.randomUUID());
        Solution solutionA = solution(studentA.getId());
        Solution solutionB = solution(studentB.getId());
        Solution solutionC = solution(studentC.getId());

        when(criterionRepository.findByGradingConfigPostIdAndType(postId, CriterionType.PEER_REVIEW))
                .thenReturn(List.of(criterion));
        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(solutionRepository.findAllByPostIdAndStatusIn(postId, List.of(SolutionStatus.SUBMITTED, SolutionStatus.GRADED)))
                .thenReturn(List.of(solutionA, solutionB, solutionC));
        when(peerReviewAssignmentRepository.findByPeerReviewConfigId(configId)).thenReturn(List.of());
        when(peerReviewAssignmentRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        peerReviewService.distributeRound1(postId, courseId, userId);

        verify(peerReviewAssignmentRepository).saveAll(argThat(assignments -> {
            List<PeerReviewAssignment> list = (List<PeerReviewAssignment>) assignments;
            long distinctReviewees = list.stream().map(a -> a.getRevieweeSolution().getId()).distinct().count();
            long distinctReviewers = list.stream().map(a -> a.getReviewerUser().getId()).distinct().count();
            return list.size() == 3
                    && distinctReviewees == 3
                    && distinctReviewers == 3
                    && list.stream().noneMatch(a ->
                    a.getReviewerUser().getId().equals(a.getRevieweeSolution().getStudent().getId()));
        }));
    }

    @Test
    void distributeRound2_shouldCreateAdditionalAssignmentsForUnderReviewedSolutions() {
        Criterion criterion = peerReviewCriterion();
        PeerReviewConfig config = peerReviewConfig(criterion);
        User studentA = user(UUID.randomUUID());
        User studentB = user(UUID.randomUUID());
        User studentC = user(UUID.randomUUID());
        Solution solutionA = solution(studentA.getId());
        Solution solutionB = solution(studentB.getId());
        Solution solutionC = solution(studentC.getId());

        PeerReviewAssignment missedAssignment = PeerReviewAssignment.builder()
                .reviewerUser(studentB)
                .revieweeSolution(solutionA)
                .status(PeerReviewAssignmentStatus.MISSED)
                .round(1)
                .build();

        when(criterionRepository.findByGradingConfigPostIdAndType(postId, CriterionType.PEER_REVIEW))
                .thenReturn(List.of(criterion));
        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(solutionRepository.findAllByPostIdAndStatusIn(postId, List.of(SolutionStatus.SUBMITTED, SolutionStatus.GRADED)))
                .thenReturn(List.of(solutionA, solutionB, solutionC));
        when(peerReviewAssignmentRepository.countByRevieweeSolutionIdAndStatus(solutionA.getId(), PeerReviewAssignmentStatus.COMPLETED))
                .thenReturn(0L);
        when(peerReviewAssignmentRepository.countByRevieweeSolutionIdAndStatus(solutionB.getId(), PeerReviewAssignmentStatus.COMPLETED))
                .thenReturn(1L);
        when(peerReviewAssignmentRepository.countByRevieweeSolutionIdAndStatus(solutionC.getId(), PeerReviewAssignmentStatus.COMPLETED))
                .thenReturn(1L);
        when(peerReviewAssignmentRepository.countByRevieweeSolutionIdAndStatus(solutionA.getId(), PeerReviewAssignmentStatus.MISSED))
                .thenReturn(1L);
        when(peerReviewAssignmentRepository.findByRevieweeSolutionId(solutionA.getId()))
                .thenReturn(List.of(missedAssignment));

        peerReviewService.distributeRound2(postId, courseId, userId);

        verify(peerReviewAssignmentRepository, atLeastOnce()).save(argThat(a ->
                a.getRevieweeSolution().equals(solutionA)
                        && a.getRound().equals(2)
                        && a.getStatus().equals(PeerReviewAssignmentStatus.PENDING)));
    }

    @Test
    void distributeRound2_shouldNotAssignTeamToItself() {
        Criterion criterion = peerReviewCriterionWithTeams();
        PeerReviewConfig config = peerReviewConfig(criterion);
        Course course = criterion.getGradingConfig().getPost().getCourse();
        CourseTeam teamA = CourseTeam.builder().id(UUID.randomUUID()).course(course).build();
        CourseTeam teamB = CourseTeam.builder().id(UUID.randomUUID()).course(course).build();
        CourseTeam teamC = CourseTeam.builder().id(UUID.randomUUID()).course(course).build();
        User studentA = user(UUID.randomUUID());
        User studentB = user(UUID.randomUUID());
        User studentC = user(UUID.randomUUID());
        Solution solutionA = teamSolution(studentA.getId(), teamA);
        Solution solutionB = teamSolution(studentB.getId(), teamB);
        Solution solutionC = teamSolution(studentC.getId(), teamC);

        PeerReviewAssignment missedAssignment = PeerReviewAssignment.builder()
                .reviewerTeam(teamB)
                .revieweeSolution(solutionA)
                .status(PeerReviewAssignmentStatus.MISSED)
                .round(1)
                .build();

        when(criterionRepository.findByGradingConfigPostIdAndType(postId, CriterionType.PEER_REVIEW))
                .thenReturn(List.of(criterion));
        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(solutionRepository.findAllByPostIdAndStatusIn(postId, List.of(SolutionStatus.SUBMITTED, SolutionStatus.GRADED)))
                .thenReturn(List.of(solutionA, solutionB, solutionC));
        when(peerReviewAssignmentRepository.countByRevieweeSolutionIdAndStatus(solutionA.getId(), PeerReviewAssignmentStatus.COMPLETED))
                .thenReturn(0L);
        when(peerReviewAssignmentRepository.countByRevieweeSolutionIdAndStatus(solutionB.getId(), PeerReviewAssignmentStatus.COMPLETED))
                .thenReturn(1L);
        when(peerReviewAssignmentRepository.countByRevieweeSolutionIdAndStatus(solutionC.getId(), PeerReviewAssignmentStatus.COMPLETED))
                .thenReturn(1L);
        when(peerReviewAssignmentRepository.countByRevieweeSolutionIdAndStatus(solutionA.getId(), PeerReviewAssignmentStatus.MISSED))
                .thenReturn(1L);
        when(peerReviewAssignmentRepository.findByRevieweeSolutionId(solutionA.getId()))
                .thenReturn(List.of(missedAssignment));

        peerReviewService.distributeRound2(postId, courseId, userId);

        verify(peerReviewAssignmentRepository, atLeastOnce()).save(argThat(a ->
                a.getRevieweeSolution().equals(solutionA)
                        && a.getRound().equals(2)
                        && a.getReviewerTeam() != null
                        && a.getReviewerTeam().getId().equals(teamC.getId())));
    }

    @Test
    void submitReview_shouldRejectNonOwner() {
        UUID assignmentId = UUID.randomUUID();
        PeerReviewAssignment assignment = PeerReviewAssignment.builder()
                .id(assignmentId)
                .reviewerUser(user(UUID.randomUUID()))
                .revieweeSolution(solution(userId))
                .peerReviewConfig(peerReviewConfig(peerReviewCriterion()))
                .build();

        when(peerReviewAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> peerReviewService.submitReview(courseId, postId, assignmentId, submitRequest(), userId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("does not belong to you");
    }

    @Test
    void submitReview_shouldRejectSelfReview() {
        UUID assignmentId = UUID.randomUUID();
        PeerReviewConfig config = peerReviewConfig(peerReviewCriterion());
        PeerReviewAssignment assignment = PeerReviewAssignment.builder()
                .id(assignmentId)
                .reviewerUser(user(userId))
                .revieweeSolution(solution(userId))
                .peerReviewConfig(config)
                .build();

        when(peerReviewAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> peerReviewService.submitReview(courseId, postId, assignmentId, submitRequest(), userId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("cannot review your own work");
    }

    @Test
    void submitReview_shouldRejectAfterDeadline() {
        UUID assignmentId = UUID.randomUUID();
        PeerReviewConfig config = peerReviewConfig(peerReviewCriterion());
        config.setFirstDeadline(Instant.now().minusSeconds(3600));

        User otherUser = user(UUID.randomUUID());
        PeerReviewAssignment assignment = PeerReviewAssignment.builder()
                .id(assignmentId)
                .reviewerUser(user(userId))
                .revieweeSolution(solution(otherUser.getId()))
                .peerReviewConfig(config)
                .build();

        when(peerReviewAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> peerReviewService.submitReview(courseId, postId, assignmentId, submitRequest(), userId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("deadline has passed");
    }

    @Test
    void submitReview_shouldRejectGradeOutOfRange() {
        UUID assignmentId = UUID.randomUUID();
        Criterion criterion = peerReviewCriterion();
        criterion.setMaxPoints(new BigDecimal("10"));
        PeerReviewConfig config = peerReviewConfig(criterion);

        User otherUser = user(UUID.randomUUID());
        PeerReviewAssignment assignment = PeerReviewAssignment.builder()
                .id(assignmentId)
                .reviewerUser(user(userId))
                .revieweeSolution(solution(otherUser.getId()))
                .peerReviewConfig(config)
                .build();

        when(peerReviewAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        SubmitPeerReviewRequest request = SubmitPeerReviewRequest.builder()
                .grade(new BigDecimal("15"))
                .build();

        assertThatThrownBy(() -> peerReviewService.submitReview(courseId, postId, assignmentId, request, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Grade must be between 0 and 10");
    }

    @Test
    void submitReview_shouldSaveReviewAndMarkCompleted() {
        UUID assignmentId = UUID.randomUUID();
        Criterion criterion = peerReviewCriterion();
        PeerReviewConfig config = peerReviewConfig(criterion);

        User otherUser = user(UUID.randomUUID());
        PeerReviewAssignment assignment = PeerReviewAssignment.builder()
                .id(assignmentId)
                .reviewerUser(user(userId))
                .revieweeSolution(solution(otherUser.getId()))
                .peerReviewConfig(config)
                .status(PeerReviewAssignmentStatus.PENDING)
                .build();

        when(peerReviewAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(peerReviewRepository.findByAssignmentId(assignmentId)).thenReturn(Optional.empty());
        when(peerReviewRepository.save(any(PeerReview.class))).thenAnswer(inv -> inv.getArgument(0));
        when(peerReviewAssignmentRepository.save(any(PeerReviewAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        peerReviewService.submitReview(courseId, postId, assignmentId, submitRequest(), userId);

        verify(peerReviewRepository).save(argThat(r -> r.getGrade().equals(submitRequest().getGrade())));
        verify(peerReviewAssignmentRepository).save(argThat(a ->
                a.getStatus().equals(PeerReviewAssignmentStatus.COMPLETED) && a.getCompletedAt() != null));
    }

    @Test
    void computeScore_shouldReturnZeroWhenNoConfig() {
        UUID solutionId = UUID.randomUUID();
        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.empty());

        assertThat(peerReviewService.computeScore(solutionId, criterionId)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void computeScore_shouldUseOnlyFirstNReviewsByTime() {
        UUID solutionId = UUID.randomUUID();
        Criterion criterion = peerReviewCriterion();
        PeerReviewConfig config = peerReviewConfig(criterion);
        config.setReviewersCount(2);
        config.setScoringStrategy(PeerReviewScoringStrategy.AVERAGE);

        PeerReview review1 = review(new BigDecimal("10"), Instant.now().minusSeconds(300));
        PeerReview review2 = review(new BigDecimal("20"), Instant.now().minusSeconds(200));
        PeerReview review3 = review(new BigDecimal("30"), Instant.now().minusSeconds(100));

        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(peerReviewRepository.findByRevieweeSolutionIdOrderBySubmittedAt(solutionId))
                .thenReturn(List.of(review1, review2, review3));

        BigDecimal score = peerReviewService.computeScore(solutionId, criterionId);

        assertThat(score).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    void computeScore_shouldSupportMinStrategy() {
        UUID solutionId = UUID.randomUUID();
        Criterion criterion = peerReviewCriterion();
        PeerReviewConfig config = peerReviewConfig(criterion);
        config.setReviewersCount(2);
        config.setScoringStrategy(PeerReviewScoringStrategy.MIN);

        PeerReview review1 = review(new BigDecimal("5"), Instant.now().minusSeconds(300));
        PeerReview review2 = review(new BigDecimal("8"), Instant.now().minusSeconds(200));

        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(peerReviewRepository.findByRevieweeSolutionIdOrderBySubmittedAt(solutionId))
                .thenReturn(List.of(review1, review2));

        assertThat(peerReviewService.computeScore(solutionId, criterionId)).isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    void computeScore_shouldSupportMaxStrategy() {
        UUID solutionId = UUID.randomUUID();
        Criterion criterion = peerReviewCriterion();
        PeerReviewConfig config = peerReviewConfig(criterion);
        config.setReviewersCount(2);
        config.setScoringStrategy(PeerReviewScoringStrategy.MAX);

        PeerReview review1 = review(new BigDecimal("5"), Instant.now().minusSeconds(300));
        PeerReview review2 = review(new BigDecimal("8"), Instant.now().minusSeconds(200));

        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(peerReviewRepository.findByRevieweeSolutionIdOrderBySubmittedAt(solutionId))
                .thenReturn(List.of(review1, review2));

        assertThat(peerReviewService.computeScore(solutionId, criterionId)).isEqualByComparingTo(new BigDecimal("8"));
    }

    @Test
    void computeScore_shouldSubtractMissedReviewPenalties() {
        UUID solutionId = UUID.randomUUID();
        User student = user(UUID.randomUUID());
        Solution solution = Solution.builder().id(solutionId).student(student).build();
        Criterion criterion = peerReviewCriterion();
        PeerReviewConfig config = peerReviewConfig(criterion);
        config.setReviewersCount(1);
        config.setScoringStrategy(PeerReviewScoringStrategy.AVERAGE);
        config.setMissedReviewPenalty(new BigDecimal("10"));

        PeerReview review = review(new BigDecimal("50"), Instant.now().minusSeconds(300));
        PeerReviewPenalty penalty = PeerReviewPenalty.builder()
                .penaltyPoints(new BigDecimal("10"))
                .build();

        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(peerReviewRepository.findByRevieweeSolutionIdOrderBySubmittedAt(solutionId))
                .thenReturn(List.of(review));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));
        when(peerReviewPenaltyRepository.findByPostIdAndUserId(postId, student.getId()))
                .thenReturn(List.of(penalty));

        assertThat(peerReviewService.computeScore(solutionId, criterionId)).isEqualByComparingTo(new BigDecimal("40"));
    }

    @Test
    void computeScore_shouldNotGoBelowZeroAfterPenalty() {
        UUID solutionId = UUID.randomUUID();
        User student = user(UUID.randomUUID());
        Solution solution = Solution.builder().id(solutionId).student(student).build();
        Criterion criterion = peerReviewCriterion();
        PeerReviewConfig config = peerReviewConfig(criterion);
        config.setMissedReviewPenalty(new BigDecimal("50"));

        PeerReview review = review(new BigDecimal("20"), Instant.now().minusSeconds(300));
        PeerReviewPenalty penalty = PeerReviewPenalty.builder()
                .penaltyPoints(new BigDecimal("50"))
                .build();

        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(peerReviewRepository.findByRevieweeSolutionIdOrderBySubmittedAt(solutionId))
                .thenReturn(List.of(review));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));
        when(peerReviewPenaltyRepository.findByPostIdAndUserId(postId, student.getId()))
                .thenReturn(List.of(penalty));

        assertThat(peerReviewService.computeScore(solutionId, criterionId)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void closeRound1_shouldMarkPendingAsMissedAndCreatePenalties() {
        Criterion criterion = peerReviewCriterion();
        PeerReviewConfig config = peerReviewConfigWithPastDeadlines(criterion);
        User studentA = user(UUID.randomUUID());
        User studentB = user(UUID.randomUUID());
        Solution solutionA = solution(studentA.getId());
        Solution solutionB = solution(studentB.getId());

        PeerReviewAssignment pendingAssignment = PeerReviewAssignment.builder()
                .id(UUID.randomUUID())
                .peerReviewConfig(config)
                .reviewerUser(studentA)
                .revieweeSolution(solutionB)
                .round(1)
                .status(PeerReviewAssignmentStatus.PENDING)
                .build();

        when(criterionRepository.findByGradingConfigPostIdAndType(postId, CriterionType.PEER_REVIEW))
                .thenReturn(List.of(criterion));
        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(peerReviewAssignmentRepository.findByPeerReviewConfigIdAndRoundAndStatus(
                config.getId(), 1, PeerReviewAssignmentStatus.PENDING))
                .thenReturn(List.of(pendingAssignment));
        when(solutionRepository.findAllByPostIdAndStatusIn(postId, List.of(SolutionStatus.SUBMITTED, SolutionStatus.GRADED)))
                .thenReturn(List.of(solutionA, solutionB));
        when(peerReviewAssignmentRepository.countByRevieweeSolutionIdAndStatus(solutionA.getId(), PeerReviewAssignmentStatus.COMPLETED))
                .thenReturn(1L);
        when(peerReviewAssignmentRepository.countByRevieweeSolutionIdAndStatus(solutionB.getId(), PeerReviewAssignmentStatus.COMPLETED))
                .thenReturn(1L);

        peerReviewService.closeRound1(postId);

        assertThat(pendingAssignment.getStatus()).isEqualTo(PeerReviewAssignmentStatus.MISSED);
        assertThat(pendingAssignment.getCompletedAt()).isNotNull();
        verify(peerReviewPenaltyRepository).save(argThat(p ->
                p.getUser().equals(studentA) && p.getPenaltyPoints().equals(new BigDecimal("10"))));
        assertThat(config.getRound1ClosedAt()).isNotNull();
    }

    @Test
    void closeRound2_shouldReturnUnderReviewedSolutions() {
        Criterion criterion = peerReviewCriterion();
        PeerReviewConfig config = peerReviewConfigWithPastDeadlines(criterion);
        User studentA = user(UUID.randomUUID());
        User studentB = user(UUID.randomUUID());
        Solution solutionA = solution(studentA.getId());
        Solution solutionB = solution(studentB.getId());

        when(criterionRepository.findByGradingConfigPostIdAndType(postId, CriterionType.PEER_REVIEW))
                .thenReturn(List.of(criterion));
        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(solutionRepository.findAllByPostIdAndStatusIn(postId, List.of(SolutionStatus.SUBMITTED, SolutionStatus.GRADED)))
                .thenReturn(List.of(solutionA, solutionB));
        when(peerReviewAssignmentRepository.countByRevieweeSolutionIdAndStatus(solutionA.getId(), PeerReviewAssignmentStatus.COMPLETED))
                .thenReturn(0L);
        when(peerReviewAssignmentRepository.countByRevieweeSolutionIdAndStatus(solutionB.getId(), PeerReviewAssignmentStatus.COMPLETED))
                .thenReturn(1L);

        List<Solution> result = peerReviewService.closeRound2(postId);

        assertThat(result).containsExactly(solutionA);
        assertThat(config.getRound2ClosedAt()).isNotNull();
    }

    @Test
    void submitReview_shouldAllowRound2SubmissionAfterFirstDeadline() {
        UUID assignmentId = UUID.randomUUID();
        Criterion criterion = peerReviewCriterion();
        PeerReviewConfig config = peerReviewConfig(criterion);
        config.setFirstDeadline(Instant.now().minusSeconds(3600));
        config.setSecondDeadline(Instant.now().plusSeconds(3600));

        User otherUser = user(UUID.randomUUID());
        PeerReviewAssignment assignment = PeerReviewAssignment.builder()
                .id(assignmentId)
                .reviewerUser(user(userId))
                .revieweeSolution(solution(otherUser.getId()))
                .peerReviewConfig(config)
                .round(2)
                .status(PeerReviewAssignmentStatus.PENDING)
                .build();

        when(peerReviewAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(peerReviewRepository.findByAssignmentId(assignmentId)).thenReturn(Optional.empty());
        when(peerReviewRepository.save(any(PeerReview.class))).thenAnswer(inv -> inv.getArgument(0));
        when(peerReviewAssignmentRepository.save(any(PeerReviewAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        peerReviewService.submitReview(courseId, postId, assignmentId, submitRequest(), userId);

        verify(peerReviewAssignmentRepository).save(argThat(a ->
                a.getStatus().equals(PeerReviewAssignmentStatus.COMPLETED)));
    }

    @Test
    void submitReview_shouldRejectRound2SubmissionAfterSecondDeadline() {
        UUID assignmentId = UUID.randomUUID();
        Criterion criterion = peerReviewCriterion();
        PeerReviewConfig config = peerReviewConfig(criterion);
        config.setFirstDeadline(Instant.now().minusSeconds(7200));
        config.setSecondDeadline(Instant.now().minusSeconds(3600));

        User otherUser = user(UUID.randomUUID());
        PeerReviewAssignment assignment = PeerReviewAssignment.builder()
                .id(assignmentId)
                .reviewerUser(user(userId))
                .revieweeSolution(solution(otherUser.getId()))
                .peerReviewConfig(config)
                .round(2)
                .build();

        when(peerReviewAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> peerReviewService.submitReview(courseId, postId, assignmentId, submitRequest(), userId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("second peer review deadline has passed");
    }

    @Test
    void closeRound1_shouldCreateTeamMissedReviewPenalty() {
        Criterion criterion = peerReviewCriterionWithTeams();
        PeerReviewConfig config = peerReviewConfigWithPastDeadlines(criterion);
        Course course = criterion.getGradingConfig().getPost().getCourse();
        CourseTeam teamA = CourseTeam.builder().id(UUID.randomUUID()).course(course).build();
        CourseTeam teamB = CourseTeam.builder().id(UUID.randomUUID()).course(course).build();
        User studentA = user(UUID.randomUUID());
        Solution solutionA = teamSolution(studentA.getId(), teamA);
        Solution solutionB = teamSolution(UUID.randomUUID(), teamB);

        PeerReviewAssignment pendingAssignment = PeerReviewAssignment.builder()
                .id(UUID.randomUUID())
                .peerReviewConfig(config)
                .reviewerTeam(teamA)
                .revieweeSolution(solutionB)
                .round(1)
                .status(PeerReviewAssignmentStatus.PENDING)
                .build();

        when(criterionRepository.findByGradingConfigPostIdAndType(postId, CriterionType.PEER_REVIEW))
                .thenReturn(List.of(criterion));
        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(peerReviewAssignmentRepository.findByPeerReviewConfigIdAndRoundAndStatus(
                config.getId(), 1, PeerReviewAssignmentStatus.PENDING))
                .thenReturn(List.of(pendingAssignment));
        when(solutionRepository.findAllByPostIdAndStatusIn(postId, List.of(SolutionStatus.SUBMITTED, SolutionStatus.GRADED)))
                .thenReturn(List.of(solutionA, solutionB));
        when(peerReviewAssignmentRepository.countByRevieweeSolutionIdAndStatus(solutionA.getId(), PeerReviewAssignmentStatus.COMPLETED))
                .thenReturn(1L);
        when(peerReviewAssignmentRepository.countByRevieweeSolutionIdAndStatus(solutionB.getId(), PeerReviewAssignmentStatus.COMPLETED))
                .thenReturn(1L);

        peerReviewService.closeRound1(postId);

        verify(peerReviewPenaltyRepository).save(argThat(p ->
                p.getTeam() != null && p.getTeam().getId().equals(teamA.getId())
                        && p.getPenaltyPoints().equals(new BigDecimal("10"))));
    }

    @Test
    void computeScore_shouldSubtractTeamMissedReviewPenalties() {
        UUID solutionId = UUID.randomUUID();
        CourseTeam team = CourseTeam.builder().id(UUID.randomUUID()).build();
        Solution solution = Solution.builder().id(solutionId).team(team).build();
        Criterion criterion = peerReviewCriterion();
        PeerReviewConfig config = peerReviewConfig(criterion);
        config.setReviewersCount(1);
        config.setScoringStrategy(PeerReviewScoringStrategy.AVERAGE);
        config.setMissedReviewPenalty(new BigDecimal("10"));

        PeerReview review = review(new BigDecimal("50"), Instant.now().minusSeconds(300));
        PeerReviewPenalty penalty = PeerReviewPenalty.builder()
                .team(team)
                .penaltyPoints(new BigDecimal("10"))
                .build();

        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(peerReviewRepository.findByRevieweeSolutionIdOrderBySubmittedAt(solutionId))
                .thenReturn(List.of(review));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));
        when(peerReviewPenaltyRepository.findByPostIdAndTeamId(postId, team.getId()))
                .thenReturn(List.of(penalty));

        assertThat(peerReviewService.computeScore(solutionId, criterionId)).isEqualByComparingTo(new BigDecimal("40"));
    }

    @Test
    void distributeRound1_shouldNotAssignTeamToItself() {
        Criterion criterion = peerReviewCriterionWithTeams();
        PeerReviewConfig config = peerReviewConfig(criterion);
        CourseTeam teamA = CourseTeam.builder().id(UUID.randomUUID()).course(criterion.getGradingConfig().getPost().getCourse()).build();
        CourseTeam teamB = CourseTeam.builder().id(UUID.randomUUID()).course(criterion.getGradingConfig().getPost().getCourse()).build();
        User studentA = user(UUID.randomUUID());
        User studentB = user(UUID.randomUUID());
        Solution solutionA = teamSolution(studentA.getId(), teamA);
        Solution solutionB = teamSolution(studentB.getId(), teamB);

        when(criterionRepository.findByGradingConfigPostIdAndType(postId, CriterionType.PEER_REVIEW))
                .thenReturn(List.of(criterion));
        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(solutionRepository.findAllByPostIdAndStatusIn(postId, List.of(SolutionStatus.SUBMITTED, SolutionStatus.GRADED)))
                .thenReturn(List.of(solutionA, solutionB));
        when(peerReviewAssignmentRepository.findByPeerReviewConfigId(configId)).thenReturn(List.of());
        when(peerReviewAssignmentRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        peerReviewService.distributeRound1(postId, courseId, userId);

        verify(peerReviewAssignmentRepository).saveAll(argThat(assignments -> {
            List<PeerReviewAssignment> list = (List<PeerReviewAssignment>) assignments;
            return list.size() == 2
                    && list.stream().allMatch(a -> a.getReviewerTeam() != null)
                    && list.stream().noneMatch(a ->
                    a.getReviewerTeam().getId().equals(a.getRevieweeSolution().getTeam().getId()));
        }));
    }

    @Test
    void getMyAssignments_shouldReturnTeamAssignments() {
        Criterion criterion = peerReviewCriterionWithTeams();
        PeerReviewConfig config = peerReviewConfig(criterion);
        Course course = criterion.getGradingConfig().getPost().getCourse();
        CourseTeam team = CourseTeam.builder().id(UUID.randomUUID()).course(course).build();
        User student = user(userId);
        CourseMember member = CourseMember.builder().user(student).team(team).build();
        PeerReviewAssignment teamAssignment = PeerReviewAssignment.builder()
                .id(UUID.randomUUID())
                .peerReviewConfig(config)
                .reviewerTeam(team)
                .revieweeSolution(teamSolution(UUID.randomUUID(), CourseTeam.builder().id(UUID.randomUUID()).course(course).build()))
                .status(PeerReviewAssignmentStatus.PENDING)
                .build();

        when(criterionRepository.findByGradingConfigPostIdAndType(postId, CriterionType.PEER_REVIEW))
                .thenReturn(List.of(criterion));
        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId)).thenReturn(Optional.of(member));
        when(peerReviewAssignmentRepository.findByReviewerTeamIdAndPeerReviewConfigId(team.getId(), config.getId()))
                .thenReturn(List.of(teamAssignment));

        List<PeerReviewAssignmentDto> result = peerReviewService.getMyAssignments(courseId, postId, userId);

        assertThat(result).hasSize(1);
    }

    @Test
    void submitReview_shouldAllowTeamMemberToSubmitTeamAssignment() {
        UUID assignmentId = UUID.randomUUID();
        Criterion criterion = peerReviewCriterionWithTeams();
        PeerReviewConfig config = peerReviewConfig(criterion);
        Course course = criterion.getGradingConfig().getPost().getCourse();
        CourseTeam team = CourseTeam.builder().id(UUID.randomUUID()).course(course).build();
        User otherStudent = user(UUID.randomUUID());
        Solution revieweeSolution = teamSolution(otherStudent.getId(), CourseTeam.builder().id(UUID.randomUUID()).course(course).build());
        PeerReviewAssignment assignment = PeerReviewAssignment.builder()
                .id(assignmentId)
                .peerReviewConfig(config)
                .reviewerTeam(team)
                .revieweeSolution(revieweeSolution)
                .status(PeerReviewAssignmentStatus.PENDING)
                .build();
        CourseMember member = CourseMember.builder().user(user(userId)).team(team).build();

        when(peerReviewAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId)).thenReturn(Optional.of(member));
        when(peerReviewRepository.findByAssignmentId(assignmentId)).thenReturn(Optional.empty());
        when(peerReviewRepository.save(any(PeerReview.class))).thenAnswer(inv -> inv.getArgument(0));
        when(peerReviewAssignmentRepository.save(any(PeerReviewAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        peerReviewService.submitReview(courseId, postId, assignmentId, submitRequest(), userId);

        verify(peerReviewAssignmentRepository).save(argThat(a -> a.getStatus().equals(PeerReviewAssignmentStatus.COMPLETED)));
    }

    @Test
    void applyGradesToAssessments_separateGradeSetsSolutionField() {
        Criterion criterion = peerReviewCriterion();
        PeerReviewConfig config = PeerReviewConfig.builder()
                .id(configId)
                .criterion(criterion)
                .reviewersCount(1)
                .scoringStrategy(PeerReviewScoringStrategy.AVERAGE)
                .firstDeadline(Instant.now().plusSeconds(3600))
                .secondDeadline(Instant.now().plusSeconds(7200))
                .redistributionFactor(2)
                .usageType(PeerReviewUsageType.SEPARATE_GRADE)
                .build();

        Solution solution = solution(userId);

        Post post = criterion.getGradingConfig().getPost();
        doNothing().when(guard).ensureTeacher(courseId, userId);
        when(guard.requireTaskPostInCourse(courseId, postId)).thenReturn(post);
        when(criterionRepository.findByGradingConfigPostIdAndType(postId, CriterionType.PEER_REVIEW))
                .thenReturn(List.of(criterion));
        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(solutionRepository.findAllByPostIdAndStatusIn(postId, List.of(SolutionStatus.SUBMITTED, SolutionStatus.GRADED)))
                .thenReturn(List.of(solution));
        when(peerReviewRepository.findByRevieweeSolutionIdOrderBySubmittedAt(solution.getId()))
                .thenReturn(List.of(review(new BigDecimal("80"), Instant.now())));
        when(peerReviewPenaltyRepository.findByPostIdAndUserId(postId, userId)).thenReturn(List.of());
        when(solutionRepository.findById(solution.getId())).thenReturn(Optional.of(solution));

        peerReviewService.applyGradesToAssessments(postId, courseId, userId);

        assertThat(solution.getPeerReviewGrade()).isEqualByComparingTo("80");
        verify(assessmentResultRepository, never()).save(any());
    }

    @Test
    void applyGradesToAssessments_criterionCreatesAssessmentResultWhenMissing() {
        Criterion criterion = peerReviewCriterion();
        PeerReviewConfig config = peerReviewConfig(criterion);
        config.setUsageType(PeerReviewUsageType.CRITERION);

        Solution solution = solution(userId);
        GradingConfigVersion version = GradingConfigVersion.builder()
                .id(UUID.randomUUID())
                .post(criterion.getGradingConfig().getPost())
                .versionNumber(1)
                .maxGrade(new BigDecimal("100"))
                .criteria(List.of(VersionedCriterion.builder()
                        .id(UUID.randomUUID())
                        .type(CriterionType.PEER_REVIEW)
                        .title("Peer Review")
                        .maxPoints(new BigDecimal("100"))
                        .weight(BigDecimal.ONE)
                        .sortOrder(0)
                        .build()))
                .build();

        Post post = criterion.getGradingConfig().getPost();
        doNothing().when(guard).ensureTeacher(courseId, userId);
        when(guard.requireTaskPostInCourse(courseId, postId)).thenReturn(post);
        when(criterionRepository.findByGradingConfigPostIdAndType(postId, CriterionType.PEER_REVIEW))
                .thenReturn(List.of(criterion));
        when(peerReviewConfigRepository.findByCriterionId(criterionId)).thenReturn(Optional.of(config));
        when(solutionRepository.findAllByPostIdAndStatusIn(postId, List.of(SolutionStatus.SUBMITTED, SolutionStatus.GRADED)))
                .thenReturn(List.of(solution));
        when(peerReviewRepository.findByRevieweeSolutionIdOrderBySubmittedAt(solution.getId()))
                .thenReturn(List.of(review(new BigDecimal("80"), Instant.now())));
        when(peerReviewPenaltyRepository.findByPostIdAndUserId(postId, userId)).thenReturn(List.of());
        when(solutionRepository.findById(solution.getId())).thenReturn(Optional.of(solution));
        when(assessmentResultRepository.findBySolutionId(solution.getId())).thenReturn(Optional.empty());
        when(gradingConfigVersionService.findOrCreateVersion(criterion.getGradingConfig())).thenReturn(version);
        when(gradingDtoMapper.toModifierConfig(version)).thenReturn(null);
        when(assessmentResultRepository.save(any(AssessmentResult.class))).thenAnswer(inv -> inv.getArgument(0));

        peerReviewService.applyGradesToAssessments(postId, courseId, userId);

        verify(assessmentResultRepository).save(argThat(result ->
                result.getSolution().equals(solution)
                        && result.getConfigVersion().equals(version)
                        && result.getCriterionGrades().stream()
                        .anyMatch(g -> g.getVersionedCriterion().getType() == CriterionType.PEER_REVIEW
                                && g.getValue().compareTo(new BigDecimal("80")) == 0)));
    }

    private PeerReviewConfigRequest configRequest() {
        return PeerReviewConfigRequest.builder()
                .reviewersCount(1)
                .scoringStrategy(PeerReviewScoringStrategy.AVERAGE)
                .firstDeadline(Instant.now().plusSeconds(3600))
                .secondDeadline(Instant.now().plusSeconds(7200))
                .redistributionFactor(2)
                .build();
    }

    private Criterion peerReviewCriterion() {
        Course course = Course.builder().id(courseId).build();
        Post post = Post.builder().id(postId).type(PostType.TASK).course(course).build();
        GradingConfig gradingConfig = GradingConfig.builder()
                .id(UUID.randomUUID())
                .post(post)
                .build();
        return Criterion.builder()
                .id(criterionId)
                .type(CriterionType.PEER_REVIEW)
                .gradingConfig(gradingConfig)
                .maxPoints(new BigDecimal("100"))
                .build();
    }

    private Criterion peerReviewCriterionWithTeams() {
        Course course = Course.builder().id(courseId).build();
        Post post = Post.builder().id(postId).type(PostType.TASK).course(course).teamFormationMode(TeamFormationMode.FREE).build();
        GradingConfig gradingConfig = GradingConfig.builder()
                .id(UUID.randomUUID())
                .post(post)
                .build();
        return Criterion.builder()
                .id(criterionId)
                .type(CriterionType.PEER_REVIEW)
                .gradingConfig(gradingConfig)
                .maxPoints(new BigDecimal("100"))
                .build();
    }

    private PeerReviewConfig peerReviewConfig(Criterion criterion) {
        return PeerReviewConfig.builder()
                .id(configId)
                .criterion(criterion)
                .reviewersCount(1)
                .scoringStrategy(PeerReviewScoringStrategy.AVERAGE)
                .firstDeadline(Instant.now().plusSeconds(3600))
                .secondDeadline(Instant.now().plusSeconds(7200))
                .redistributionFactor(2)
                .missedReviewPenalty(new BigDecimal("10"))
                .reviewMode(com.classroom.core.model.PeerReviewReviewMode.MANY_TO_ONE)
                .build();
    }

    private PeerReviewConfig peerReviewConfigWithPastDeadlines(Criterion criterion) {
        return PeerReviewConfig.builder()
                .id(configId)
                .criterion(criterion)
                .reviewersCount(1)
                .scoringStrategy(PeerReviewScoringStrategy.AVERAGE)
                .firstDeadline(Instant.now().minusSeconds(3600))
                .secondDeadline(Instant.now().minusSeconds(1800))
                .redistributionFactor(2)
                .missedReviewPenalty(new BigDecimal("10"))
                .reviewMode(com.classroom.core.model.PeerReviewReviewMode.MANY_TO_ONE)
                .build();
    }

    private User user(UUID id) {
        return User.builder().id(id).username(id.toString()).build();
    }

    private Solution solution(UUID studentId) {
        return Solution.builder()
                .id(UUID.randomUUID())
                .student(user(studentId))
                .status(SolutionStatus.SUBMITTED)
                .build();
    }

    private Solution teamSolution(UUID studentId, CourseTeam team) {
        return Solution.builder()
                .id(UUID.randomUUID())
                .student(user(studentId))
                .team(team)
                .status(SolutionStatus.SUBMITTED)
                .build();
    }

    private SubmitPeerReviewRequest submitRequest() {
        return SubmitPeerReviewRequest.builder()
                .grade(new BigDecimal("75"))
                .comment("Good work")
                .build();
    }

    private PeerReview review(BigDecimal grade, Instant submittedAt) {
        return PeerReview.builder()
                .grade(grade)
                .submittedAt(submittedAt)
                .build();
    }
}
