package com.classroom.core.dto.team;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpsertTeamGradeRequest {

    @NotNull
    @Min(0)
    @Max(100)
    private Integer grade;

    @Size(max = 5000)
    private String comment;
}
