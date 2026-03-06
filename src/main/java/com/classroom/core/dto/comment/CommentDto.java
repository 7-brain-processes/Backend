package com.classroom.core.dto.comment;

import com.classroom.core.dto.auth.UserDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class CommentDto {
    private UUID id;
    private String text;
    private UserDto author;
    private Instant createdAt;
    private Instant updatedAt;
}
