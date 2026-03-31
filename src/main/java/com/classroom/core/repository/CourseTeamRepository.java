package com.classroom.core.repository;

import com.classroom.core.model.CourseTeam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseTeamRepository extends JpaRepository<CourseTeam, UUID> {

    List<CourseTeam> findByCourseIdOrderByCreatedAtAsc(UUID courseId);

    boolean existsByCourseIdAndNameIgnoreCase(UUID courseId, String name);
}
