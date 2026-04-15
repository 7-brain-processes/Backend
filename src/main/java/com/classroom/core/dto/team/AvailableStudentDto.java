package com.classroom.core.dto.team;

import com.classroom.core.dto.course.CourseCategoryDto;
import com.classroom.core.model.CourseRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class AvailableStudentDto {
    private UUID userId;
    private String username;
    private String displayName;
    private CourseRole role;
    private CourseCategoryDto category;
}