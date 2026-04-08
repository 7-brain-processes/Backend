package com.classroom.core.dto.team;

import com.classroom.core.dto.auth.UserDto;
import com.classroom.core.dto.course.CourseCategoryDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class CourseTeamMemberDto {
    private UserDto user;
    private CourseCategoryDto category;
}
