package com.classroom.core.repository;

import com.classroom.core.model.GradingConfigVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GradingConfigVersionRepository extends JpaRepository<GradingConfigVersion, UUID> {

    Optional<GradingConfigVersion> findTopByPostIdOrderByVersionNumberDesc(UUID postId);

    List<GradingConfigVersion> findByPostIdOrderByVersionNumberAsc(UUID postId);
}
