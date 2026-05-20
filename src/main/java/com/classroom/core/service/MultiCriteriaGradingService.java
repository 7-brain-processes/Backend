package com.classroom.core.service;

import com.classroom.core.dto.grading.*;
import com.classroom.core.exception.BadRequestException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.*;
import com.classroom.core.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MultiCriteriaGradingService {

    private final GradingConfigRepository gradingConfigRepository;
    private final CriterionRepository criterionRepository;
    private final GradingConfigVersionRepository gradingConfigVersionRepository;
    private final VersionedCriterionRepository versionedCriterionRepository;
    private final AssessmentResultRepository assessmentResultRepository;
    private final AssessmentCriterionGradeRepository assessmentCriterionGradeRepository;
    private final PostRepository postRepository;
    private final SolutionRepository solutionRepository;
    private final CourseMemberRepository courseMemberRepository;
    private final CourseRepository courseRepository;
    private final ObjectMapper objectMapper;

    public GradingConfigDto getGradingConfig(UUID courseId, UUID postId, UUID userId) {
        requireMember(courseId, userId);
        requireTaskPostInCourse(courseId, postId);

        GradingConfig config = gradingConfigRepository.findByPostId(postId).orElse(null);
        if (config == null) {
            return null;
        }
        return toConfigDto(config);
    }

    @Transactional
    public GradingConfigDto upsertGradingConfig(UUID courseId, UUID postId,
                                                UpsertGradingConfigRequest request, UUID userId) {
        ensureTeacher(courseId, userId);
        Post post = requireTaskPostInCourse(courseId, postId);

        if (request.getMaxGrade().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("maxGrade must be greater than 0");
        }

        GradingConfig config = gradingConfigRepository.findByPostId(postId).orElse(null);
        if (config == null) {
            config = GradingConfig.builder()
                    .post(post)
                    .build();
        }

        config.setMaxGrade(request.getMaxGrade());
        config.setResultsVisible(request.getResultsVisible() != null ? request.getResultsVisible() : false);

        if (request.getModifiers() != null) {
            try {
                config.setModifiersJson(objectMapper.writeValueAsString(request.getModifiers()));
            } catch (JsonProcessingException e) {
                throw new BadRequestException("Invalid modifiers JSON");
            }
        } else {
            config.setModifiersJson(null);
        }

        List<Criterion> newCriteria = request.getCriteria().stream()
                .map(dto -> Criterion.builder()
                        .type(dto.getType())
                        .title(dto.getTitle())
                        .maxPoints(dto.getMaxPoints())
                        .weight(dto.getWeight() != null ? dto.getWeight() : BigDecimal.ONE)
                        .build())
                .toList();

        config.replaceCriteria(newCriteria);

        GradingConfig saved = gradingConfigRepository.save(config);
        return toConfigDto(saved);
    }

    @Transactional
    public void deleteGradingConfig(UUID courseId, UUID postId, UUID userId) {
        ensureTeacher(courseId, userId);
        requireTaskPostInCourse(courseId, postId);

        GradingConfig config = gradingConfigRepository.findByPostId(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Grading config not found"));
        gradingConfigRepository.delete(config);
    }

    public CriteriaGradeResultDto getCriteriaGrades(UUID courseId, UUID postId, UUID solutionId, UUID userId) {
        ensureTeacher(courseId, userId);
        requireTaskPostInCourse(courseId, postId);
        Solution solution = requireSolutionInPost(postId, solutionId);
        GradingConfig config = requireGradingConfig(postId);

        AssessmentResult result = assessmentResultRepository.findBySolutionId(solutionId).orElse(null);
        if (result != null) {
            return toResultDto(result);
        }

        // Return empty preview based on current config
        List<CriterionGradeResultItemDto> items = config.getCriteria().stream()
                .sorted(Comparator.comparingInt(Criterion::getSortOrder))
                .map(c -> CriterionGradeResultItemDto.builder()
                        .criterion(toCriterionConfigDto(c))
                        .value(BigDecimal.ZERO)
                        .computedPoints(BigDecimal.ZERO)
                        .build())
                .toList();

        return CriteriaGradeResultDto.builder()
                .solutionId(solutionId)
                .criteriaGrades(items)
                .modifierEffects(Collections.emptyList())
                .basicScore(BigDecimal.ZERO)
                .modifierDelta(null)
                .finalScore(null)
                .maxGrade(config.getMaxGrade())
                .isPublished(false)
                .gradedAt(null)
                .build();
    }

    @Transactional
    public CriteriaGradeResultDto upsertCriteriaGrades(UUID courseId, UUID postId, UUID solutionId,
                                                       CriteriaGradeSubmissionDto request, UUID userId) {
        ensureTeacher(courseId, userId);
        requireTaskPostInCourse(courseId, postId);
        Solution solution = requireSolutionInPost(postId, solutionId);
        GradingConfig config = requireGradingConfig(postId);

        AssessmentResult result = assessmentResultRepository.findBySolutionId(solutionId).orElse(null);
        if (result != null && Boolean.TRUE.equals(result.getIsPublished())) {
            throw new ForbiddenException("Published assessment cannot be modified directly. Use recalculate first.");
        }

        List<Criterion> criteria = criterionRepository.findByGradingConfigIdOrderBySortOrderAsc(config.getId());
        Map<UUID, Criterion> criterionMap = criteria.stream()
                .collect(Collectors.toMap(Criterion::getId, c -> c));

        Set<UUID> submittedCriterionIds = request.getGrades().stream()
                .map(CriterionGradeEntryDto::getCriterionId)
                .collect(Collectors.toSet());

        if (!submittedCriterionIds.equals(criterionMap.keySet())) {
            throw new BadRequestException("Grades must be submitted for exactly the configured criteria");
        }

        // Ensure version snapshot exists for current config
        GradingConfigVersion version = findOrCreateConfigVersion(config);

        if (result == null) {
            result = AssessmentResult.builder()
                    .solution(solution)
                    .configVersion(version)
                    .build();
        } else {
            result.setConfigVersion(version);
            result.getCriterionGrades().clear();
        }

        List<CriterionGradeResultItemDto> items = new ArrayList<>();
        Map<String, VersionedCriterion> versionedByTitle = version.getCriteria().stream()
                .collect(Collectors.toMap(VersionedCriterion::getTitle, c -> c));

        for (CriterionGradeEntryDto entry : request.getGrades()) {
            Criterion criterion = criterionMap.get(entry.getCriterionId());
            criterion.validateValue(entry.getValue());

            VersionedCriterion vc = versionedByTitle.get(criterion.getTitle());
            if (vc == null) {
                throw new BadRequestException("Criterion mismatch between config and version");
            }

            AssessmentCriterionGrade acg = AssessmentCriterionGrade.builder()
                    .assessmentResult(result)
                    .versionedCriterion(vc)
                    .value(entry.getValue())
                    .comment(entry.getComment())
                    .build();
            result.getCriterionGrades().add(acg);

            items.add(CriterionGradeResultItemDto.builder()
                    .criterion(toCriterionConfigDto(criterion))
                    .value(entry.getValue())
                    .computedPoints(vc.computePoints(entry.getValue()))
                    .comment(entry.getComment())
                    .build());
        }

        BigDecimal basicScore = result.computeBasicScore();
        List<ModifierEffectDto> effects = new ArrayList<>();
        BigDecimal modifierDelta = computeModifierDelta(version, solution, effects);
        BigDecimal rawFinal = basicScore.add(modifierDelta);
        BigDecimal finalScore = rawFinal.max(BigDecimal.ZERO).min(version.getMaxGrade());

        result.setBasicScore(basicScore);
        result.setModifierDelta(modifierDelta);
        result.setFinalScore(finalScore);
        result.setGradedAt(Instant.now());

        AssessmentResult saved = assessmentResultRepository.save(result);

        // Sync simple grade on Solution for backward compatibility
        solution.setGrade(finalScore.setScale(0, RoundingMode.HALF_UP).intValue());
        solution.setStatus(SolutionStatus.GRADED);
        solution.setGradedAt(Instant.now());
        solutionRepository.save(solution);

        return toResultDto(saved);
    }

    @Transactional
    public void setGradePublished(UUID courseId, UUID postId, UUID userId, boolean published) {
        ensureTeacher(courseId, userId);
        requireTaskPostInCourse(courseId, postId);
        requireGradingConfig(postId);

        List<AssessmentResult> results = assessmentResultRepository.findBySolutionPostId(postId);
        if (published) {
            List<AssessmentResult> unpublished = results.stream()
                    .filter(r -> !Boolean.TRUE.equals(r.getIsPublished()))
                    .toList();
            if (unpublished.isEmpty()) {
                throw new BadRequestException("No grades to publish");
            }
            for (AssessmentResult r : unpublished) {
                r.setIsPublished(true);
                assessmentResultRepository.save(r);
            }
        } else {
            for (AssessmentResult r : results) {
                r.setIsPublished(false);
                assessmentResultRepository.save(r);
            }
        }
    }

    public CriteriaGradeResultDto getGradeDecomposition(UUID courseId, UUID postId, UUID solutionId, UUID userId) {
        requireMember(courseId, userId);
        requireTaskPostInCourse(courseId, postId);
        Solution solution = requireSolutionInPost(postId, solutionId);
        GradingConfig config = requireGradingConfig(postId);

        CourseMember member = courseMemberRepository.findByCourseIdAndUserId(courseId, userId).orElseThrow();
        boolean isTeacher = member.getRole() == CourseRole.TEACHER;

        AssessmentResult result = assessmentResultRepository.findBySolutionId(solutionId).orElse(null);

        if (!isTeacher) {
            if (!solution.getStudent().getId().equals(userId)) {
                throw new ForbiddenException("You can only view your own grade decomposition");
            }
            if (result == null || !Boolean.TRUE.equals(result.getIsPublished())) {
                throw new ForbiddenException("Grade decomposition is not yet published by the teacher");
            }
        }

        if (result != null) {
            return toResultDto(result);
        }

        // Live preview for teacher when no assessment result exists yet
        List<CriterionGradeResultItemDto> items = config.getCriteria().stream()
                .sorted(Comparator.comparingInt(Criterion::getSortOrder))
                .map(c -> CriterionGradeResultItemDto.builder()
                        .criterion(toCriterionConfigDto(c))
                        .value(BigDecimal.ZERO)
                        .computedPoints(BigDecimal.ZERO)
                        .build())
                .toList();

        List<ModifierEffectDto> effects = new ArrayList<>();
        BigDecimal modifierDelta = computeModifierDelta(config, solution, effects);

        return CriteriaGradeResultDto.builder()
                .solutionId(solutionId)
                .criteriaGrades(items)
                .modifierEffects(effects)
                .basicScore(BigDecimal.ZERO)
                .modifierDelta(modifierDelta)
                .finalScore(modifierDelta.max(BigDecimal.ZERO).min(config.getMaxGrade()))
                .maxGrade(config.getMaxGrade())
                .isPublished(false)
                .gradedAt(null)
                .build();
    }

    @Transactional
    public CriteriaGradeResultDto recalculateAssessment(UUID courseId, UUID postId, UUID solutionId, UUID userId) {
        ensureTeacher(courseId, userId);
        requireTaskPostInCourse(courseId, postId);
        Solution solution = requireSolutionInPost(postId, solutionId);
        GradingConfig config = requireGradingConfig(postId);

        AssessmentResult result = assessmentResultRepository.findBySolutionId(solutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment result not found for this solution"));

        GradingConfigVersion version = findOrCreateConfigVersion(config);

        // Map old grades by title for transfer
        Map<String, AssessmentCriterionGrade> oldByTitle = result.getCriterionGrades().stream()
                .collect(Collectors.toMap(
                        g -> g.getVersionedCriterion().getTitle(),
                        g -> g,
                        (a, b) -> a));

        result.setConfigVersion(version);
        result.getCriterionGrades().clear();
        result.setIsPublished(false);

        Map<String, VersionedCriterion> versionedByTitle = version.getCriteria().stream()
                .collect(Collectors.toMap(VersionedCriterion::getTitle, c -> c));

        for (VersionedCriterion vc : version.getCriteria().stream().sorted(Comparator.comparingInt(VersionedCriterion::getSortOrder)).toList()) {
            AssessmentCriterionGrade old = oldByTitle.get(vc.getTitle());
            BigDecimal value = old != null ? old.getValue() : BigDecimal.ZERO;
            String comment = old != null ? old.getComment() : null;

            // Validate value against new criterion rules
            vc.validateValue(value);

            AssessmentCriterionGrade acg = AssessmentCriterionGrade.builder()
                    .assessmentResult(result)
                    .versionedCriterion(vc)
                    .value(value)
                    .comment(comment)
                    .build();
            result.getCriterionGrades().add(acg);
        }

        BigDecimal basicScore = result.computeBasicScore();
        List<ModifierEffectDto> effects = new ArrayList<>();
        BigDecimal modifierDelta = computeModifierDelta(version, solution, effects);
        BigDecimal rawFinal = basicScore.add(modifierDelta);
        BigDecimal finalScore = rawFinal.max(BigDecimal.ZERO).min(version.getMaxGrade());

        result.setBasicScore(basicScore);
        result.setModifierDelta(modifierDelta);
        result.setFinalScore(finalScore);
        result.setGradedAt(Instant.now());

        AssessmentResult saved = assessmentResultRepository.save(result);

        solution.setGrade(finalScore.setScale(0, RoundingMode.HALF_UP).intValue());
        solution.setStatus(SolutionStatus.GRADED);
        solution.setGradedAt(Instant.now());
        solutionRepository.save(solution);

        return toResultDto(saved);
    }

    private GradingConfigVersion findOrCreateConfigVersion(GradingConfig config) {
        List<Criterion> currentCriteria = criterionRepository.findByGradingConfigIdOrderBySortOrderAsc(config.getId());

        Optional<GradingConfigVersion> latestOpt = gradingConfigVersionRepository
                .findTopByPostIdOrderByVersionNumberDesc(config.getPost().getId());

        if (latestOpt.isPresent()) {
            GradingConfigVersion latest = latestOpt.get();
            List<VersionedCriterion> versioned = versionedCriterionRepository
                    .findByConfigVersionIdOrderBySortOrderAsc(latest.getId());

            if (versionsMatch(currentCriteria, versioned, config, latest)) {
                return latest;
            }

            GradingConfigVersion next = GradingConfigVersion.builder()
                    .post(config.getPost())
                    .versionNumber(latest.getVersionNumber() + 1)
                    .maxGrade(config.getMaxGrade())
                    .modifiersJson(config.getModifiersJson())
                    .build();

            List<VersionedCriterion> newVersioned = currentCriteria.stream()
                    .map(c -> VersionedCriterion.builder()
                            .type(c.getType())
                            .title(c.getTitle())
                            .maxPoints(c.getMaxPoints())
                            .weight(c.getWeight())
                            .sortOrder(c.getSortOrder())
                            .build())
                    .toList();
            next.replaceCriteria(newVersioned);
            return gradingConfigVersionRepository.save(next);
        }

        GradingConfigVersion first = GradingConfigVersion.builder()
                .post(config.getPost())
                .versionNumber(1)
                .maxGrade(config.getMaxGrade())
                .modifiersJson(config.getModifiersJson())
                .build();

        List<VersionedCriterion> newVersioned = currentCriteria.stream()
                .map(c -> VersionedCriterion.builder()
                        .type(c.getType())
                        .title(c.getTitle())
                        .maxPoints(c.getMaxPoints())
                        .weight(c.getWeight())
                        .sortOrder(c.getSortOrder())
                        .build())
                .toList();
        first.replaceCriteria(newVersioned);
        return gradingConfigVersionRepository.save(first);
    }

    private boolean versionsMatch(List<Criterion> current, List<VersionedCriterion> versioned,
                                  GradingConfig config, GradingConfigVersion version) {
        if (!config.getMaxGrade().equals(version.getMaxGrade())) {
            return false;
        }
        if (!Objects.equals(config.getModifiersJson(), version.getModifiersJson())) {
            return false;
        }
        if (current.size() != versioned.size()) {
            return false;
        }
        for (int i = 0; i < current.size(); i++) {
            Criterion c = current.get(i);
            VersionedCriterion v = versioned.get(i);
            if (!Objects.equals(c.getType(), v.getType())
                    || !Objects.equals(c.getTitle(), v.getTitle())
                    || !Objects.equals(c.getMaxPoints(), v.getMaxPoints())
                    || !Objects.equals(c.getWeight(), v.getWeight())
                    || !Objects.equals(c.getSortOrder(), v.getSortOrder())) {
                return false;
            }
        }
        return true;
    }

    private CriteriaGradeResultDto toResultDto(AssessmentResult result) {
        GradingConfigVersion version = result.getConfigVersion();
        List<VersionedCriterion> criteria = version.getCriteria().stream()
                .sorted(Comparator.comparingInt(VersionedCriterion::getSortOrder))
                .toList();

        Map<UUID, AssessmentCriterionGrade> gradeByCriterion = result.getCriterionGrades().stream()
                .collect(Collectors.toMap(g -> g.getVersionedCriterion().getId(), g -> g));

        List<CriterionGradeResultItemDto> items = new ArrayList<>();
        for (VersionedCriterion vc : criteria) {
            AssessmentCriterionGrade grade = gradeByCriterion.get(vc.getId());
            BigDecimal value = grade != null ? grade.getValue() : BigDecimal.ZERO;
            BigDecimal computed = vc.computePoints(value);
            items.add(CriterionGradeResultItemDto.builder()
                    .criterion(CriterionConfigDto.builder()
                            .id(vc.getId())
                            .type(vc.getType())
                            .title(vc.getTitle())
                            .maxPoints(vc.getMaxPoints())
                            .weight(vc.getWeight())
                            .sortOrder(vc.getSortOrder())
                            .build())
                    .value(value)
                    .computedPoints(computed)
                    .comment(grade != null ? grade.getComment() : null)
                    .build());
        }

        List<ModifierEffectDto> effects = new ArrayList<>();
        BigDecimal modifierDelta = result.getModifierDelta();
        if (modifierDelta == null) {
            modifierDelta = computeModifierDelta(version, result.getSolution(), effects);
        } else {
            // Recompute effects for display only (delta is already stored)
            computeModifierDelta(version, result.getSolution(), effects);
        }

        BigDecimal basicScore = result.getBasicScore() != null ? result.getBasicScore() : result.computeBasicScore();
        BigDecimal finalScore = result.getFinalScore();
        if (finalScore == null) {
            BigDecimal rawFinal = basicScore.add(modifierDelta);
            finalScore = rawFinal.max(BigDecimal.ZERO).min(version.getMaxGrade());
        }

        return CriteriaGradeResultDto.builder()
                .solutionId(result.getSolution().getId())
                .criteriaGrades(items)
                .modifierEffects(effects)
                .basicScore(basicScore)
                .modifierDelta(modifierDelta)
                .finalScore(finalScore)
                .maxGrade(version.getMaxGrade())
                .isPublished(result.getIsPublished())
                .gradedAt(result.getGradedAt())
                .build();
    }

    private BigDecimal computeModifierDelta(GradingConfig config, Solution solution, List<ModifierEffectDto> effects) {
        if (config.getModifiersJson() == null || config.getModifiersJson().isBlank()) {
            return BigDecimal.ZERO;
        }
        ModifierConfigDto modifiers;
        try {
            modifiers = objectMapper.readValue(config.getModifiersJson(), ModifierConfigDto.class);
        } catch (JsonProcessingException e) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;

        DeadlineModifierDto deadline = modifiers.getDeadlines();
        if (deadline != null && Boolean.TRUE.equals(deadline.getEnabled())) {
            BigDecimal delta = computeDeadlineDelta(deadline, solution.getSubmittedAt());
            if (delta.compareTo(BigDecimal.ZERO) != 0) {
                effects.add(ModifierEffectDto.builder()
                        .modifierType("DEADLINE")
                        .description(delta.compareTo(BigDecimal.ZERO) > 0 ? "Early submission bonus" : "Late submission penalty")
                        .delta(delta)
                        .build());
                total = total.add(delta);
            }
        }

        return total;
    }

    private BigDecimal computeModifierDelta(GradingConfigVersion version, Solution solution, List<ModifierEffectDto> effects) {
        if (version.getModifiersJson() == null || version.getModifiersJson().isBlank()) {
            return BigDecimal.ZERO;
        }
        ModifierConfigDto modifiers;
        try {
            modifiers = objectMapper.readValue(version.getModifiersJson(), ModifierConfigDto.class);
        } catch (JsonProcessingException e) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;

        DeadlineModifierDto deadline = modifiers.getDeadlines();
        if (deadline != null && Boolean.TRUE.equals(deadline.getEnabled())) {
            BigDecimal delta = computeDeadlineDelta(deadline, solution.getSubmittedAt());
            if (delta.compareTo(BigDecimal.ZERO) != 0) {
                effects.add(ModifierEffectDto.builder()
                        .modifierType("DEADLINE")
                        .description(delta.compareTo(BigDecimal.ZERO) > 0 ? "Early submission bonus" : "Late submission penalty")
                        .delta(delta)
                        .build());
                total = total.add(delta);
            }
        }

        return total;
    }

    private BigDecimal computeDeadlineDelta(DeadlineModifierDto deadline, Instant submittedAt) {
        if (submittedAt == null) return BigDecimal.ZERO;

        Instant hardDeadline = deadline.getHardDeadline();
        Instant softDeadline = deadline.getSoftDeadline();

        if (hardDeadline != null && submittedAt.isAfter(hardDeadline)) {
            long daysLate = ChronoUnit.DAYS.between(hardDeadline, submittedAt) + 1;
            if (deadline.getMaxLatePenaltyDays() != null) {
                daysLate = Math.min(daysLate, deadline.getMaxLatePenaltyDays());
            }
            BigDecimal penaltyPerDay = deadline.getLatePenaltyPerDay() != null
                    ? deadline.getLatePenaltyPerDay() : BigDecimal.ZERO;
            return penaltyPerDay.multiply(BigDecimal.valueOf(daysLate)).negate();
        }

        if (softDeadline != null && !submittedAt.isAfter(softDeadline)) {
            if (deadline.getEarlySubmissionBonusPerDay() != null
                    && deadline.getEarlySubmissionBonusPerDay().compareTo(BigDecimal.ZERO) > 0) {
                long daysEarly = ChronoUnit.DAYS.between(submittedAt, softDeadline);
                return deadline.getEarlySubmissionBonusPerDay().multiply(BigDecimal.valueOf(daysEarly));
            }
            if (deadline.getSoftDeadlineBonus() != null) {
                return deadline.getSoftDeadlineBonus();
            }
        }

        return BigDecimal.ZERO;
    }

    private GradingConfigDto toConfigDto(GradingConfig config) {
        ModifierConfigDto modifiers = null;
        if (config.getModifiersJson() != null && !config.getModifiersJson().isBlank()) {
            try {
                modifiers = objectMapper.readValue(config.getModifiersJson(), ModifierConfigDto.class);
            } catch (JsonProcessingException e) {
                modifiers = null;
            }
        }

        return GradingConfigDto.builder()
                .postId(config.getPost().getId())
                .maxGrade(config.getMaxGrade())
                .criteria(config.getCriteria().stream()
                        .sorted(Comparator.comparingInt(Criterion::getSortOrder))
                        .map(this::toCriterionConfigDto)
                        .toList())
                .modifiers(modifiers)
                .resultsVisible(config.getResultsVisible())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    private CriterionConfigDto toCriterionConfigDto(Criterion criterion) {
        return CriterionConfigDto.builder()
                .id(criterion.getId())
                .type(criterion.getType())
                .title(criterion.getTitle())
                .maxPoints(criterion.getMaxPoints())
                .weight(criterion.getWeight())
                .sortOrder(criterion.getSortOrder())
                .build();
    }

    private void ensureTeacher(UUID courseId, UUID userId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Course not found");
        }
        CourseMember member = courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this course"));
        if (member.getRole() != CourseRole.TEACHER) {
            throw new ForbiddenException("Only teachers can manage grading configuration");
        }
    }

    private void requireMember(UUID courseId, UUID userId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Course not found");
        }
        courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this course"));
    }

    private Post requireTaskPostInCourse(UUID courseId, UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        if (!post.getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Post not found");
        }
        if (post.getType() != PostType.TASK) {
            throw new BadRequestException("Grading configuration is available only for task posts");
        }
        return post;
    }

    private Solution requireSolutionInPost(UUID postId, UUID solutionId) {
        Solution solution = solutionRepository.findById(solutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Solution not found"));
        if (!solution.getPost().getId().equals(postId)) {
            throw new ResourceNotFoundException("Solution not found");
        }
        return solution;
    }

    private GradingConfig requireGradingConfig(UUID postId) {
        return gradingConfigRepository.findByPostId(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Grading configuration not found for this post"));
    }
}
