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
public class GradingConfigDto {
    private UUID postId;
    private BigDecimal maxGrade;
    private List<CriterionConfigDto> criteria;
    private ModifierConfigDto modifiers;
    private Boolean resultsVisible;
    private Instant createdAt;
    private Instant updatedAt;
}
