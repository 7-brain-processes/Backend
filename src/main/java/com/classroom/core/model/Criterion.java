package com.classroom.core.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
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

    public CriterionSpec toSpec() {
        return new CriterionSpec(type, title, maxPoints, weight, sortOrder);
    }

    /**
     * Validates that the submitted raw value conforms to this criterion's type rules.
     * Domain rule: a criterion knows what values it accepts.
     */
    public void validateValue(BigDecimal value) {
        toSpec().validateValue(value);
    }

    /**
     * Computes the final points for this criterion based on the raw value.
     * Domain rule: a criterion knows how its own score is calculated.
     */
    public BigDecimal computePoints(BigDecimal value) {
        return toSpec().computePoints(value);
    }
}
