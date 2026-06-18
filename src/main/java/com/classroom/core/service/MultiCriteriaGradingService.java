package com.classroom.core.service;

import com.classroom.core.dto.grading.*;
import com.classroom.core.event.DomainEvent;
import com.classroom.core.event.SolutionGradedEvent;
import com.classroom.core.exception.BadRequestException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.*;
import com.classroom.core.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
public class MultiCriteriaGradingService {

    private final GradingConfigRepository gradingConfigRepository;
    private final CriterionRepository criterionRepository;
    private final AssessmentResultRepository assessmentResultRepository;
    private final AssessmentCriterionGradeRepository assessmentCriterionGradeRepository;
    private final ObjectMapper objectMapper;
    private final GradingGuard guard;
    private final GradingDtoMapper mapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PeerReviewService peerReviewService;
    private final GradingConfigVersionService gradingConfigVersionService;

    public GradingConfigDto getGradingConfig(UUID courseId, UUID postId, UUID userId) {
        guard.requireMember(courseId, userId);
        guard.requireTaskPostInCourse(courseId, postId);

        GradingConfig config = gradingConfigRepository.findByPostId(postId).orElse(null);
        if (config == null) {
            return GradingConfigDto.builder().postId(postId).build();
        }
        boolean isTeacher = guard.isTeacher(courseId, userId);
        return mapper.toConfigDto(config, isTeacher);
    }

    @Transactional
    public GradingConfigDto upsertGradingConfig(UUID courseId, UUID postId,
                                                UpsertGradingConfigRequest request, UUID userId) {
        guard.ensureTeacher(courseId, userId);
        Post post = guard.requireTaskPostInCourse(courseId, postId);

        if (request.getMaxGrade().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("maxGrade must be greater than 0");
        }

        long peerReviewCriteriaCount = request.getCriteria().stream()
                .filter(c -> c.getType() == CriterionType.PEER_REVIEW)
                .count();
        if (peerReviewCriteriaCount > 1) {
            throw new BadRequestException("Only one PEER_REVIEW criterion is allowed per task");
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
        criterionRepository.saveAll(saved.getCriteria());

        List<Criterion> savedCriteria = criterionRepository.findByGradingConfigIdOrderBySortOrderAsc(saved.getId());
        List<CriterionConfigDto> criterionDtos = request.getCriteria();
        for (int i = 0; i < criterionDtos.size() && i < savedCriteria.size(); i++) {
            CriterionConfigDto dto = criterionDtos.get(i);
            if (dto.getType() == CriterionType.PEER_REVIEW && dto.getPeerReviewConfigRequest() != null) {
                peerReviewService.createOrUpdateConfig(savedCriteria.get(i), dto.getPeerReviewConfigRequest());
            }
        }

        return mapper.toConfigDto(saved);
    }

    @Transactional
    public void deleteGradingConfig(UUID courseId, UUID postId, UUID userId) {
        guard.ensureTeacher(courseId, userId);
        guard.requireTaskPostInCourse(courseId, postId);

        GradingConfig config = guard.requireGradingConfig(postId);
        gradingConfigRepository.delete(config);
    }

    public CriteriaGradeResultDto getCriteriaGrades(UUID courseId, UUID postId, UUID solutionId, UUID userId) {
        guard.ensureTeacher(courseId, userId);
        guard.requireTaskPostInCourse(courseId, postId);
        Solution solution = guard.requireSolutionInPost(postId, solutionId);
        GradingConfig config = guard.requireGradingConfig(postId);

        AssessmentResult result = assessmentResultRepository.findBySolutionId(solutionId).orElse(null);
        if (result != null) {
            return mapper.toResultDto(result);
        }

        return mapper.buildPreview(solution, config, mapper.toModifierConfig(config));
    }

    @Transactional
    public CriteriaGradeResultDto upsertCriteriaGrades(UUID courseId, UUID postId, UUID solutionId,
                                                       CriteriaGradeSubmissionDto request, UUID userId) {
        guard.ensureTeacher(courseId, userId);
        guard.requireTaskPostInCourse(courseId, postId);
        Solution solution = guard.requireSolutionInPost(postId, solutionId);
        GradingConfig config = guard.requireGradingConfig(postId);

        AssessmentResult result = assessmentResultRepository.findBySolutionId(solutionId).orElse(null);
        if (result != null && Boolean.TRUE.equals(result.getIsPublished())) {
            throw new ForbiddenException("Published assessment cannot be modified directly. Use recalculate first.");
        }

        List<Criterion> criteria = criterionRepository.findByGradingConfigIdOrderBySortOrderAsc(config.getId());
        validateGradeSubmission(criteria, request);

        GradingConfigVersion version = findOrCreateConfigVersion(config);

        if (result == null) {
            result = AssessmentResult.builder()
                    .solution(solution)
                    .configVersion(version)
                    .build();
        } else {
            result.setConfigVersion(version);
        }

        List<AssessmentCriterionGrade> grades = buildAssessmentGrades(request, criteria, version, result);
        preservePeerReviewGrade(grades, version, result);
        ModifierConfig modifierConfig = mapper.toModifierConfig(version);
        result.assess(grades, modifierConfig);

        AssessmentResult saved = assessmentResultRepository.save(result);
        publishSolutionGradedEvent(saved);

        return mapper.toResultDto(saved);
    }

    @Transactional
    public void setGradePublished(UUID courseId, UUID postId, UUID userId, boolean published) {
        guard.ensureTeacher(courseId, userId);
        guard.requireTaskPostInCourse(courseId, postId);
        guard.requireGradingConfig(postId);

        List<AssessmentResult> results = assessmentResultRepository.findBySolutionPostId(postId);
        if (published) {
            List<AssessmentResult> unpublished = results.stream()
                    .filter(r -> !Boolean.TRUE.equals(r.getIsPublished()))
                    .toList();
            if (unpublished.isEmpty()) {
                throw new BadRequestException("No grades to publish");
            }
            for (AssessmentResult r : unpublished) {
                r.publish();
                assessmentResultRepository.save(r);
                publishEvents(r.pullDomainEvents());
            }
        } else {
            for (AssessmentResult r : results) {
                r.unpublish();
                assessmentResultRepository.save(r);
            }
        }
    }

    public CriteriaGradeResultDto getGradeDecomposition(UUID courseId, UUID postId, UUID solutionId, UUID userId) {
        guard.requireMember(courseId, userId);
        guard.requireTaskPostInCourse(courseId, postId);
        Solution solution = guard.requireSolutionInPost(postId, solutionId);
        GradingConfig config = guard.requireGradingConfig(postId);

        boolean isTeacher = guard.isTeacher(courseId, userId);
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
            return mapper.toResultDto(result);
        }

        return mapper.buildPreview(solution, config, mapper.toModifierConfig(config));
    }

