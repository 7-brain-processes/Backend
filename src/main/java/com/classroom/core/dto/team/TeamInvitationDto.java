package com.classroom.core.dto.team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class TeamInvitationDto {
    private UUID id;
    private UUID captainId;
    private String captainUsername;
    private String captainDisplayName;
    private UUID studentId;
    private String studentUsername;
    private String studentDisplayName;
    private UUID postId;
    private String status;
    private Instant createdAt;
    private Instant respondedAt;
}