package com.classroom.core.dto.team;

import com.classroom.core.model.TeamGradeDistributionMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetTeamGradeDistributionModeRequest {

    @NotNull
    private TeamGradeDistributionMode distributionMode;
}
