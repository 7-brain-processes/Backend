package com.classroom.core.dto.team;

import com.classroom.core.dto.course.CourseCategoryDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class AutoFormationStudentDto {
    private UUID userId;
    private String username;
    private String displayName;
    private CourseCategoryDto category;
}
