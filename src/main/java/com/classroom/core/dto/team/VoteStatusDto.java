package com.classroom.core.dto.team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class VoteStatusDto {
    private UUID teamId;
    private Integer teamGrade;
    private boolean finalized;
    private List<VoterStatusDto> voters;
    private List<StudentDistributedGradeDto> myVote;
    private List<StudentDistributedGradeDto> finalDistribution;
}
