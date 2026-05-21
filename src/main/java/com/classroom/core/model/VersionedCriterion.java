package com.classroom.core.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "versioned_criteria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VersionedCriterion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "config_version_id", nullable = false)
    private GradingConfigVersion configVersion;

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

    public void validateValue(BigDecimal value) {
        toSpec().validateValue(value);
    }

    public BigDecimal computePoints(BigDecimal value) {
        return toSpec().computePoints(value);
    }
}
