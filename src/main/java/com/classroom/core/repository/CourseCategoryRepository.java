package com.classroom.core.repository;

import com.classroom.core.model.CourseCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseCategoryRepository extends JpaRepository<CourseCategory, UUID> {

    List<CourseCategory> findByCourseIdOrderByTitleAsc(UUID courseId);

    Optional<CourseCategory> findByIdAndCourseId(UUID id, UUID courseId);
}
