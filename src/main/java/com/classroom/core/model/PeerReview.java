package com.classroom.core.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "peer_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeerReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false, unique = true)
    private PeerReviewAssignment assignment;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal grade;

    @Column(columnDefinition = "TEXT")
    private String comment;
    
    @Column(name = "submitted_at", nullable = false)
    @Builder.Default
    private Instant submittedAt = Instant.now();
}
