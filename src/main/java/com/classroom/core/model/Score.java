package com.classroom.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Immutable value object representing a grade score within a defined maximum.
 * Encapsulates the clamping rule: score is always in [0, max].
 */
@Getter
@AllArgsConstructor
public class Score {

    private final BigDecimal value;
    private final BigDecimal max;

    public static Score of(BigDecimal value, BigDecimal max) {
        return new Score(value, max);
    }

    public Score clamp() {
        BigDecimal clamped = value.max(BigDecimal.ZERO).min(max);
        return new Score(clamped, max);
    }
}
