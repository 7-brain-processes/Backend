package com.classroom.core.repository;

import com.classroom.core.model.SolutionFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SolutionFileRepository extends JpaRepository<SolutionFile, UUID> {

    List<SolutionFile> findBySolutionId(UUID solutionId);

    int countBySolutionId(UUID solutionId);
}
