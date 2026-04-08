package com.classroom.core.dto.team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;


@Data
@Builder
@AllArgsConstructor
public class EnrollmentResponseDto {
    private boolean success;
    private String message;
    private TeamEnrollmentStatusDto team;

    @Data
    @Builder
    @AllArgsConstructor
    public static class TeamEnrollmentStatusDto {
        private String teamId;
        private String teamName;
        private int currentMembers;
        private Integer maxSize;
    }
}
