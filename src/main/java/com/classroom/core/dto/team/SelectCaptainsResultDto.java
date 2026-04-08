package com.classroom.core.dto.team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class SelectCaptainsResultDto {
    private int selectedCaptains;
    private List<PostCaptainDto> captains;
    private Instant selectedAt;
}