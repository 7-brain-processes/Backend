package com.classroom.core.dto.team;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ApplyTeamRequirementTemplateRequest {

    @NotNull
    private UUID postId;
}
