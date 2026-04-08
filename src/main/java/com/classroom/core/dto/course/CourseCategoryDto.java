package com.classroom.core.dto.course;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class CourseCategoryDto {
    private UUID id;
    private String title;
    private String description;
    private boolean active;
    private Instant createdAt;
}
