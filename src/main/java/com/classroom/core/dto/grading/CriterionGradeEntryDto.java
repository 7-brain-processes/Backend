package com.classroom.core.dto.grading;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriterionGradeEntryDto {

    @NotNull
    private UUID criterionId;

    @NotNull
    private BigDecimal value;

    private String comment;
}
