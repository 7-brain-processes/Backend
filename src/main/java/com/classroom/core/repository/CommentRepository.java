package com.classroom.core.repository;

import com.classroom.core.model.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    Page<Comment> findByPostId(UUID postId, Pageable pageable);

    Page<Comment> findBySolutionId(UUID solutionId, Pageable pageable);

    int countByPostId(UUID postId);

    int countBySolutionId(UUID solutionId);
}
