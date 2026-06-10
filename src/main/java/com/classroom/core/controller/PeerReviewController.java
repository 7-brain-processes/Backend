package com.classroom.core.controller;

import com.classroom.core.dto.ErrorResponse;
import com.classroom.core.dto.peerreview.PeerReviewAssignmentDto;
import com.classroom.core.dto.peerreview.SubmitPeerReviewRequest;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.PeerReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/posts/{postId}/peer-review")
@Tag(name = "Peer Review", description = "Peer-to-peer review assignment distribution and submission.")
@RequiredArgsConstructor
public class PeerReviewController {

    private final PeerReviewService peerReviewService;

    @PostMapping("/distribute")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Distribute round-1 peer review assignments (teacher only)",
            description = "Assigns reviewers to all submitted solutions. Any existing PENDING round-1 assignments are replaced.",
            operationId = "distributeRound1",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "Assignments distributed"),
                    @ApiResponse(responseCode = "400", description = "Not enough solutions or already has completed reviews",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public void distributeRound1(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {

        peerReviewService.distributeRound1(postId, courseId, principal.getId());
    }

    @PostMapping("/distribute-round2")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Redistribute reviews for under-reviewed solutions after the first deadline (teacher only)",
            description = "Creates round-2 assignments for solutions that did not receive the required number of completed reviews.",
            operationId = "distributeRound2",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "Round-2 assignments created (or nothing to do)"),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public void distributeRound2(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {

        peerReviewService.distributeRound2(postId, courseId, principal.getId());
    }

    @GetMapping("/my-assignments")
    @Operation(
            summary = "Get peer review assignments for the current student",
            description = "Returns all assignments where the authenticated user is the reviewer. Second deadline is never included (BR-05).",
            operationId = "getMyAssignments",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of assignments"),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<List<PeerReviewAssignmentDto>> getMyAssignments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {

        List<PeerReviewAssignmentDto> assignments = peerReviewService.getMyAssignments(postId, principal.getId());
        return ResponseEntity.ok(assignments);
    }

    @PutMapping("/assignments/{assignmentId}")
    @Operation(
            summary = "Submit or update a peer review (student)",
            description = "Student submits a grade and optional comment for an assigned solution. Editable until the first deadline.",
            operationId = "submitPeerReview",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Review submitted",
                            content = @Content(schema = @Schema(implementation = PeerReviewAssignmentDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid grade value",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Not your assignment or deadline has passed",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Assignment not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<PeerReviewAssignmentDto> submitPeerReview(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID assignmentId,
            @Valid @RequestBody SubmitPeerReviewRequest request) {

        PeerReviewAssignmentDto result = peerReviewService.submitReview(assignmentId, request, principal.getId());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/apply-grades")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Apply computed peer review scores to all assessment results (teacher only)",
            description = "Calculates the P2P score for each solution using the configured scoring strategy and writes it into the PEER_REVIEW criterion grade of each AssessmentResult.",
            operationId = "applyPeerReviewGrades",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "Grades applied"),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public void applyPeerReviewGrades(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {

        peerReviewService.applyGradesToAssessments(postId, courseId, principal.getId());
    }
}
