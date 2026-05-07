package com.classroom.core.dto.grading;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModifierConfigDto {
    private DeadlineModifierDto deadlines;
    private TeamSizeModifierDto teamSize;
    private ProgressRegularityModifierDto progressRegularity;
    private ContributionModifierDto contributionVoting;
}
