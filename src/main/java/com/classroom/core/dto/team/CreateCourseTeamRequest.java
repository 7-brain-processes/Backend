package com.classroom.core.dto.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class CreateCourseTeamRequest {

    @NotBlank
    @Size(max = 200)
    private String name;

    private List<UUID> memberIds = new ArrayList<>();

    private List<UUID> categoryIds = new ArrayList<>();

    @Positive
    private Integer maxSize;

    private Boolean selfEnrollmentEnabled;
}
