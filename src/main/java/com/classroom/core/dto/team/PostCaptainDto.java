package com.classroom.core.dto.team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class PostCaptainDto {
    private UUID id;
    private UUID userId;
    private String username;
    private String displayName;
}