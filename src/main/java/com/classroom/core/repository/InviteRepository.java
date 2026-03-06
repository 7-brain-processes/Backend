package com.classroom.core.repository;

import com.classroom.core.model.Invite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteRepository extends JpaRepository<Invite, UUID> {

    Optional<Invite> findByCode(String code);

    List<Invite> findByCourseId(UUID courseId);
}
