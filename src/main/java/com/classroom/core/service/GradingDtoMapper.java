package com.classroom.core.service;

import com.classroom.core.dto.grading.*;
import com.classroom.core.dto.peerreview.PeerReviewConfigDto;
import com.classroom.core.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GradingDtoMapper {

    private final ObjectMapper objectMapper;

    public GradingConfigDto toConfigDto(GradingConfig config) {
        return toConfigDto(config, true);
    }

    public GradingConfigDto toConfigDto(GradingConfig config, boolean isTeacher) {
        ModifierConfigDto modifiers = null;
        if (config.getModifiersJson() != null && !config.getModifiersJson().isBlank()) {
            try {
                modifiers = objectMapper.readValue(config.getModifiersJson(), ModifierConfigDto.class);
            } catch (JsonProcessingException e) {
                modifiers = null;
            }
        }

        final boolean ft = isTeacher;
        return GradingConfigDto.builder()
                .postId(config.getPost().getId())
                .maxGrade(config.getMaxGrade())
                .criteria(config.getCriteria().stream()
                        .sorted(Comparator.comparingInt(Criterion::getSortOrder))
                        .map(c -> toCriterionConfigDto(c, ft))
                        .toList())
                .modifiers(modifiers)
                .resultsVisible(config.getResultsVisible())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    public CriterionConfigDto toCriterionConfigDto(Criterion criterion) {
        return toCriterionConfigDto(criterion, true);
    }

    public CriterionConfigDto toCriterionConfigDto(Criterion criterion, boolean isTeacher) {
        PeerReviewConfigDto peerReviewConfigDto = null;
        if (criterion.getType() == CriterionType.PEER_REVIEW && criterion.getPeerReviewConfig() != null) {
            peerReviewConfigDto = toPeerReviewConfigDto(criterion.getPeerReviewConfig(), isTeacher);
        }
        return CriterionConfigDto.builder()
                .id(criterion.getId())
                .type(criterion.getType())
                .title(criterion.getTitle())
                .maxPoints(criterion.getMaxPoints())
                .weight(criterion.getWeight())
                .sortOrder(criterion.getSortOrder())
                .peerReviewConfig(peerReviewConfigDto)
                .build();
    }

    private PeerReviewConfigDto toPeerReviewConfigDto(PeerReviewConfig config, boolean isTeacher) {
        return PeerReviewConfigDto.builder()
                .id(config.getId())
                .reviewersCount(config.getReviewersCount())
                .scoringStrategy(config.getScoringStrategy())
                .firstDeadline(config.getFirstDeadline())
                .secondDeadline(isTeacher ? config.getSecondDeadline() : null)
                .redistributionFactor(config.getRedistributionFactor())
                .build();
    }

    public CriterionConfigDto toCriterionConfigDto(VersionedCriterion vc) {
        return CriterionConfigDto.builder()
                .id(vc.getId())
                .type(vc.getType())
                .title(vc.getTitle())
                .maxPoints(vc.getMaxPoints())
                .weight(vc.getWeight())
                .sortOrder(vc.getSortOrder())
                .build();
    }

    public CriteriaGradeResultDto toResultDto(AssessmentResult result) {
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
                    .criterion(toCriterionConfigDto(vc))
                    .value(value)
                    .computedPoints(computed)
                    .comment(grade != null ? grade.getComment() : null)
                    .build());
        }

        BigDecimal basicScore = result.getBasicScore() != null ? result.getBasicScore() : result.computeBasicScore();
        BigDecimal modifierDelta = result.getModifierDelta();
        BigDecimal finalScore = result.getFinalScore();
        if (finalScore == null) {
            BigDecimal rawFinal = basicScore.add(modifierDelta);
            finalScore = rawFinal.max(BigDecimal.ZERO).min(version.getMaxGrade());
        }

        ModifierConfig modifierConfig = toModifierConfig(version);
        List<ModifierEffectDto> effects = computeModifierEffects(modifierConfig, result.getSolution().getSubmittedAt());

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

    public CriteriaGradeResultDto buildPreview(Solution solution, GradingConfig config, ModifierConfig modifierConfig) {
        List<CriterionGradeResultItemDto> items = config.getCriteria().stream()
                .sorted(Comparator.comparingInt(Criterion::getSortOrder))
                .map(c -> CriterionGradeResultItemDto.builder()
                        .criterion(toCriterionConfigDto(c))
                        .value(BigDecimal.ZERO)
                        .computedPoints(BigDecimal.ZERO)
                        .build())
                .toList();

        BigDecimal modifierDelta = modifierConfig != null
                ? modifierConfig.computeTotalDelta(solution.getSubmittedAt())
                : BigDecimal.ZERO;
        Score score = Score.of(modifierDelta, config.getMaxGrade()).clamp();
        List<ModifierEffectDto> effects = computeModifierEffects(modifierConfig, solution.getSubmittedAt());

        return CriteriaGradeResultDto.builder()
                .solutionId(solution.getId())
                .criteriaGrades(items)
                .modifierEffects(effects)
                .basicScore(BigDecimal.ZERO)
                .modifierDelta(modifierDelta)
                .finalScore(score.getValue())
                .maxGrade(config.getMaxGrade())
                .isPublished(false)
                .gradedAt(null)
                .build();
    }

    public ModifierConfig toModifierConfig(GradingConfig config) {
        if (config.getModifiersJson() == null || config.getModifiersJson().isBlank()) {
            return new ModifierConfig(null);
        }
        try {
            ModifierConfigDto dto = objectMapper.readValue(config.getModifiersJson(), ModifierConfigDto.class);
            return toModifierConfig(dto);
        } catch (JsonProcessingException e) {
            return new ModifierConfig(null);
        }
    }

    public ModifierConfig toModifierConfig(GradingConfigVersion version) {
        if (version.getModifiersJson() == null || version.getModifiersJson().isBlank()) {
            return new ModifierConfig(null);
        }
        try {
            ModifierConfigDto dto = objectMapper.readValue(version.getModifiersJson(), ModifierConfigDto.class);
            return toModifierConfig(dto);
        } catch (JsonProcessingException e) {
            return new ModifierConfig(null);
        }
    }

    public ModifierConfig toModifierConfig(ModifierConfigDto dto) {
        if (dto == null || dto.getDeadlines() == null) {
            return new ModifierConfig(null);
        }
        DeadlineModifierDto d = dto.getDeadlines();
        DeadlineModifier deadline = new DeadlineModifier(
                Boolean.TRUE.equals(d.getEnabled()),
                d.getSoftDeadline(),
                d.getHardDeadline(),
                d.getSoftDeadlineBonus(),
                d.getEarlySubmissionBonusPerDay(),
                d.getLatePenaltyPerDay(),
                d.getMaxLatePenaltyDays()
        );
        return new ModifierConfig(deadline);
    }

    public List<ModifierEffectDto> computeModifierEffects(ModifierConfig modifierConfig, Instant submittedAt) {
        List<ModifierEffectDto> effects = new ArrayList<>();
        if (modifierConfig == null || modifierConfig.getDeadline() == null) {
            return effects;
        }
        DeadlineModifier deadline = modifierConfig.getDeadline();
        if (!deadline.isEnabled()) {
            return effects;
        }
        BigDecimal delta = deadline.computeDelta(submittedAt);
        if (delta.compareTo(BigDecimal.ZERO) != 0) {
            effects.add(ModifierEffectDto.builder()
                    .modifierType("DEADLINE")
                    .description(delta.compareTo(BigDecimal.ZERO) > 0 ? "Early submission bonus" : "Late submission penalty")
                    .delta(delta)
                    .build());
        }
        return effects;
    }
}
