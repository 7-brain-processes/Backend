package com.classroom.core.model;

import com.classroom.core.exception.BadRequestException;
import com.classroom.core.model.CriterionType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Immutable value object representing the specification of a grading criterion.
 * Encapsulates validation and scoring logic shared between mutable {@link Criterion}
 * and snapshot {@link VersionedCriterion}.
 */
@Getter
@AllArgsConstructor
public class CriterionSpec {

    private final CriterionType type;
    private final String title;
    private final BigDecimal maxPoints;
    private final BigDecimal weight;
    private final Integer sortOrder;

    public void validateValue(BigDecimal value) {
        switch (type) {
            case YES_NO -> {
                if (value.compareTo(BigDecimal.ZERO) != 0 && value.compareTo(BigDecimal.ONE) != 0) {
                    throw new BadRequestException("YES_NO criterion value must be 0 or 1");
                }
            }
            case PERCENTAGE -> {
                if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("100")) > 0) {
                    throw new BadRequestException("PERCENTAGE criterion value must be between 0 and 100");
                }
            }
            case POINTS -> {
                if (maxPoints.compareTo(BigDecimal.ZERO) > 0) {
                    if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(maxPoints) > 0) {
                        throw new BadRequestException("POINTS criterion value must be between 0 and " + maxPoints);
                    }
                } else if (maxPoints.compareTo(BigDecimal.ZERO) < 0) {
                    if (value.compareTo(maxPoints) < 0 || value.compareTo(BigDecimal.ZERO) > 0) {
                        throw new BadRequestException("POINTS criterion value must be between " + maxPoints + " and 0");
                    }
                } else {
                    if (value.compareTo(BigDecimal.ZERO) != 0) {
                        throw new BadRequestException("POINTS criterion value must be 0 when maxPoints is 0");
                    }
                }
            }
        }
    }

    public BigDecimal computePoints(BigDecimal value) {
        BigDecimal effectiveWeight = weight != null ? weight : BigDecimal.ONE;
        return switch (type) {
            case YES_NO -> maxPoints.multiply(value).multiply(effectiveWeight);
            case PERCENTAGE -> maxPoints.multiply(value).multiply(effectiveWeight)
                    .divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
            case POINTS -> value.multiply(effectiveWeight);
        };
    }
}
