package com.classroom.core.dto.team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class AutoTeamFormationResultDto {
    private int formedTeams;
    private int assignedStudents;
    private int unassignedStudents;
    private Instant generatedAt;
    private List<CourseTeamDto> teams;
}
