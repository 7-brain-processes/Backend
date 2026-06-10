package com.classroom.core.dto.peerreview;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitPeerReviewRequest {

    @NotNull
    private BigDecimal grade;

    @Size(max = 5000)
    private String comment;
}
