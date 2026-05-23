package com.classroom.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Immutable value object representing deadline-based grade modifiers.
 * Computes the delta (bonus or penalty) based on submission time.
 */
@Getter
@AllArgsConstructor
public class DeadlineModifier {

    private final boolean enabled;
    private final Instant softDeadline;
    private final Instant hardDeadline;
    private final BigDecimal softDeadlineBonus;
    private final BigDecimal earlySubmissionBonusPerDay;
    private final BigDecimal latePenaltyPerDay;
    private final Integer maxLatePenaltyDays;

    public BigDecimal computeDelta(Instant submittedAt) {
        if (!enabled || submittedAt == null) {
            return BigDecimal.ZERO;
        }

        if (hardDeadline != null && submittedAt.isAfter(hardDeadline)) {
            long daysLate = ChronoUnit.DAYS.between(hardDeadline, submittedAt) + 1;
            if (maxLatePenaltyDays != null) {
                daysLate = Math.min(daysLate, maxLatePenaltyDays);
            }
            BigDecimal penaltyPerDay = latePenaltyPerDay != null ? latePenaltyPerDay : BigDecimal.ZERO;
            return penaltyPerDay.multiply(BigDecimal.valueOf(daysLate)).negate();
        }

        if (softDeadline != null && !submittedAt.isAfter(softDeadline)) {
            if (earlySubmissionBonusPerDay != null && earlySubmissionBonusPerDay.compareTo(BigDecimal.ZERO) > 0) {
                long daysEarly = ChronoUnit.DAYS.between(submittedAt, softDeadline);
                return earlySubmissionBonusPerDay.multiply(BigDecimal.valueOf(daysEarly));
            }
            if (softDeadlineBonus != null) {
                return softDeadlineBonus;
            }
        }

        return BigDecimal.ZERO;
    }
}
