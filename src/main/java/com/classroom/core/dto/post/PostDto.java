package com.classroom.core.dto.post;

import com.classroom.core.dto.auth.UserDto;
import com.classroom.core.model.PostType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class PostDto {
    private UUID id;
    private String title;
    private String content;
    private PostType type;
    private Instant deadline;
    private UserDto author;
    private int materialsCount;
    private int commentsCount;
    private Integer solutionsCount;
    private UUID mySolutionId;
    private Instant createdAt;
    private Instant updatedAt;
}
