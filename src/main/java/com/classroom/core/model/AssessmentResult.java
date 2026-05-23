package com.classroom.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import com.classroom.core.event.AssessmentPublishedEvent;
import com.classroom.core.event.DomainEvent;
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

    @Transient
    @Builder.Default
    private List<DomainEvent> domainEvents = new ArrayList<>();

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

    public Score computeFinalScore(ModifierConfig modifierConfig) {
        BigDecimal basic = computeBasicScore();
        BigDecimal modifierDelta = modifierConfig != null
                ? modifierConfig.computeTotalDelta(solution.getSubmittedAt())
                : BigDecimal.ZERO;
        return Score.of(basic.add(modifierDelta), configVersion.getMaxGrade()).clamp();
    }

    public void assess(List<AssessmentCriterionGrade> grades, ModifierConfig modifierConfig) {
        replaceCriterionGrades(grades);
        BigDecimal basic = computeBasicScore();
        BigDecimal delta = modifierConfig != null
                ? modifierConfig.computeTotalDelta(solution.getSubmittedAt())
                : BigDecimal.ZERO;
        Score score = Score.of(basic.add(delta), configVersion.getMaxGrade()).clamp();
        this.basicScore = basic;
        this.modifierDelta = delta;
        this.finalScore = score.getValue();
        this.gradedAt = Instant.now();
    }

    public void replaceCriterionGrades(List<AssessmentCriterionGrade> grades) {
        List<AssessmentCriterionGrade> toAdd = new ArrayList<>(grades);
        this.criterionGrades.clear();
        for (AssessmentCriterionGrade g : toAdd) {
            g.setAssessmentResult(this);
            this.criterionGrades.add(g);
        }
    }

    public void publish() {
        if (Boolean.TRUE.equals(this.isPublished)) {
            throw new IllegalStateException("Assessment is already published");
        }
        this.isPublished = true;
        domainEvents.add(new AssessmentPublishedEvent(this.id, this.solution.getId(), Instant.now()));
    }

    public void unpublish() {
        this.isPublished = false;
    }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return events;
    }
}
