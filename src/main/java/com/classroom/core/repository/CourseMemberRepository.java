package com.classroom.core.repository;

import com.classroom.core.model.CourseMember;
import com.classroom.core.model.CourseRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseMemberRepository extends JpaRepository<CourseMember, UUID> {

    Optional<CourseMember> findByCourseIdAndUserId(UUID courseId, UUID userId);

    boolean existsByCourseIdAndUserId(UUID courseId, UUID userId);

    List<CourseMember> findByCourseIdAndRole(UUID courseId, CourseRole role);

    Page<CourseMember> findByCourseId(UUID courseId, Pageable pageable);

    Page<CourseMember> findByCourseIdAndRole(UUID courseId, CourseRole role, Pageable pageable);

    Page<CourseMember> findByUserId(UUID userId, Pageable pageable);

    Page<CourseMember> findByUserIdAndRole(UUID userId, CourseRole role, Pageable pageable);

    int countByCourseIdAndRole(UUID courseId, CourseRole role);

    List<CourseMember> findByCourseIdAndUserIdIn(UUID courseId, Collection<UUID> userIds);

    List<CourseMember> findByCourseIdAndTeamIdOrderByJoinedAtAsc(UUID courseId, UUID teamId);

    void deleteByCourseIdAndUserId(UUID courseId, UUID userId);

    int countByTeamId(UUID teamId);

    Optional<CourseMember> findByCourseIdAndUserIdAndTeamId(UUID courseId, UUID userId, UUID teamId);

    @Query("SELECT cm FROM CourseMember cm " +
           "JOIN cm.team t " +
           "WHERE cm.course.id = :courseId AND cm.user.id = :userId AND t.post.id = :postId")
    Optional<CourseMember> findStudentTeamInPost(@Param("courseId") UUID courseId, 
                                                   @Param("userId") UUID userId,
                                                   @Param("postId") UUID postId);

    @Query("SELECT COUNT(cm) FROM CourseMember cm " +
           "JOIN cm.team t " +
           "WHERE cm.course.id = :courseId AND cm.user.id = :userId AND t.post.id = :postId")
    int countStudentTeamsInPost(@Param("courseId") UUID courseId, 
                                 @Param("userId") UUID userId,
                                 @Param("postId") UUID postId);
}
