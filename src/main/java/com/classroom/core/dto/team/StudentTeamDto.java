package com.classroom.core.dto.team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Data
@Builder
@AllArgsConstructor
public class StudentTeamDto {
    private UUID teamId;
    private String teamName;
    private int membersCount;
    private Integer maxSize;
    private List<CourseTeamMemberDto> members;
    private Instant joinedAt;
}
