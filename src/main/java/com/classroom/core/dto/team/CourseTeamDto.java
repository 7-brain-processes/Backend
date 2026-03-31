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
public class CourseTeamDto {
    private UUID id;
    private String name;
    private Instant createdAt;
    private int membersCount;
    private List<CourseTeamMemberDto> members;
}
