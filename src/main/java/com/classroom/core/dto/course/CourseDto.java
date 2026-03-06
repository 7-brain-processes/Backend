package com.classroom.core.dto.course;

import com.classroom.core.model.CourseRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class CourseDto {
    private UUID id;
    private String name;
    private String description;
    private Instant createdAt;
    private CourseRole currentUserRole;
    private int teacherCount;
    private int studentCount;
}
