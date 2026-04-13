package com.classroom.core.repository;

import com.classroom.core.model.TeamInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamInvitationRepository extends JpaRepository<TeamInvitation, UUID> {

    List<TeamInvitation> findByStudentIdAndPostId(UUID studentId, UUID postId);

    List<TeamInvitation> findByCaptainIdAndPostId(UUID captainId, UUID postId);

    Optional<TeamInvitation> findByCaptainIdAndStudentIdAndPostId(UUID captainId, UUID studentId, UUID postId);

    boolean existsByStudentIdAndPostIdAndStatus(UUID studentId, UUID postId, TeamInvitation.InvitationStatus status);

    boolean existsByCaptainIdAndStudentIdAndPostIdAndStatus(UUID captainId, UUID studentId, UUID postId, TeamInvitation.InvitationStatus status);

    @Query("SELECT COUNT(ti) FROM TeamInvitation ti WHERE ti.captain.id = :captainId AND ti.post.id = :postId AND ti.status = 'ACCEPTED'")
    long countAcceptedInvitationsByCaptainAndPost(@Param("captainId") UUID captainId, @Param("postId") UUID postId);

    @Query("SELECT COUNT(ti) FROM TeamInvitation ti WHERE ti.captain.id = :captainId AND ti.post.id = :postId AND ti.status <> 'DECLINED'")
    long countActiveInvitationsByCaptainAndPost(@Param("captainId") UUID captainId, @Param("postId") UUID postId);

    @Modifying
    @Query("UPDATE TeamInvitation ti SET ti.status = :status, ti.respondedAt = :respondedAt WHERE ti.id = :id")
    void updateStatus(@Param("id") UUID id,
                      @Param("status") TeamInvitation.InvitationStatus status,
                      @Param("respondedAt") Instant respondedAt);
}