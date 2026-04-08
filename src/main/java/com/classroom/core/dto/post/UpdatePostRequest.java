package com.classroom.core.dto.post;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.classroom.core.model.TeamFormationMode;

import java.time.Instant;

@Data
public class UpdatePostRequest {

    @Size(min = 1, max = 300)
    private String title;

    @Size(max = 10000)
    private String content;

    @Future
    private Instant deadline;

    private TeamFormationMode teamFormationMode;
}
