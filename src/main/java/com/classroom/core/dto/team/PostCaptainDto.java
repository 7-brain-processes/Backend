package com.classroom.core.dto.team;

import com.classroom.core.dto.auth.UserDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostCaptainDto {

    private UUID id;
    private UUID postId;
    private UUID captainId;
    private UserDto captain;
    private Instant assignedAt;
}