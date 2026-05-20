package com.classroom.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "assessment_results", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"solution_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssessmentResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solution_id", nullable = false, unique = true)
    private Solution solution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "config_version_id", nullable = false)
    private GradingConfigVersion configVersion;

    @Column(name = "basic_score", precision = 10, scale = 2)
    private BigDecimal basicScore;

    @Column(name = "modifier_delta", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal modifierDelta = BigDecimal.ZERO;

    @Column(name = "final_score", precision = 10, scale = 2)
    private BigDecimal finalScore;

    @Column(name = "is_published", nullable = false)
    @Builder.Default
    private Boolean isPublished = false;

    @Column(name = "graded_at")
    private Instant gradedAt;

    @OneToMany(mappedBy = "assessmentResult", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AssessmentCriterionGrade> criterionGrades = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public BigDecimal computeBasicScore() {
        Map<UUID, BigDecimal> valueByCriterion = criterionGrades.stream()
                .collect(Collectors.toMap(
                        g -> g.getVersionedCriterion().getId(),
                        AssessmentCriterionGrade::getValue,
                        (a, b) -> a));

        return configVersion.getCriteria().stream()
                .sorted(Comparator.comparingInt(VersionedCriterion::getSortOrder))
                .map(c -> {
                    BigDecimal value = valueByCriterion.getOrDefault(c.getId(), BigDecimal.ZERO);
                    return c.computePoints(value);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
