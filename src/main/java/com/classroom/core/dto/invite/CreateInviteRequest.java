package com.classroom.core.dto.invite;

import com.classroom.core.model.CourseRole;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

@Data
public class CreateInviteRequest {

    @NotNull
    private CourseRole role;

    private Instant expiresAt;

    @Min(1)
    private Integer maxUses;
}
