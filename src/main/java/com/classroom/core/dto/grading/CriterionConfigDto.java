package com.classroom.core.dto.grading;

import com.classroom.core.model.CriterionType;
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
public class CriterionConfigDto {
    private UUID id;
    private CriterionType type;
    private String title;
    private BigDecimal maxPoints;
    private BigDecimal weight;
    private Integer sortOrder;
}
