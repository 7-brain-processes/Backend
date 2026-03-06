package com.classroom.core.repository;

import com.classroom.core.model.PostFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostFileRepository extends JpaRepository<PostFile, UUID> {

    List<PostFile> findByPostId(UUID postId);

    int countByPostId(UUID postId);
}