    @Transactional
    public CriteriaGradeResultDto recalculateAssessment(UUID courseId, UUID postId, UUID solutionId, UUID userId) {
        guard.ensureTeacher(courseId, userId);
        guard.requireTaskPostInCourse(courseId, postId);
        Solution solution = guard.requireSolutionInPost(postId, solutionId);
        GradingConfig config = guard.requireGradingConfig(postId);

        AssessmentResult result = assessmentResultRepository.findBySolutionId(solutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment result not found for this solution"));

        GradingConfigVersion version = findOrCreateConfigVersion(config);

        Map<String, AssessmentCriterionGrade> oldByTitle = result.getCriterionGrades().stream()
                .collect(Collectors.toMap(
                        g -> g.getVersionedCriterion().getTitle(),
                        g -> g,
                        (a, b) -> a));

        List<AssessmentCriterionGrade> newGrades = new ArrayList<>();
        for (VersionedCriterion vc : version.getCriteria().stream()
                .sorted(Comparator.comparingInt(VersionedCriterion::getSortOrder))
                .toList()) {
            AssessmentCriterionGrade old = oldByTitle.get(vc.getTitle());
            BigDecimal value = old != null ? old.getValue() : BigDecimal.ZERO;
            String comment = old != null ? old.getComment() : null;

            vc.validateValue(value);

            newGrades.add(AssessmentCriterionGrade.builder()
                    .assessmentResult(result)
                    .versionedCriterion(vc)
                    .value(value)
                    .comment(comment)
                    .build());
        }

        result.setConfigVersion(version);
        result.unpublish();
        ModifierConfig modifierConfig = mapper.toModifierConfig(version);
        result.assess(newGrades, modifierConfig);

        AssessmentResult saved = assessmentResultRepository.save(result);
        publishSolutionGradedEvent(saved);

        return mapper.toResultDto(saved);
    }

    private GradingConfigVersion findOrCreateConfigVersion(GradingConfig config) {
        return gradingConfigVersionService.findOrCreateVersion(config);
    }

    private void validateGradeSubmission(List<Criterion> criteria, CriteriaGradeSubmissionDto request) {
       Set<UUID> teacherRequiredIds = criteria.stream()
                .filter(c -> c.getType() != CriterionType.PEER_REVIEW)
                .map(Criterion::getId)
                .collect(Collectors.toSet());

        Set<UUID> submittedCriterionIds = request.getGrades().stream()
                .map(CriterionGradeEntryDto::getCriterionId)
                .collect(Collectors.toSet());

        if (!submittedCriterionIds.equals(teacherRequiredIds)) {
            throw new BadRequestException("Grades must be submitted for exactly the non-peer-review criteria");
        }
    }

    private List<AssessmentCriterionGrade> buildAssessmentGrades(CriteriaGradeSubmissionDto request,
                                                                  List<Criterion> criteria,
                                                                  GradingConfigVersion version,
                                                                  AssessmentResult result) {
        Map<UUID, Criterion> criterionMap = criteria.stream()
                .filter(c -> c.getType() != CriterionType.PEER_REVIEW)
                .collect(Collectors.toMap(Criterion::getId, c -> c));
        Map<String, VersionedCriterion> versionedByTitle = version.getCriteria().stream()
                .collect(Collectors.toMap(VersionedCriterion::getTitle, c -> c));

        List<AssessmentCriterionGrade> grades = new ArrayList<>();
        for (CriterionGradeEntryDto entry : request.getGrades()) {
            Criterion criterion = criterionMap.get(entry.getCriterionId());
            if (criterion == null) {
                throw new BadRequestException("Unknown criterion id: " + entry.getCriterionId());
            }
            criterion.validateValue(entry.getValue());

            VersionedCriterion vc = versionedByTitle.get(criterion.getTitle());
            if (vc == null) {
                throw new BadRequestException("Criterion mismatch between config and version");
            }

            grades.add(AssessmentCriterionGrade.builder()
                    .assessmentResult(result)
                    .versionedCriterion(vc)
                    .value(entry.getValue())
                    .comment(entry.getComment())
                    .build());
        }
        return grades;
    }

    /**
     * Keeps any previously applied peer-review score when a teacher submits or updates
     * the non-peer-review criteria. Without this, the PEER_REVIEW criterion grade would be
     * wiped out by {@link AssessmentResult#replaceCriterionGrades(List)}.
     */
    private void preservePeerReviewGrade(List<AssessmentCriterionGrade> grades,
                                         GradingConfigVersion version,
                                         AssessmentResult result) {
        Optional<VersionedCriterion> peerReviewVc = version.getCriteria().stream()
                .filter(vc -> vc.getType() == CriterionType.PEER_REVIEW)
                .findFirst();
        if (peerReviewVc.isEmpty()) {
            return;
        }

        BigDecimal existingValue = Optional.ofNullable(result)
                .map(AssessmentResult::getCriterionGrades)
                .flatMap(list -> list.stream()
                        .filter(g -> g.getVersionedCriterion().getType() == CriterionType.PEER_REVIEW)
                        .findFirst())
                .map(AssessmentCriterionGrade::getValue)
                .orElse(BigDecimal.ZERO);

        BigDecimal maxPoints = peerReviewVc.get().getMaxPoints();
        BigDecimal value = existingValue.max(BigDecimal.ZERO).min(maxPoints);

        grades.add(AssessmentCriterionGrade.builder()
                .assessmentResult(result)
                .versionedCriterion(peerReviewVc.get())
                .value(value)
                .build());
    }

    private void publishSolutionGradedEvent(AssessmentResult result) {
        eventPublisher.publishEvent(new SolutionGradedEvent(
                result.getSolution().getId(),
                result.getFinalScore().setScale(0, RoundingMode.HALF_UP).intValue(),
                result.getGradedAt(),
                Instant.now()
        ));
    }

    private void publishEvents(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            eventPublisher.publishEvent(event);
        }
    }
}
