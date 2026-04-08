package com.classroom.core.repository;

import com.classroom.core.model.TeamRequirementTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamRequirementTemplateRepository extends JpaRepository<TeamRequirementTemplate, UUID> {

    List<TeamRequirementTemplate> findByCourseIdOrderByCreatedAtAsc(UUID courseId);

    Optional<TeamRequirementTemplate> findByIdAndCourseId(UUID templateId, UUID courseId);
}
