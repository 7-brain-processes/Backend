package com.classroom.core.repository;

import com.classroom.core.model.PostCaptain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PostCaptainRepository extends JpaRepository<PostCaptain, UUID> {

    List<PostCaptain> findByPostId(UUID postId);

    @Query("SELECT pc FROM PostCaptain pc JOIN FETCH pc.user WHERE pc.post.id = :postId")
    List<PostCaptain> findByPostIdWithUser(@Param("postId") UUID postId);

    boolean existsByPostIdAndUserId(UUID postId, UUID userId);

    @Modifying
    @Query("DELETE FROM PostCaptain pc WHERE pc.post.id = :postId")
    void deleteByPostId(@Param("postId") UUID postId);

    @Query("SELECT COUNT(pc) FROM PostCaptain pc WHERE pc.post.id = :postId")
    long countByPostId(@Param("postId") UUID postId);
}