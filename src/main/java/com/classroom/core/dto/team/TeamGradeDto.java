package com.classroom.core.dto.team;

import com.classroom.core.model.TeamGradeDistributionMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class TeamGradeDto {
    private UUID id;
    private UUID postId;
    private UUID teamId;
    private Integer grade;
    private String comment;
    private TeamGradeDistributionMode distributionMode;
    private Instant updatedAt;
}
