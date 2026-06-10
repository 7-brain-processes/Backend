package com.classroom.core.repository;

import com.classroom.core.model.Solution;
import com.classroom.core.model.SolutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SolutionRepository extends JpaRepository<Solution, UUID> {

    Optional<Solution> findByPostIdAndStudentId(UUID postId, UUID studentId);

    boolean existsByPostIdAndStudentId(UUID postId, UUID studentId);

    List<Solution> findAllByPostId(UUID postId);

    List<Solution> findAllByPostIdAndStatusIn(UUID postId, Collection<SolutionStatus> statuses);

    Page<Solution> findByPostId(UUID postId, Pageable pageable);

    Page<Solution> findByPostIdAndStatus(UUID postId, SolutionStatus status, Pageable pageable);

    int countByPostId(UUID postId);
}
