package com.classroom.core.dto.team;

import com.classroom.core.model.TeamFormationMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class TeamFormationModeDto {
    private TeamFormationMode mode;
    private boolean isCaptain;
}
