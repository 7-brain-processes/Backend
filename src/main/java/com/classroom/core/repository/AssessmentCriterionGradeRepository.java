package com.classroom.core.repository;

import com.classroom.core.model.AssessmentCriterionGrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssessmentCriterionGradeRepository extends JpaRepository<AssessmentCriterionGrade, UUID> {

    List<AssessmentCriterionGrade> findByAssessmentResultId(UUID assessmentResultId);

    void deleteByAssessmentResultId(UUID assessmentResultId);
}
