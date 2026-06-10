package com.classroom.core.dto.peerreview;

import com.classroom.core.model.PeerReviewScoringStrategy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeerReviewConfigRequest {

    @NotNull
    @Min(1)
    private Integer reviewersCount;

    @NotNull
    private PeerReviewScoringStrategy scoringStrategy;

    @NotNull
    private Instant firstDeadline;

    @NotNull
    private Instant secondDeadline;

    @Min(1)
    private Integer redistributionFactor;
}
