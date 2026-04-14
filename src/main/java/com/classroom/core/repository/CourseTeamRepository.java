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

    boolean existsByCourseIdAndPostIsNullAndNameIgnoreCase(UUID courseId, String name);

    boolean existsByCourseIdAndPostIdAndNameIgnoreCase(UUID courseId, UUID postId, String name);

    List<CourseTeam> findByPostIdAndSelfEnrollmentEnabledOrderByCreatedAtAsc(UUID postId, boolean enabled);

    List<CourseTeam> findByPostIdOrderByCreatedAtAsc(UUID postId);

    Optional<CourseTeam> findByPostIdAndId(UUID postId, UUID teamId);

    List<CourseTeam> findByPostId(UUID postId);

    @Query("select t from CourseTeam t left join fetch t.categories where t.id = :teamId")
    Optional<CourseTeam> findByIdWithCategories(@Param("teamId") UUID teamId);

    @Query("SELECT t FROM CourseTeam t WHERE t.post.id = :postId AND EXISTS (SELECT m FROM CourseMember m WHERE m.team = t AND m.user.id = :captainId)")
    Optional<CourseTeam> findByPostIdAndCaptainId(@Param("postId") UUID postId, @Param("captainId") UUID captainId);

    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM CourseMember m WHERE m.team.post.id = :postId AND m.user.id = :userId")
    boolean existsByPostIdAndMemberUserId(@Param("postId") UUID postId, @Param("userId") UUID userId);
}
