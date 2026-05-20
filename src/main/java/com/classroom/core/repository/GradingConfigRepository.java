package com.classroom.core.repository;

import com.classroom.core.model.GradingConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GradingConfigRepository extends JpaRepository<GradingConfig, UUID> {

    Optional<GradingConfig> findByPostId(UUID postId);

    boolean existsByPostId(UUID postId);
}
