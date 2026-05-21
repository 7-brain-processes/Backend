package com.classroom.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable value object wrapping all grading modifiers.
 */
@Getter
@AllArgsConstructor
public class ModifierConfig {

    private final DeadlineModifier deadline;

    public BigDecimal computeTotalDelta(Instant submittedAt) {
        if (deadline == null) {
            return BigDecimal.ZERO;
        }
        return deadline.computeDelta(submittedAt);
    }
}
