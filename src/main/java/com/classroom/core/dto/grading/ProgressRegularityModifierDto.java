package com.classroom.core.dto.grading;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressRegularityModifierDto {
    private Boolean enabled;
    private Integer checkpointCount;
    private BigDecimal pointsPerCheckpoint;
}
