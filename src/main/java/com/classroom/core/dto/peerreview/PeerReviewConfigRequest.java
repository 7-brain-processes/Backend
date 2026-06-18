package com.classroom.core.dto.peerreview;

import com.classroom.core.model.PeerReviewReviewMode;
import com.classroom.core.model.PeerReviewScoringStrategy;
import com.classroom.core.model.PeerReviewUsageType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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

    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal missedReviewPenalty;

    private PeerReviewReviewMode reviewMode;

    private PeerReviewUsageType usageType;
}
