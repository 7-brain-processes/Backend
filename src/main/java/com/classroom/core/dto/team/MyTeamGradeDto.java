package com.classroom.core.dto.team;

import com.classroom.core.model.TeamGradeDistributionMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class MyTeamGradeDto {
    private UUID teamId;
    private String teamName;
    private Integer teamGrade;
    private TeamGradeDistributionMode distributionMode;
    private Integer myGrade;
    private boolean finalized;
    private List<StudentDistributedGradeDto> finalDistribution;
}
