package com.classroom.core.dto.grading;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriteriaGradeSubmissionDto {

    @NotEmpty
    @Valid
    private List<CriterionGradeEntryDto> grades;
}
