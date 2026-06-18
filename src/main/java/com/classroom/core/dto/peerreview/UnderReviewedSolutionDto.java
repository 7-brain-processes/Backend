package com.classroom.core.dto.peerreview;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnderReviewedSolutionDto {

    private UUID solutionId;
    private UUID studentId;
    private String studentUsername;
    private UUID teamId;
    private String teamName;
    private Integer requiredReviews;
    private Integer completedReviews;
}
