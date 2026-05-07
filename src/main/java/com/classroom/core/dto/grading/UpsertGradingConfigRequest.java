package com.classroom.core.dto.grading;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertGradingConfigRequest {

    @NotNull
    @Min(1)
    private BigDecimal maxGrade;

    @NotEmpty
    @Valid
    private List<CriterionConfigDto> criteria;

    private ModifierConfigDto modifiers;

    private Boolean resultsVisible;
}
