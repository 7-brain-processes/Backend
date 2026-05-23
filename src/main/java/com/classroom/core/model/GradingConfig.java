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
import java.util.UUID;

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

    public GradingConfigVersion snapshotVersion(int versionNumber, List<Criterion> criteria) {
        GradingConfigVersion version = GradingConfigVersion.builder()
                .post(this.post)
                .versionNumber(versionNumber)
                .maxGrade(this.maxGrade)
                .modifiersJson(this.modifiersJson)
                .build();

        List<VersionedCriterion> versioned = criteria.stream()
                .sorted(Comparator.comparingInt(Criterion::getSortOrder))
                .map(c -> VersionedCriterion.builder()
                        .configVersion(version)
                        .type(c.getType())
                        .title(c.getTitle())
                        .maxPoints(c.getMaxPoints())
                        .weight(c.getWeight())
                        .sortOrder(c.getSortOrder())
                        .build())
                .toList();
        version.replaceCriteria(versioned);
        return version;
    }

    public boolean matchesVersion(GradingConfigVersion version, List<Criterion> criteria) {
        if (!this.maxGrade.equals(version.getMaxGrade())) {
            return false;
        }
        if (!java.util.Objects.equals(this.modifiersJson, version.getModifiersJson())) {
            return false;
        }
        List<Criterion> sortedCriteria = criteria.stream()
                .sorted(Comparator.comparingInt(Criterion::getSortOrder))
                .toList();
        List<VersionedCriterion> sortedVersioned = version.getCriteria().stream()
                .sorted(Comparator.comparingInt(VersionedCriterion::getSortOrder))
                .toList();
        if (sortedCriteria.size() != sortedVersioned.size()) {
            return false;
        }
        for (int i = 0; i < sortedCriteria.size(); i++) {
            Criterion c = sortedCriteria.get(i);
            VersionedCriterion v = sortedVersioned.get(i);
            if (!java.util.Objects.equals(c.getType(), v.getType())
                    || !java.util.Objects.equals(c.getTitle(), v.getTitle())
                    || !java.util.Objects.equals(c.getMaxPoints(), v.getMaxPoints())
                    || !java.util.Objects.equals(c.getWeight(), v.getWeight())
                    || !java.util.Objects.equals(c.getSortOrder(), v.getSortOrder())) {
                return false;
            }
        }
        return true;
    }
}
