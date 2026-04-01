package com.classroom.core.dto.team;

import com.classroom.core.model.TeamFormationMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class TemplateApplyResultDto {
    private UUID postId;
    private UUID templateId;
    private TeamFormationMode appliedMode;
}
