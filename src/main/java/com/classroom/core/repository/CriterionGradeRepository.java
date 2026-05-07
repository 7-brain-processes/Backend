package com.classroom.core.repository;

import com.classroom.core.model.CriterionGrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CriterionGradeRepository extends JpaRepository<CriterionGrade, UUID> {

    List<CriterionGrade> findBySolutionId(UUID solutionId);

    Optional<CriterionGrade> findBySolutionIdAndCriterionId(UUID solutionId, UUID criterionId);

    void deleteBySolutionId(UUID solutionId);
}
