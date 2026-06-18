package com.classroom.core.service;

import com.classroom.core.dto.peerreview.PeerReviewAssignmentDto;
import com.classroom.core.dto.peerreview.PeerReviewConfigDto;
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
    private final PeerReviewPenaltyRepository peerReviewPenaltyRepository;
    private final CriterionRepository criterionRepository;
    private final SolutionRepository solutionRepository;
    private final CourseMemberRepository courseMemberRepository;
    private final AssessmentResultRepository assessmentResultRepository;
    private final GradingDtoMapper gradingDtoMapper;
    private final GradingGuard guard;
    private final NotificationService notificationService;

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
        config.setMissedReviewPenalty(
                request.getMissedReviewPenalty() != null ? request.getMissedReviewPenalty() : new BigDecimal("10"));
        config.setReviewMode(
                request.getReviewMode() != null ? request.getReviewMode() : PeerReviewReviewMode.MANY_TO_ONE);
        config.setUsageType(
                request.getUsageType() != null ? request.getUsageType() : PeerReviewUsageType.CRITERION);

        peerReviewConfigRepository.save(config);
    }

   public Optional<PeerReviewConfig> findConfigByCriterionId(UUID criterionId) {
        return peerReviewConfigRepository.findByCriterionId(criterionId);
    }

    public Optional<PeerReviewConfigDto> getConfigDto(UUID courseId, UUID postId, UUID userId) {
        guard.requireMember(courseId, userId);
        guard.requireTaskPostInCourse(courseId, postId);
        List<Criterion> criteria = findPeerReviewCriteriaForPost(postId);
        if (criteria.isEmpty()) {
            return Optional.empty();
        }
        PeerReviewConfig config = peerReviewConfigRepository.findByCriterionId(criteria.get(0).getId())
                .orElse(null);
        boolean isTeacher = guard.isTeacher(courseId, userId);
        return Optional.ofNullable(config)
                .map(c -> gradingDtoMapper.toPeerReviewConfigDto(c, isTeacher));
    }

    public List<Solution> getUnderReviewedSolutions(UUID courseId, UUID postId, UUID userId) {
        guard.ensureTeacher(courseId, userId);
        guard.requireTaskPostInCourse(courseId, postId);
        PeerReviewConfig config = requirePeerReviewConfig(postId);
        return findUnderReviewedSolutions(config);
    }

    public long countCompletedReviews(UUID solutionId) {
        return peerReviewAssignmentRepository
                .countByRevieweeSolutionIdAndStatus(solutionId, PeerReviewAssignmentStatus.COMPLETED);
    }

    @Transactional
    public void distributeRound1(UUID postId, UUID courseId, UUID userId) {
        guard.ensureTeacher(courseId, userId);
        guard.requireTaskPostInCourse(courseId, postId);

        if (!distributeRound1Internal(postId, true)) {
            throw new BadRequestException("Need at least 2 submitted solutions to distribute reviews");
        }
    }

    /**
     * System-only entry point for automatic round-1 distribution.
     * Does not enforce teacher rights and does not throw on insufficient participants.
     */
    @Transactional
    public void distributeRound1(UUID postId) {
        distributeRound1Internal(postId, false);
    }

    private boolean distributeRound1Internal(UUID postId, boolean throwOnFailure) {
        List<Criterion> peerCriteria = findPeerReviewCriteriaForPost(postId);
        if (peerCriteria.isEmpty()) {
            if (throwOnFailure) {
                throw new BadRequestException("No PEER_REVIEW criterion configured for this post");
            }
            return false;
        }

        Criterion criterion = peerCriteria.get(0);
        PeerReviewConfig config = peerReviewConfigRepository.findByCriterionId(criterion.getId())
                .orElse(null);
        if (config == null) {
            if (throwOnFailure) {
                throw new ResourceNotFoundException("PeerReviewConfig not found for criterion");
            }
            return false;
        }

        List<Solution> solutions = solutionRepository.findAllByPostIdAndStatusIn(
                postId, List.of(SolutionStatus.SUBMITTED, SolutionStatus.GRADED));
        if (solutions.size() < 2) {
            notifyTeachersOfImpossibleDistribution(config,
                    "Cannot start peer review: fewer than 2 submitted works.");
            return false;
        }

        List<PeerReviewAssignment> existing = peerReviewAssignmentRepository.findByPeerReviewConfigId(config.getId());
        boolean hasCompletedRound1 = existing.stream()
                .anyMatch(a -> a.getRound() == 1 && a.getStatus() == PeerReviewAssignmentStatus.COMPLETED);
        if (hasCompletedRound1) {
            if (throwOnFailure) {
                throw new BadRequestException("Round 1 already has completed reviews. Use round 2 redistribution.");
            }
            return true;
        }
        peerReviewAssignmentRepository.deleteAll(existing.stream()
                .filter(a -> a.getRound() == 1)
                .toList());

        List<PeerReviewAssignment> assignments = buildAssignments(config, solutions, 1);
        peerReviewAssignmentRepository.saveAll(assignments);
        return true;
    }

   @Transactional
    public void distributeRound2(UUID postId, UUID courseId, UUID userId) {
        guard.ensureTeacher(courseId, userId);
        guard.requireTaskPostInCourse(courseId, postId);

        List<Criterion> peerCriteria = findPeerReviewCriteriaForPost(postId);
        if (peerCriteria.isEmpty()) {
            throw new BadRequestException("No PEER_REVIEW criterion configured for this post");
        }

        Criterion criterion = peerCriteria.get(0);
        PeerReviewConfig config = peerReviewConfigRepository.findByCriterionId(criterion.getId())
                .orElseThrow(() -> new ResourceNotFoundException("PeerReviewConfig not found for criterion"));

        distributeRound2Internal(config);
    }

    @Transactional
    public void closeRound1(UUID postId, UUID courseId, UUID userId) {
        guard.ensureTeacher(courseId, userId);
        guard.requireTaskPostInCourse(courseId, postId);
        closeRound1(postId);
    }

    @Transactional
    public void closeRound1(UUID postId) {
        PeerReviewConfig config = requirePeerReviewConfig(postId);
        if (config.getRound1ClosedAt() != null) {
            return;
        }

        Instant now = Instant.now();
        if (now.isBefore(config.getFirstDeadline())) {
            return;
        }

        List<PeerReviewAssignment> pendingRound1 = peerReviewAssignmentRepository
                .findByPeerReviewConfigIdAndRoundAndStatus(config.getId(), 1, PeerReviewAssignmentStatus.PENDING);

        for (PeerReviewAssignment assignment : pendingRound1) {
            assignment.setStatus(PeerReviewAssignmentStatus.MISSED);
            assignment.setCompletedAt(now);
            createMissedReviewPenalty(assignment);
        }

        distributeRound2Internal(config);
        config.setRound1ClosedAt(now);
    }

    @Transactional
    public List<Solution> closeRound2(UUID postId, UUID courseId, UUID userId) {
        guard.ensureTeacher(courseId, userId);
        guard.requireTaskPostInCourse(courseId, postId);
        return closeRound2(postId);
    }

    @Transactional
    public List<Solution> closeRound2(UUID postId) {
        PeerReviewConfig config = requirePeerReviewConfig(postId);
        if (config.getRound2ClosedAt() != null) {
            return List.of();
        }

        Instant now = Instant.now();
        if (now.isBefore(config.getSecondDeadline())) {
            return List.of();
        }

        List<Solution> underReviewed = findUnderReviewedSolutions(config);
        if (!underReviewed.isEmpty()) {
            notifyTeachersOfUnderReviewed(config, underReviewed.size());
        }
        config.setRound2ClosedAt(now);
        return underReviewed;
    }

    private void notifyTeachersOfImpossibleDistribution(PeerReviewConfig config, String reason) {
        Post post = config.getCriterion().getGradingConfig().getPost();
        notificationService.notifyTeachers(
                post.getCourse().getId(),
                "Peer review distribution impossible",
                reason + " Task: " + post.getTitle());
    }

    private void notifyTeachersOfUnderReviewed(PeerReviewConfig config, int count) {
        Post post = config.getCriterion().getGradingConfig().getPost();
        notificationService.notifyTeachers(
                post.getCourse().getId(),
                "Peer review round 2 finished",
                count + " work(s) did not receive the required number of reviews. Task: " + post.getTitle());
    }

    public List<PeerReviewAssignmentDto> getMyAssignments(UUID courseId, UUID postId, UUID userId) {
        guard.requireMember(courseId, userId);
        guard.requireTaskPostInCourse(courseId, postId);
        PeerReviewConfig config = requirePeerReviewConfig(postId);
        boolean teamBased = isTeamBased(config);

        List<PeerReviewAssignment> assignments;
        if (teamBased) {
            CourseMember member = courseMemberRepository
                    .findByCourseIdAndUserId(config.getCriterion().getGradingConfig().getPost().getCourse().getId(), userId)
                    .orElse(null);
            if (member == null || member.getTeam() == null) {
                return List.of();
            }
            assignments = peerReviewAssignmentRepository
                    .findByReviewerTeamIdAndPeerReviewConfigId(member.getTeam().getId(), config.getId());
        } else {
            assignments = peerReviewAssignmentRepository
                    .findByReviewerUserIdAndPostId(userId, postId);
        }

        return assignments.stream()
                .map(this::toAssignmentDto)
                .toList();
    }

    @Transactional
    public PeerReviewAssignmentDto submitReview(UUID courseId, UUID postId, UUID assignmentId,
                                                SubmitPeerReviewRequest request, UUID userId) {
        guard.requireMember(courseId, userId);
        PeerReviewAssignment assignment = peerReviewAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));

        Post assignmentPost = assignment.getPeerReviewConfig().getCriterion().getGradingConfig().getPost();
        if (!assignmentPost.getId().equals(postId) || !assignmentPost.getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Assignment not found");
        }

        boolean isReviewer = assignment.getReviewerUser() != null
                && assignment.getReviewerUser().getId().equals(userId);
        boolean isTeamReviewer = assignment.getReviewerTeam() != null
                && isMemberOfReviewerTeam(assignment, userId);
        if (!isReviewer && !isTeamReviewer) {
            throw new ForbiddenException("This assignment does not belong to you");
        }
        if (assignment.getRevieweeSolution().getStudent() != null
                && assignment.getRevieweeSolution().getStudent().getId().equals(userId)) {
            throw new ForbiddenException("You cannot review your own work");
        }
        if (assignment.getRevieweeSolution().getTeam() != null
                && isMemberOfRevieweeTeam(assignment, userId)) {
            throw new ForbiddenException("Your team cannot review its own work");
        }

        PeerReviewConfig config = assignment.getPeerReviewConfig();

        Instant now = Instant.now();
        if (assignment.getRound() == 1 && now.isAfter(config.getFirstDeadline())) {
            throw new ForbiddenException("The first peer review deadline has passed");
        }
        if (assignment.getRound() == 2 && now.isAfter(config.getSecondDeadline())) {
            throw new ForbiddenException("The second peer review deadline has passed");
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

        BigDecimal baseScore;
        if (reviews.isEmpty()) {
            baseScore = BigDecimal.ZERO;
        } else {
            List<BigDecimal> grades = reviews.stream()
                    .limit(config.getReviewersCount())
                    .map(PeerReview::getGrade)
                    .toList();

            baseScore = switch (config.getScoringStrategy()) {
                case AVERAGE -> grades.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(grades.size()), 10, RoundingMode.HALF_UP);
                case MIN -> grades.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
                case MAX -> grades.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            };
        }

        Solution solution = solutionRepository.findById(solutionId).orElse(null);
        if (solution == null) {
            return baseScore;
        }

        UUID postId = config.getCriterion().getGradingConfig().getPost().getId();
        BigDecimal totalPenalty = BigDecimal.ZERO;

        if (solution.getStudent() != null) {
            totalPenalty = totalPenalty.add(peerReviewPenaltyRepository
                    .findByPostIdAndUserId(postId, solution.getStudent().getId())
                    .stream()
                    .map(PeerReviewPenalty::getPenaltyPoints)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        if (solution.getTeam() != null) {
            totalPenalty = totalPenalty.add(peerReviewPenaltyRepository
                    .findByPostIdAndTeamId(postId, solution.getTeam().getId())
                    .stream()
                    .map(PeerReviewPenalty::getPenaltyPoints)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }

        BigDecimal finalScore = baseScore.subtract(totalPenalty);
        return finalScore.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : finalScore;
    }

    @Transactional
    public void applyGradesToAssessments(UUID postId, UUID courseId, UUID userId) {
        guard.ensureTeacher(courseId, userId);
        guard.requireTaskPostInCourse(courseId, postId);

        List<Criterion> peerCriteria = findPeerReviewCriteriaForPost(postId);
        if (peerCriteria.isEmpty()) {
            return;
        }

        Criterion criterion = peerCriteria.get(0);
        PeerReviewConfig config = peerReviewConfigRepository.findByCriterionId(criterion.getId())
                .orElse(null);
        if (config == null) {
            return;
        }

        List<AssessmentResult> results = assessmentResultRepository.findBySolutionPostId(postId);

        for (AssessmentResult result : results) {
            UUID solutionId = result.getSolution().getId();
            BigDecimal score = computeScore(solutionId, criterion.getId());

            if (config.getUsageType() == PeerReviewUsageType.SEPARATE_GRADE) {
                result.getSolution().setPeerReviewGrade(score);
                continue;
            }

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
        boolean teamBased = isTeamBased(config);
        if (config.getReviewMode() == PeerReviewReviewMode.ONE_TO_ONE) {
            return buildOneToOneAssignments(config, solutions, teamBased, round);
        }

        int reviewersCount = config.getReviewersCount();
        List<PeerReviewAssignment> assignments = new ArrayList<>();

        List<Solution> shuffled = new ArrayList<>(solutions);
        Collections.shuffle(shuffled);

        for (Solution reviewee : solutions) {
            List<Solution> candidates = shuffled.stream()
                    .filter(s -> !isSameReviewer(s, reviewee, teamBased))
                    .collect(Collectors.toList());

           int assignCount = Math.min(reviewersCount, candidates.size());
            for (int i = 0; i < assignCount; i++) {
                Solution reviewerSolution = candidates.get(i);
                assignments.add(buildAssignment(config, reviewee, reviewerSolution, teamBased, round));
            }
        }
        return assignments;
    }

    private List<PeerReviewAssignment> buildOneToOneAssignments(PeerReviewConfig config,
                                                                 List<Solution> solutions,
                                                                 boolean teamBased,
                                                                 int round) {
        if (solutions.size() < 2) {
            return List.of();
        }
        List<PeerReviewAssignment> assignments = new ArrayList<>();
        List<Solution> shuffled = new ArrayList<>(solutions);
        Collections.shuffle(shuffled);

        for (int i = 0; i < shuffled.size(); i++) {
            Solution reviewee = shuffled.get(i);
            Solution reviewer = shuffled.get((i + 1) % shuffled.size());
            if (isSameReviewer(reviewer, reviewee, teamBased)) {
                continue;
            }
            assignments.add(buildAssignment(config, reviewee, reviewer, teamBased, round));
        }
        return assignments;
    }

    private PeerReviewAssignment buildAssignment(PeerReviewConfig config,
                                                  Solution reviewee,
                                                  Solution reviewerSolution,
                                                  boolean teamBased,
                                                  int round) {
        PeerReviewAssignment.PeerReviewAssignmentBuilder builder = PeerReviewAssignment.builder()
                .peerReviewConfig(config)
                .revieweeSolution(reviewee)
                .round(round)
                .status(PeerReviewAssignmentStatus.PENDING);
        if (teamBased && reviewerSolution.getTeam() != null) {
            builder.reviewerTeam(reviewerSolution.getTeam());
        } else {
            builder.reviewerUser(reviewerSolution.getStudent());
        }
        return builder.build();
    }

    private PeerReviewConfig requirePeerReviewConfig(UUID postId) {
        List<Criterion> peerCriteria = findPeerReviewCriteriaForPost(postId);
        if (peerCriteria.isEmpty()) {
            throw new ResourceNotFoundException("No PEER_REVIEW criterion configured for this post");
        }
        return peerReviewConfigRepository.findByCriterionId(peerCriteria.get(0).getId())
                .orElseThrow(() -> new ResourceNotFoundException("PeerReviewConfig not found for criterion"));
    }

    private void createMissedReviewPenalty(PeerReviewAssignment assignment) {
        PeerReviewConfig config = assignment.getPeerReviewConfig();
        BigDecimal penalty = config.getMissedReviewPenalty() != null
                ? config.getMissedReviewPenalty()
                : BigDecimal.ZERO;
        if (penalty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Post post = config.getCriterion().getGradingConfig().getPost();
        if (assignment.getReviewerTeam() != null) {
            peerReviewPenaltyRepository.save(PeerReviewPenalty.builder()
                    .post(post)
                    .team(assignment.getReviewerTeam())
                    .penaltyPoints(penalty)
                    .reason("Missed team peer review assignment in round 1")
                    .build());
        } else if (assignment.getReviewerUser() != null) {
            peerReviewPenaltyRepository.save(PeerReviewPenalty.builder()
                    .post(post)
                    .user(assignment.getReviewerUser())
                    .penaltyPoints(penalty)
                    .reason("Missed peer review assignment in round 1")
                    .build());
        }
    }

    private void distributeRound2Internal(PeerReviewConfig config) {
        boolean teamBased = isTeamBased(config);
        UUID postId = config.getCriterion().getGradingConfig().getPost().getId();
        List<Solution> solutions = solutionRepository.findAllByPostIdAndStatusIn(
                postId, List.of(SolutionStatus.SUBMITTED, SolutionStatus.GRADED));

        if (solutions.size() < 2) {
            return;
        }

        List<Solution> underReviewed = findUnderReviewedSolutions(config);
        if (underReviewed.isEmpty()) {
            return;
        }

        for (Solution reviewee : underReviewed) {
            long completed = peerReviewAssignmentRepository
                    .countByRevieweeSolutionIdAndStatus(reviewee.getId(), PeerReviewAssignmentStatus.COMPLETED);
            long missed = peerReviewAssignmentRepository
                    .countByRevieweeSolutionIdAndStatus(reviewee.getId(), PeerReviewAssignmentStatus.MISSED);

            int needed = (int) (missed * config.getRedistributionFactor());
            if (needed == 0) {
                needed = (int) (config.getReviewersCount() - completed);
            }

            Set<UUID> alreadyAssigned = peerReviewAssignmentRepository
                    .findByRevieweeSolutionId(reviewee.getId()).stream()
                    .map(a -> reviewerIdentity(a, teamBased))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            List<Solution> eligible = solutions.stream()
                    .filter(s -> !s.getId().equals(reviewee.getId()))
                    .filter(s -> !alreadyAssigned.contains(reviewerIdentity(s, teamBased)))
                    .filter(s -> !isSameReviewer(s, reviewee, teamBased))
                    .collect(Collectors.toList());

            Collections.shuffle(eligible);

            int assignCount = Math.min(needed, eligible.size());
            for (int i = 0; i < assignCount; i++) {
                Solution reviewerSolution = eligible.get(i);
                PeerReviewAssignment.PeerReviewAssignmentBuilder builder = PeerReviewAssignment.builder()
                        .peerReviewConfig(config)
                        .revieweeSolution(reviewee)
                        .round(2)
                        .status(PeerReviewAssignmentStatus.PENDING);
                if (teamBased && reviewerSolution.getTeam() != null) {
                    builder.reviewerTeam(reviewerSolution.getTeam());
                } else {
                    builder.reviewerUser(reviewerSolution.getStudent());
                }
                peerReviewAssignmentRepository.save(builder.build());
            }
        }
    }

    private List<Solution> findUnderReviewedSolutions(PeerReviewConfig config) {
        UUID postId = config.getCriterion().getGradingConfig().getPost().getId();
        List<Solution> solutions = solutionRepository.findAllByPostIdAndStatusIn(
                postId, List.of(SolutionStatus.SUBMITTED, SolutionStatus.GRADED));
        return solutions.stream()
                .filter(s -> peerReviewAssignmentRepository
                        .countByRevieweeSolutionIdAndStatus(s.getId(), PeerReviewAssignmentStatus.COMPLETED)
                        < config.getReviewersCount())
                .toList();
    }

    private List<Criterion> findPeerReviewCriteriaForPost(UUID postId) {
        return criterionRepository.findByGradingConfigPostIdAndType(postId, CriterionType.PEER_REVIEW);
    }

    private boolean isTeamBased(PeerReviewConfig config) {
        Post post = config.getCriterion().getGradingConfig().getPost();
        return post.getTeamFormationMode() != null;
    }

    private boolean isSameReviewer(Solution a, Solution b, boolean teamBased) {
        if (teamBased && a.getTeam() != null && b.getTeam() != null) {
            return a.getTeam().getId().equals(b.getTeam().getId());
        }
        return a.getStudent().getId().equals(b.getStudent().getId());
    }

    private UUID reviewerIdentity(Solution solution, boolean teamBased) {
        if (teamBased && solution.getTeam() != null) {
            return solution.getTeam().getId();
        }
        return solution.getStudent() != null ? solution.getStudent().getId() : null;
    }

    private UUID reviewerIdentity(PeerReviewAssignment assignment, boolean teamBased) {
        if (teamBased && assignment.getReviewerTeam() != null) {
            return assignment.getReviewerTeam().getId();
        }
        return assignment.getReviewerUser() != null ? assignment.getReviewerUser().getId() : null;
    }

    private boolean isMemberOfReviewerTeam(PeerReviewAssignment assignment, UUID userId) {
        if (assignment.getReviewerTeam() == null) {
            return false;
        }
        CourseMember member = courseMemberRepository
                .findByCourseIdAndUserId(assignment.getReviewerTeam().getCourse().getId(), userId)
                .orElse(null);
        return member != null
                && member.getTeam() != null
                && member.getTeam().getId().equals(assignment.getReviewerTeam().getId());
    }

    private boolean isMemberOfRevieweeTeam(PeerReviewAssignment assignment, UUID userId) {
        if (assignment.getRevieweeSolution() == null || assignment.getRevieweeSolution().getTeam() == null) {
            return false;
        }
        CourseTeam revieweeTeam = assignment.getRevieweeSolution().getTeam();
        CourseMember member = courseMemberRepository
                .findByCourseIdAndUserId(revieweeTeam.getCourse().getId(), userId)
                .orElse(null);
        return member != null
                && member.getTeam() != null
                && member.getTeam().getId().equals(revieweeTeam.getId());
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
