package com.classroom.core.dto.post;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
public class UpdatePostRequest {

    @Size(min = 1, max = 300)
    private String title;

    @Size(max = 10000)
    private String content;

    private Instant deadline;
}
