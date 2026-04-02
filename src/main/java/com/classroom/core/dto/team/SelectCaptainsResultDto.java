package com.classroom.core.dto.team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectCaptainsResultDto {

    private List<PostCaptainDto> captains;
    private int captainCount;
}