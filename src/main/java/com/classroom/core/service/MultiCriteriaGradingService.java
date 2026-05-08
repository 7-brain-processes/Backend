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
    private final CriterionGradeRepository criterionGradeRepository;
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

        List<CriterionGrade> grades = criterionGradeRepository.findBySolutionId(solutionId);
        Map<UUID, CriterionGrade> gradeByCriterion = grades.stream()
                .collect(Collectors.toMap(g -> g.getCriterion().getId(), g -> g));

        List<CriterionGradeResultItemDto> items = new ArrayList<>();

        for (Criterion criterion : config.getCriteria()) {
            CriterionGrade grade = gradeByCriterion.get(criterion.getId());
            BigDecimal value = grade != null ? grade.getValue() : BigDecimal.ZERO;
            BigDecimal computed = criterion.computePoints(value);

            items.add(CriterionGradeResultItemDto.builder()
                    .criterion(toCriterionConfigDto(criterion))
                    .value(value)
                    .computedPoints(computed)
                    .comment(grade != null ? grade.getComment() : null)
                    .build());
        }

        BigDecimal basicScore = config.computeBasicScore(grades);

        Instant latestGradedAt = grades.stream()
                .map(CriterionGrade::getUpdatedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return CriteriaGradeResultDto.builder()
                .solutionId(solutionId)
                .criteriaGrades(items)
                .modifierEffects(Collections.emptyList())
                .basicScore(basicScore)
                .modifierDelta(null)
                .finalScore(null)
                .maxGrade(config.getMaxGrade())
                .isPublished(config.getResultsVisible())
                .gradedAt(latestGradedAt)
                .build();
    }

    @Transactional
    public CriteriaGradeResultDto upsertCriteriaGrades(UUID courseId, UUID postId, UUID solutionId,
                                                       CriteriaGradeSubmissionDto request, UUID userId) {
        ensureTeacher(courseId, userId);
        requireTaskPostInCourse(courseId, postId);
        Solution solution = requireSolutionInPost(postId, solutionId);
        GradingConfig config = requireGradingConfig(postId);

        List<Criterion> criteria = criterionRepository.findByGradingConfigIdOrderBySortOrderAsc(config.getId());
        Map<UUID, Criterion> criterionMap = criteria.stream()
                .collect(Collectors.toMap(Criterion::getId, c -> c));

        Set<UUID> submittedCriterionIds = request.getGrades().stream()
                .map(CriterionGradeEntryDto::getCriterionId)
                .collect(Collectors.toSet());

        if (!submittedCriterionIds.equals(criterionMap.keySet())) {
            throw new BadRequestException("Grades must be submitted for exactly the configured criteria");
        }

        criterionGradeRepository.deleteBySolutionId(solutionId);

        List<CriterionGradeResultItemDto> items = new ArrayList<>();
        List<CriterionGrade> savedGrades = new ArrayList<>();

        for (CriterionGradeEntryDto entry : request.getGrades()) {
            Criterion criterion = criterionMap.get(entry.getCriterionId());
            criterion.validateValue(entry.getValue());

            CriterionGrade grade = CriterionGrade.builder()
                    .solution(solution)
                    .criterion(criterion)
                    .value(entry.getValue())
                    .comment(entry.getComment())
                    .build();
            savedGrades.add(criterionGradeRepository.save(grade));

            BigDecimal computed = criterion.computePoints(entry.getValue());
            items.add(CriterionGradeResultItemDto.builder()
                    .criterion(toCriterionConfigDto(criterion))
                    .value(entry.getValue())
                    .computedPoints(computed)
                    .comment(entry.getComment())
                    .build());
        }

        BigDecimal basicScore = config.computeBasicScore(savedGrades);

        return CriteriaGradeResultDto.builder()
                .solutionId(solutionId)
                .criteriaGrades(items)
                .modifierEffects(Collections.emptyList())
                .basicScore(basicScore)
                .modifierDelta(null)
                .finalScore(null)
                .maxGrade(config.getMaxGrade())
                .isPublished(false)
                .gradedAt(Instant.now())
                .build();
    }

    @Transactional
    public void setGradePublished(UUID courseId, UUID postId, UUID userId, boolean published) {
        ensureTeacher(courseId, userId);
        requireTaskPostInCourse(courseId, postId);
        GradingConfig config = requireGradingConfig(postId);
        config.setResultsVisible(published);
        gradingConfigRepository.save(config);
    }

    public CriteriaGradeResultDto getGradeDecomposition(UUID courseId, UUID postId, UUID solutionId, UUID userId) {
        requireMember(courseId, userId);
        requireTaskPostInCourse(courseId, postId);
        Solution solution = requireSolutionInPost(postId, solutionId);
        GradingConfig config = requireGradingConfig(postId);

        CourseMember member = courseMemberRepository.findByCourseIdAndUserId(courseId, userId).orElseThrow();
        boolean isTeacher = member.getRole() == CourseRole.TEACHER;

        if (!isTeacher) {
            if (!solution.getStudent().getId().equals(userId)) {
                throw new ForbiddenException("You can only view your own grade decomposition");
            }
            if (!Boolean.TRUE.equals(config.getResultsVisible())) {
                throw new ForbiddenException("Grade decomposition is not yet published by the teacher");
            }
        }

        List<CriterionGrade> grades = criterionGradeRepository.findBySolutionId(solutionId);
        Map<UUID, CriterionGrade> gradeByCriterion = grades.stream()
                .collect(Collectors.toMap(g -> g.getCriterion().getId(), g -> g));

        List<CriterionGradeResultItemDto> items = new ArrayList<>();
        for (Criterion criterion : config.getCriteria()) {
            CriterionGrade grade = gradeByCriterion.get(criterion.getId());
            BigDecimal value = grade != null ? grade.getValue() : BigDecimal.ZERO;
            BigDecimal computed = criterion.computePoints(value);
            items.add(CriterionGradeResultItemDto.builder()
                    .criterion(toCriterionConfigDto(criterion))
                    .value(value)
                    .computedPoints(computed)
                    .comment(grade != null ? grade.getComment() : null)
                    .build());
        }

        BigDecimal basicScore = config.computeBasicScore(grades);

        List<ModifierEffectDto> effects = new ArrayList<>();
        BigDecimal modifierDelta = computeModifierDelta(config, solution, effects);

        BigDecimal rawFinal = basicScore.add(modifierDelta);
        BigDecimal finalScore = rawFinal.max(BigDecimal.ZERO).min(config.getMaxGrade());

        Instant latestGradedAt = grades.stream()
                .map(CriterionGrade::getUpdatedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return CriteriaGradeResultDto.builder()
                .solutionId(solutionId)
                .criteriaGrades(items)
                .modifierEffects(effects)
                .basicScore(basicScore)
                .modifierDelta(modifierDelta)
                .finalScore(finalScore)
                .maxGrade(config.getMaxGrade())
                .isPublished(config.getResultsVisible())
                .gradedAt(latestGradedAt)
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
