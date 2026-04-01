package com.classroom.core.dto.team;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateTeamRequirementTemplateRequest {

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 2000)
    private String description;

    @Min(1)
    private Integer minTeamSize;

    @Min(1)
    private Integer maxTeamSize;

    private UUID requiredCategoryId;

    private Boolean requireAudio = false;

    private Boolean requireVideo = false;
}
