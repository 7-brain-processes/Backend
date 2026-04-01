package com.classroom.core.dto.post;

import com.classroom.core.model.PostType;
import com.classroom.core.model.TeamFormationMode;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
public class CreatePostRequest {

    @NotBlank
    @Size(min = 1, max = 300)
    private String title;

    @Size(max = 10000)
    private String content;

    @NotNull
    private PostType type;

    private TeamFormationMode teamFormationMode;

    @Future
    private Instant deadline;
}
