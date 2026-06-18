package com.classroom.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "peer_review_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeerReviewConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criterion_id", nullable = false, unique = true)
    private Criterion criterion;

    @Column(name = "reviewers_count", nullable = false)
    private Integer reviewersCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_strategy", nullable = false, length = 20)
    private PeerReviewScoringStrategy scoringStrategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_mode", nullable = false, length = 20)
    @Builder.Default
    private PeerReviewReviewMode reviewMode = PeerReviewReviewMode.MANY_TO_ONE;

    @Column(name = "first_deadline", nullable = false)
    private Instant firstDeadline;

    @Column(name = "second_deadline", nullable = false)
    private Instant secondDeadline;

    @Column(name = "redistribution_factor", nullable = false)
    @Builder.Default
    private Integer redistributionFactor = 2;

    @Column(name = "missed_review_penalty", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal missedReviewPenalty = new BigDecimal("10");

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false, length = 20)
    @Builder.Default
    private PeerReviewUsageType usageType = PeerReviewUsageType.CRITERION;

    @Column(name = "round1_closed_at")
    private Instant round1ClosedAt;

    @Column(name = "round2_closed_at")
    private Instant round2ClosedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
