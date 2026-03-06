package com.classroom.core.dto.invite;

import com.classroom.core.model.CourseRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class InviteDto {
    private UUID id;
    private String code;
    private CourseRole role;
    private Instant expiresAt;
    private Integer maxUses;
    private int currentUses;
    private Instant createdAt;
}
