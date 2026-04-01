package com.classroom.core.dto.team;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AutoTeamFormationRequest {

    @Min(1)
    private Integer minTeamSize;

    @Min(1)
    private Integer maxTeamSize;

    private Boolean balanceByCategory = false;

    private Boolean balanceByRole = false;

    private Boolean reshuffle = false;
}
