package com.classroom.core.dto.course;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateCourseCategoryRequest {

    @Size(max = 200)
    private String title;

    @Size(max = 2000)
    private String description;

    private Boolean active;
}
