package com.classroom.core.controller;

import com.classroom.core.dto.ErrorResponse;
import com.classroom.core.dto.peerreview.PeerReviewAssignmentDto;
import com.classroom.core.dto.peerreview.PeerReviewConfigDto;
import com.classroom.core.dto.peerreview.SubmitPeerReviewRequest;
import com.classroom.core.dto.peerreview.UnderReviewedSolutionDto;
import com.classroom.core.model.Solution;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/posts/{postId}/peer-review")
@Tag(name = "Peer Review", description = "Peer-to-peer review assignment distribution and submission.")
@RequiredArgsConstructor
public class PeerReviewController {

    private final PeerReviewService peerReviewService;

    @GetMapping("/config")
    @Operation(
            summary = "Get peer review configuration",
            description = "Returns the peer review configuration for the task. Second deadline is visible only to teachers.",
            operationId = "getPeerReviewConfig",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Peer review configuration",
                            content = @Content(schema = @Schema(implementation = PeerReviewConfigDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<PeerReviewConfigDto> getConfig(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {

        PeerReviewConfigDto dto = peerReviewService.getConfigDto(courseId, postId, principal.getId())
                .orElseThrow(() -> new com.classroom.core.exception.ResourceNotFoundException("Peer review config not found"));
        return ResponseEntity.ok(dto);
    }

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

    @PostMapping("/close-round1")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Close round 1 manually (teacher only)",
            description = "Marks pending round-1 assignments as missed, applies penalties and triggers round-2 redistribution.",
            operationId = "closeRound1",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "Round 1 closed"),
                    @ApiResponse(responseCode = "400", description = "First deadline has not passed",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public void closeRound1(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {

        peerReviewService.closeRound1(postId, courseId, principal.getId());
    }

    @PostMapping("/close-round2")
    @Operation(
            summary = "Close round 2 manually (teacher only)",
            description = "Identifies solutions that still lack required reviews after the second deadline. " +
                    "Returns the list of under-reviewed solutions so the teacher can intervene manually.",
            operationId = "closeRound2",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Round 2 closed",
                            content = @Content(schema = @Schema(implementation = UnderReviewedSolutionDto.class))),
                    @ApiResponse(responseCode = "400", description = "Second deadline has not passed",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<List<UnderReviewedSolutionDto>> closeRound2(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {

        List<Solution> solutions = peerReviewService.closeRound2(postId, courseId, principal.getId());
        return ResponseEntity.ok(toUnderReviewedDtos(courseId, postId, principal.getId(), solutions));
    }

    private List<UnderReviewedSolutionDto> toUnderReviewedDtos(UUID courseId, UUID postId, UUID userId, List<Solution> solutions) {
        int requiredReviews = peerReviewService.getConfigDto(courseId, postId, userId)
                .map(PeerReviewConfigDto::getReviewersCount)
                .orElse(0);
        return solutions.stream()
                .map(s -> {
                    UnderReviewedSolutionDto.UnderReviewedSolutionDtoBuilder builder = UnderReviewedSolutionDto.builder()
                            .solutionId(s.getId())
                            .requiredReviews(requiredReviews)
                            .completedReviews((int) peerReviewService.countCompletedReviews(s.getId()));
                    if (s.getStudent() != null) {
                        builder.studentId(s.getStudent().getId())
                                .studentUsername(s.getStudent().getUsername());
                    }
                    if (s.getTeam() != null) {
                        builder.teamId(s.getTeam().getId())
                                .teamName(s.getTeam().getName());
                    }
                    return builder.build();
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/under-reviewed")
    @Operation(
            summary = "Get solutions with insufficient reviews (teacher only)",
            description = "Returns works that have fewer completed reviews than required.",
            operationId = "getUnderReviewedSolutions",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of under-reviewed solutions"),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<List<UnderReviewedSolutionDto>> getUnderReviewedSolutions(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {

        List<Solution> solutions = peerReviewService.getUnderReviewedSolutions(courseId, postId, principal.getId());
        return ResponseEntity.ok(toUnderReviewedDtos(courseId, postId, principal.getId(), solutions));
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

        List<PeerReviewAssignmentDto> assignments = peerReviewService.getMyAssignments(courseId, postId, principal.getId());
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

        PeerReviewAssignmentDto result = peerReviewService.submitReview(courseId, postId, assignmentId, request, principal.getId());
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
