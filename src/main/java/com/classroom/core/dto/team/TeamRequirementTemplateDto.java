package com.classroom.core.dto.team;

import com.classroom.core.dto.course.CourseCategoryDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class TeamRequirementTemplateDto {
    private UUID id;
    private String name;
    private String description;
    private Integer minTeamSize;
    private Integer maxTeamSize;
    private CourseCategoryDto requiredCategory;
    private boolean requireAudio;
    private boolean requireVideo;
    private boolean active;
    private Instant createdAt;
    private Instant archivedAt;
}
