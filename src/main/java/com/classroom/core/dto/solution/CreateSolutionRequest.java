package com.classroom.core.dto.solution;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateSolutionRequest {

    @Size(max = 10000)
    private String text;
}
