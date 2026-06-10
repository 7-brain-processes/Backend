package com.classroom.core.service;

import com.classroom.core.dto.peerreview.PeerReviewAssignmentDto;
import com.classroom.core.dto.peerreview.PeerReviewConfigRequest;
import com.classroom.core.dto.peerreview.SubmitPeerReviewRequest;
import com.classroom.core.exception.BadRequestException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.*;
import com.classroom.core.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PeerReviewService {

    private final PeerReviewConfigRepository peerReviewConfigRepository;
    private final PeerReviewAssignmentRepository peerReviewAssignmentRepository;
    private final PeerReviewRepository peerReviewRepository;
    private final CriterionRepository criterionRepository;
    private final SolutionRepository solutionRepository;
    private final AssessmentResultRepository assessmentResultRepository;
    private final GradingDtoMapper gradingDtoMapper;
    private final GradingGuard guard;

    @Transactional
    public void createOrUpdateConfig(Criterion criterion, PeerReviewConfigRequest request) {
        if (criterion.getType() != CriterionType.PEER_REVIEW) {
            throw new BadRequestException("peerReviewConfig is only valid for PEER_REVIEW criteria");
        }
        if (request.getFirstDeadline().isAfter(request.getSecondDeadline())) {
            throw new BadRequestException("firstDeadline must be before secondDeadline");
        }

        PeerReviewConfig config = peerReviewConfigRepository.findByCriterionId(criterion.getId())
                .orElse(PeerReviewConfig.builder().criterion(criterion).build());

        config.setReviewersCount(request.getReviewersCount());
        config.setScoringStrategy(request.getScoringStrategy());
        config.setFirstDeadline(request.getFirstDeadline());
        config.setSecondDeadline(request.getSecondDeadline());
        config.setRedistributionFactor(
                request.getRedistributionFactor() != null ? request.getRedistributionFactor() : 2);

        peerReviewConfigRepository.save(config);
    }

   public Optional<PeerReviewConfig> findConfigByCriterionId(UUID criterionId) {
        return peerReviewConfigRepository.findByCriterionId(criterionId);
    }

    @Transactional
    public void distributeRound1(UUID postId, UUID courseId, UUID userId) {
        guard.ensureTeacher(courseId, userId);

        List<Criterion> peerCriteria = findPeerReviewCriteriaForPost(postId);
        if (peerCriteria.isEmpty()) {
            throw new BadRequestException("No PEER_REVIEW criterion configured for this post");
        }

        Criterion criterion = peerCriteria.get(0);
        PeerReviewConfig config = peerReviewConfigRepository.findByCriterionId(criterion.getId())
                .orElseThrow(() -> new ResourceNotFoundException("PeerReviewConfig not found for criterion"));

        List<Solution> solutions = solutionRepository.findAllByPostIdAndStatusIn(
                postId, List.of(SolutionStatus.SUBMITTED, SolutionStatus.GRADED));
        if (solutions.size() < 2) {
            throw new BadRequestException("Need at least 2 submitted solutions to distribute reviews");
        }

        List<PeerReviewAssignment> existing = peerReviewAssignmentRepository.findByPeerReviewConfigId(config.getId());
        boolean hasCompleted = existing.stream()
                .anyMatch(a -> a.getStatus() == PeerReviewAssignmentStatus.COMPLETED);
        if (hasCompleted) {
            throw new BadRequestException("Round 1 already has completed reviews. Use round 2 redistribution.");
        }
        peerReviewAssignmentRepository.deleteAll(existing);

        List<PeerReviewAssignment> assignments = buildAssignments(config, solutions, 1);
        peerReviewAssignmentRepository.saveAll(assignments);
    }

   @Transactional
    public void distributeRound2(UUID postId, UUID courseId, UUID userId) {
        guard.ensureTeacher(courseId, userId);

        List<Criterion> peerCriteria = findPeerReviewCriteriaForPost(postId);
        if (peerCriteria.isEmpty()) {
            throw new BadRequestException("No PEER_REVIEW criterion configured for this post");
        }

        Criterion criterion = peerCriteria.get(0);
        PeerReviewConfig config = peerReviewConfigRepository.findByCriterionId(criterion.getId())
                .orElseThrow(() -> new ResourceNotFoundException("PeerReviewConfig not found for criterion"));

        List<Solution> solutions = solutionRepository.findAllByPostIdAndStatusIn(
                postId, List.of(SolutionStatus.SUBMITTED, SolutionStatus.GRADED));

        List<Solution> underReviewed = solutions.stream()
                .filter(s -> {
                    long completed = peerReviewAssignmentRepository
                            .countByRevieweeSolutionIdAndStatus(s.getId(), PeerReviewAssignmentStatus.COMPLETED);
                    return completed < config.getReviewersCount();
                })
                .toList();

        if (underReviewed.isEmpty()) {
            return;
        }

        for (Solution reviewee : underReviewed) {
            long completed = peerReviewAssignmentRepository
                    .countByRevieweeSolutionIdAndStatus(reviewee.getId(), PeerReviewAssignmentStatus.COMPLETED);
            long missed = peerReviewAssignmentRepository
                    .countByRevieweeSolutionIdAndStatus(reviewee.getId(), PeerReviewAssignmentStatus.MISSED);

           int needed = (int) (missed * config.getRedistributionFactor());
            if (needed == 0) needed = (int) (config.getReviewersCount() - completed);

            Set<UUID> alreadyAssigned = peerReviewAssignmentRepository
                    .findByRevieweeSolutionId(reviewee.getId()).stream()
                    .filter(a -> a.getReviewerUser() != null)
                    .map(a -> a.getReviewerUser().getId())
                    .collect(Collectors.toSet());

            List<Solution> eligible = solutions.stream()
                    .filter(s -> !s.getId().equals(reviewee.getId()))
                    .filter(s -> !alreadyAssigned.contains(s.getStudent().getId()))
                    .filter(s -> !s.getStudent().getId().equals(reviewee.getStudent().getId()))
                    .collect(Collectors.toList());

            Collections.shuffle(eligible);

            int assignCount = Math.min(needed, eligible.size());
            for (int i = 0; i < assignCount; i++) {
                PeerReviewAssignment assignment = PeerReviewAssignment.builder()
                        .peerReviewConfig(config)
                        .reviewerUser(eligible.get(i).getStudent())
                        .revieweeSolution(reviewee)
                        .round(2)
                        .status(PeerReviewAssignmentStatus.PENDING)
                        .build();
                peerReviewAssignmentRepository.save(assignment);
            }
        }
    }

    public List<PeerReviewAssignmentDto> getMyAssignments(UUID postId, UUID userId) {
        List<PeerReviewAssignment> assignments = peerReviewAssignmentRepository
                .findByReviewerUserIdAndPostId(userId, postId);

        return assignments.stream()
                .map(this::toAssignmentDto)
                .toList();
    }

    @Transactional
    public PeerReviewAssignmentDto submitReview(UUID assignmentId, SubmitPeerReviewRequest request, UUID userId) {
        PeerReviewAssignment assignment = peerReviewAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));

        if (assignment.getReviewerUser() == null ||
                !assignment.getReviewerUser().getId().equals(userId)) {
            throw new ForbiddenException("This assignment does not belong to you");
        }
        if (assignment.getRevieweeSolution().getStudent().getId().equals(userId)) {
            throw new ForbiddenException("You cannot review your own work");
        }

        PeerReviewConfig config = assignment.getPeerReviewConfig();

        if (Instant.now().isAfter(config.getFirstDeadline())) {
            throw new ForbiddenException("The peer review deadline has passed");
        }

        BigDecimal maxPoints = config.getCriterion().getMaxPoints();
        if (request.getGrade().compareTo(BigDecimal.ZERO) < 0 ||
                request.getGrade().compareTo(maxPoints) > 0) {
            throw new BadRequestException("Grade must be between 0 and " + maxPoints);
        }

        PeerReview review = peerReviewRepository.findByAssignmentId(assignmentId)
                .orElse(PeerReview.builder().assignment(assignment).build());

        review.setGrade(request.getGrade());
        review.setComment(request.getComment());
        review.setSubmittedAt(Instant.now());
        peerReviewRepository.save(review);

        assignment.setStatus(PeerReviewAssignmentStatus.COMPLETED);
        assignment.setCompletedAt(Instant.now());
        peerReviewAssignmentRepository.save(assignment);

        return toAssignmentDto(assignment);
    }

    public BigDecimal computeScore(UUID solutionId, UUID criterionId) {
        PeerReviewConfig config = peerReviewConfigRepository.findByCriterionId(criterionId)
                .orElse(null);
        if (config == null) {
            return BigDecimal.ZERO;
        }

        List<PeerReview> reviews = peerReviewRepository
                .findByRevieweeSolutionIdOrderBySubmittedAt(solutionId);

        if (reviews.isEmpty()) {
            return BigDecimal.ZERO;
        }

        List<BigDecimal> grades = reviews.stream()
                .limit(config.getReviewersCount())
                .map(PeerReview::getGrade)
                .toList();

        return switch (config.getScoringStrategy()) {
            case AVERAGE -> grades.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(grades.size()), 10, RoundingMode.HALF_UP);
            case MIN -> grades.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            case MAX -> grades.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        };
    }

    @Transactional
    public void applyGradesToAssessments(UUID postId, UUID courseId, UUID userId) {
        guard.ensureTeacher(courseId, userId);

        List<Criterion> peerCriteria = findPeerReviewCriteriaForPost(postId);
        if (peerCriteria.isEmpty()) {
            return;
        }

        Criterion criterion = peerCriteria.get(0);
        List<AssessmentResult> results = assessmentResultRepository.findBySolutionPostId(postId);

        for (AssessmentResult result : results) {
            UUID solutionId = result.getSolution().getId();
            BigDecimal score = computeScore(solutionId, criterion.getId());

            Optional<VersionedCriterion> peerVersionedCriterion = result.getConfigVersion().getCriteria()
                    .stream()
                    .filter(vc -> vc.getType() == CriterionType.PEER_REVIEW)
                    .findFirst();

            if (peerVersionedCriterion.isEmpty()) {
                continue;
            }

            ModifierConfig modifierConfig = gradingDtoMapper.toModifierConfig(result.getConfigVersion());
            result.applyPeerReviewGrade(peerVersionedCriterion.get(), score, modifierConfig);
            assessmentResultRepository.save(result);
        }
    }

    private List<PeerReviewAssignment> buildAssignments(PeerReviewConfig config,
                                                         List<Solution> solutions,
                                                         int round) {
        int reviewersCount = config.getReviewersCount();
        List<PeerReviewAssignment> assignments = new ArrayList<>();

        List<Solution> shuffled = new ArrayList<>(solutions);
        Collections.shuffle(shuffled);

        for (Solution reviewee : solutions) {
            List<Solution> candidates = shuffled.stream()
                    .filter(s -> !s.getStudent().getId().equals(reviewee.getStudent().getId()))
                    .collect(Collectors.toList());

           int assignCount = Math.min(reviewersCount, candidates.size());
            for (int i = 0; i < assignCount; i++) {
                assignments.add(PeerReviewAssignment.builder()
                        .peerReviewConfig(config)
                        .reviewerUser(candidates.get(i).getStudent())
                        .revieweeSolution(reviewee)
                        .round(round)
                        .status(PeerReviewAssignmentStatus.PENDING)
                        .build());
            }
        }
        return assignments;
    }

    private List<Criterion> findPeerReviewCriteriaForPost(UUID postId) {
        return criterionRepository.findByGradingConfigPostIdAndType(postId, CriterionType.PEER_REVIEW);
    }

    private PeerReviewAssignmentDto toAssignmentDto(PeerReviewAssignment assignment) {
        PeerReview review = peerReviewRepository.findByAssignmentId(assignment.getId()).orElse(null);
        return PeerReviewAssignmentDto.builder()
                .assignmentId(assignment.getId())
                .revieweeSolutionId(assignment.getRevieweeSolution().getId())
                .status(assignment.getStatus())
                .round(assignment.getRound())
                .firstDeadline(assignment.getPeerReviewConfig().getFirstDeadline())
                .assignedAt(assignment.getAssignedAt())
                .completedAt(assignment.getCompletedAt())
                .submittedGrade(review != null ? review.getGrade() : null)
                .submittedComment(review != null ? review.getComment() : null)
                .build();
    }
}
