package com.classroom.core.dto.grading;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriteriaGradeResultDto {
    private UUID solutionId;
    private List<CriterionGradeResultItemDto> criteriaGrades;
    private List<ModifierEffectDto> modifierEffects;
    private BigDecimal basicScore;
    private BigDecimal modifierDelta;
    private BigDecimal finalScore;
    private BigDecimal maxGrade;
    private Boolean isPublished;
    private Instant gradedAt;
}
