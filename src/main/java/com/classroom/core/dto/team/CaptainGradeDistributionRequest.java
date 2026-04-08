package com.classroom.core.dto.team;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CaptainGradeDistributionRequest {

    @NotNull
    @NotEmpty
    private List<@Valid CaptainStudentGradeEntry> grades;
}
