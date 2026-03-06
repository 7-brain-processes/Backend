package com.classroom.core.dto.solution;

import com.classroom.core.dto.auth.UserDto;
import com.classroom.core.model.SolutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class SolutionDto {
    private UUID id;
    private String text;
    private SolutionStatus status;
    private Integer grade;
    private int filesCount;
    private UserDto student;
    private Instant submittedAt;
    private Instant updatedAt;
    private Instant gradedAt;
}
