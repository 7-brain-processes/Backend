package com.classroom.core.dto.peerreview;

import com.classroom.core.model.PeerReviewReviewMode;
import com.classroom.core.model.PeerReviewScoringStrategy;
import com.classroom.core.model.PeerReviewUsageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeerReviewConfigDto {

    private UUID id;
    private Integer reviewersCount;
    private PeerReviewScoringStrategy scoringStrategy;
    private Instant firstDeadline;
    private Instant secondDeadline;
    private Integer redistributionFactor;
    private BigDecimal missedReviewPenalty;
    private PeerReviewReviewMode reviewMode;
    private PeerReviewUsageType usageType;
    private Instant round1ClosedAt;
    private Instant round2ClosedAt;
}
