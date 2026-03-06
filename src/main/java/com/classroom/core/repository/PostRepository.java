package com.classroom.core.repository;

import com.classroom.core.model.Post;
import com.classroom.core.model.PostType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    Page<Post> findByCourseId(UUID courseId, Pageable pageable);

    Page<Post> findByCourseIdAndType(UUID courseId, PostType type, Pageable pageable);
}
