package com.classroom.core.dto.team;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CaptainStudentGradeEntry {

    @NotNull
    private UUID studentId;

    @NotNull
    @Min(0)
    @Max(100)
    private Integer grade;
}
