package com.classroom.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "grading_config_versions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"post_id", "version_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradingConfigVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "max_grade", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxGrade;

    @Column(name = "modifiers_json", columnDefinition = "TEXT")
    private String modifiersJson;

    @OneToMany(mappedBy = "configVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<VersionedCriterion> criteria = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public void replaceCriteria(List<VersionedCriterion> newCriteria) {
        this.criteria.clear();
        int sortOrder = 0;
        for (VersionedCriterion c : newCriteria) {
            c.setConfigVersion(this);
            c.setSortOrder(sortOrder++);
            this.criteria.add(c);
        }
    }

    public BigDecimal computeBasicScore(List<AssessmentCriterionGrade> grades) {
        Map<UUID, BigDecimal> valueByCriterion = grades.stream()
                .collect(Collectors.toMap(
                        g -> g.getVersionedCriterion().getId(),
                        AssessmentCriterionGrade::getValue,
                        (a, b) -> a));

        return criteria.stream()
                .sorted(Comparator.comparingInt(VersionedCriterion::getSortOrder))
                .map(c -> {
                    BigDecimal value = valueByCriterion.getOrDefault(c.getId(), BigDecimal.ZERO);
                    return c.computePoints(value);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
