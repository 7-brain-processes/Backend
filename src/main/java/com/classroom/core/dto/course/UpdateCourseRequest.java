package com.classroom.core.dto.course;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateCourseRequest {

    @Size(min = 1, max = 200)
    private String name;

    @Size(max = 2000)
    private String description;
}
