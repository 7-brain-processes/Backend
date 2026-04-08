package com.classroom.core.repository;

import com.classroom.core.model.TeamGradeVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamGradeVoteRepository extends JpaRepository<TeamGradeVote, UUID> {

    @Query("SELECT DISTINCT v FROM TeamGradeVote v JOIN FETCH v.entries WHERE v.teamGrade.id = :teamGradeId")
    List<TeamGradeVote> findByTeamGradeIdWithEntries(@Param("teamGradeId") UUID teamGradeId);

    @Query("SELECT v FROM TeamGradeVote v LEFT JOIN FETCH v.entries WHERE v.teamGrade.id = :teamGradeId AND v.voter.id = :voterId")
    Optional<TeamGradeVote> findByTeamGradeIdAndVoterIdWithEntries(@Param("teamGradeId") UUID teamGradeId,
                                                                   @Param("voterId") UUID voterId);

    boolean existsByTeamGradeIdAndVoterId(UUID teamGradeId, UUID voterId);

    long countByTeamGradeId(UUID teamGradeId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM TeamGradeVote v WHERE v.teamGrade.id = :teamGradeId")
    void deleteByTeamGradeId(@Param("teamGradeId") UUID teamGradeId);
}
