package com.classroom.core.repository;

import com.classroom.core.model.PostCaptain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostCaptainRepository extends JpaRepository<PostCaptain, UUID> {

    List<PostCaptain> findByPostId(UUID postId);

    void deleteByPostId(UUID postId);
}