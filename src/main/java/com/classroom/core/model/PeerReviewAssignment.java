package com.classroom.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "peer_review_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeerReviewAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "peer_review_config_id", nullable = false)
    private PeerReviewConfig peerReviewConfig;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_user_id")
    private User reviewerUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_team_id")
    private CourseTeam reviewerTeam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewee_solution_id", nullable = false)
    private Solution revieweeSolution;

    @Column(nullable = false)
    @Builder.Default
    private Integer round = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PeerReviewAssignmentStatus status = PeerReviewAssignmentStatus.PENDING;

    @CreationTimestamp
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
