package com.classroom.core.dto.team;

import com.classroom.core.dto.course.CourseCategoryDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Data
@Builder
@AllArgsConstructor
public class CourseTeamAvailabilityDto {
    private UUID id;
    private String name;
    private int currentMembers;
    private Integer maxSize;
    private boolean selfEnrollmentEnabled;
    private boolean isFull;
    private boolean isStudentMember;
    private List<CourseCategoryDto> categories;
    private Instant createdAt;

    public CourseTeamAvailabilityDto() {
        this.selfEnrollmentEnabled = false;
        this.isFull = false;
        this.isStudentMember = false;
    }
}
