package com.classroom.core.dto.grading;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadlineModifierDto {
    private Boolean enabled;
    private Instant softDeadline;
    private Instant hardDeadline;
    private BigDecimal softDeadlineBonus;
    private BigDecimal earlySubmissionBonusPerDay;
    private BigDecimal latePenaltyPerDay;
    private Integer maxLatePenaltyDays;
}
