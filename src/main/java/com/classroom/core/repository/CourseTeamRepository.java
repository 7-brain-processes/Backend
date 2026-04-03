package com.classroom.core.repository;

import com.classroom.core.model.CourseTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseTeamRepository extends JpaRepository<CourseTeam, UUID> {

    List<CourseTeam> findByCourseIdOrderByCreatedAtAsc(UUID courseId);

    Optional<CourseTeam> findByIdAndCourseId(UUID teamId, UUID courseId);

    boolean existsByCourseIdAndNameIgnoreCase(UUID courseId, String name);

    List<CourseTeam> findByPostIdAndSelfEnrollmentEnabledOrderByCreatedAtAsc(UUID postId, boolean enabled);

    Optional<CourseTeam> findByPostIdAndId(UUID postId, UUID teamId);

    List<CourseTeam> findByPostId(UUID postId);

    @Query("select t from CourseTeam t left join fetch t.categories where t.id = :teamId")
    Optional<CourseTeam> findByIdWithCategories(@Param("teamId") UUID teamId);
}
