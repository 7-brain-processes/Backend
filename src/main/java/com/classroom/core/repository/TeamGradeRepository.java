package com.classroom.core.repository;

import com.classroom.core.model.TeamGrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TeamGradeRepository extends JpaRepository<TeamGrade, UUID> {

    Optional<TeamGrade> findByPostIdAndTeamId(UUID postId, UUID teamId);
}
