package com.classroom.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "course_teams", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"course_id", "name"}),
        @UniqueConstraint(columnNames = {"course_id", "post_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "course_team_categories",
            joinColumns = @JoinColumn(name = "team_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<CourseCategory> categories = new LinkedHashSet<>();

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "max_size")
    private Integer maxSize;

    @Column(nullable = false)
    @Builder.Default
    private boolean selfEnrollmentEnabled = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
