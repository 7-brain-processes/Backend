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
@Table(name = "grading_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradingConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, unique = true)
    private Post post;

    @Column(name = "max_grade", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxGrade;

    @Column(name = "results_visible", nullable = false)
    @Builder.Default
    private Boolean resultsVisible = false;

    @Column(name = "modifiers_json", columnDefinition = "TEXT")
    private String modifiersJson;

    @OneToMany(mappedBy = "gradingConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Criterion> criteria = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Aggregate-root consistency boundary: replaces all criteria atomically.
     * Old criteria (and their linked grades via DB cascade) are removed;
     * new ones are adopted into the aggregate.
     */
    public void replaceCriteria(List<Criterion> newCriteria) {
        this.criteria.clear();
        int sortOrder = 0;
        for (Criterion c : newCriteria) {
            c.setGradingConfig(this);
            c.setSortOrder(sortOrder++);
            this.criteria.add(c);
        }
    }

    /**
     * Computes the basic score by asking each criterion in the aggregate
     * to calculate its own contribution.
     */
    public BigDecimal computeBasicScore(List<CriterionGrade> grades) {
        Map<UUID, BigDecimal> valueByCriterion = grades.stream()
                .collect(Collectors.toMap(
                        g -> g.getCriterion().getId(),
                        CriterionGrade::getValue,
                        (a, b) -> a));

        return criteria.stream()
                .sorted(Comparator.comparingInt(Criterion::getSortOrder))
                .map(c -> {
                    BigDecimal value = valueByCriterion.getOrDefault(c.getId(), BigDecimal.ZERO);
                    return c.computePoints(value);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
