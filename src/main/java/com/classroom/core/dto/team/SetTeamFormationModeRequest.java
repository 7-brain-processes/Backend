package com.classroom.core.dto.team;

import com.classroom.core.model.TeamFormationMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetTeamFormationModeRequest {

    @NotNull
    private TeamFormationMode mode;
}
