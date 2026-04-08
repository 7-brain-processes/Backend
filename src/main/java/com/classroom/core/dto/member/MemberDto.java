package com.classroom.core.dto.member;

import com.classroom.core.dto.auth.UserDto;
import com.classroom.core.dto.course.CourseCategoryDto;
import com.classroom.core.model.CourseRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
public class MemberDto {
    private UserDto user;
    private CourseRole role;
    private Instant joinedAt;
    private CourseCategoryDto category;
}
