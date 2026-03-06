package com.classroom.core.repository;

import com.classroom.core.model.CourseMember;
import com.classroom.core.model.CourseRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CourseMemberRepository extends JpaRepository<CourseMember, UUID> {

    Optional<CourseMember> findByCourseIdAndUserId(UUID courseId, UUID userId);

    boolean existsByCourseIdAndUserId(UUID courseId, UUID userId);

    Page<CourseMember> findByCourseId(UUID courseId, Pageable pageable);

    Page<CourseMember> findByCourseIdAndRole(UUID courseId, CourseRole role, Pageable pageable);

    Page<CourseMember> findByUserId(UUID userId, Pageable pageable);

    Page<CourseMember> findByUserIdAndRole(UUID userId, CourseRole role, Pageable pageable);

    int countByCourseIdAndRole(UUID courseId, CourseRole role);

    void deleteByCourseIdAndUserId(UUID courseId, UUID userId);
}
