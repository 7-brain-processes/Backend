package com.classroom.core.repository;

import com.classroom.core.model.TeamStudentGrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamStudentGradeRepository extends JpaRepository<TeamStudentGrade, UUID> {

    List<TeamStudentGrade> findByTeamGradeIdOrderByStudentIdAsc(UUID teamGradeId);

    Optional<TeamStudentGrade> findByTeamGradeIdAndStudentId(UUID teamGradeId, UUID studentId);

    void deleteByTeamGradeId(UUID teamGradeId);
}
