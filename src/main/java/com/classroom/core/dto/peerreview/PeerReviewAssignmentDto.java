package com.classroom.core.dto.peerreview;

import com.classroom.core.model.PeerReviewAssignmentStatus;
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
public class PeerReviewAssignmentDto {

    private UUID assignmentId;
    private UUID revieweeSolutionId;
    private PeerReviewAssignmentStatus status;
    private Integer round;
    private Instant firstDeadline;
    private Instant assignedAt;
    private Instant completedAt;
    private BigDecimal submittedGrade;
    private String submittedComment;
}
