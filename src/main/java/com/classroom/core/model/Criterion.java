package com.classroom.core.model;

import com.classroom.core.exception.BadRequestException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "criteria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Criterion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grading_config_id", nullable = false)
    private GradingConfig gradingConfig;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CriterionType type;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "max_points", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxPoints;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal weight = BigDecimal.ONE;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    /**
     * Validates that the submitted raw value conforms to this criterion's type rules.
     * Domain rule: a criterion knows what values it accepts.
     */
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

    /**
     * Computes the final points for this criterion based on the raw value.
     * Domain rule: a criterion knows how its own score is calculated.
     */
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
