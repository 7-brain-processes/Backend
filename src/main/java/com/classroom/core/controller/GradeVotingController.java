package com.classroom.core.controller;

import com.classroom.core.dto.ErrorResponse;
import com.classroom.core.dto.team.CaptainGradeDistributionRequest;
import com.classroom.core.dto.team.TeamGradeDistributionDto;
import com.classroom.core.dto.team.VoteStatusDto;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.GradeVotingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Tag(name = "Grade Voting", description = "Team member voting for grade distribution")
@RequiredArgsConstructor
public class GradeVotingController {

    private final GradeVotingService gradeVotingService;

   
    @GetMapping("/api/v1/courses/{courseId}/posts/{postId}/grade-vote")
    @Operation(
            summary = "Get vote status for current user's team",
            operationId = "getGradeVoteStatus",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Vote status",
                            content = @Content(schema = @Schema(implementation = VoteStatusDto.class))),
                    @ApiResponse(responseCode = "400", description = "Vote mode not enabled or grade not set",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Not a team member for this post",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Post not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<VoteStatusDto> getVoteStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {

        return ResponseEntity.ok(gradeVotingService.getVoteStatus(courseId, postId, principal.getId()));
    }

    @PostMapping("/api/v1/courses/{courseId}/posts/{postId}/grade-vote")
    @Operation(
            summary = "Submit grade distribution vote",
            operationId = "submitGradeVote",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Vote submitted, returns updated status",
                            content = @Content(schema = @Schema(implementation = VoteStatusDto.class))),
                    @ApiResponse(responseCode = "400", description = "Already voted, sum mismatch, or missing members",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Not a team member for this post",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Post not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<VoteStatusDto> submitVote(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @Valid @RequestBody CaptainGradeDistributionRequest request) {

        return ResponseEntity.ok(gradeVotingService.submitVote(courseId, postId, request, principal.getId()));
    }

   
    @GetMapping("/api/v1/courses/{courseId}/posts/{postId}/teams/{teamId}/grade-vote")
    @Operation(
            summary = "Get vote status for a specific team (teacher only)",
            operationId = "getTeamGradeVoteStatus",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Vote status",
                            content = @Content(schema = @Schema(implementation = VoteStatusDto.class))),
                    @ApiResponse(responseCode = "400", description = "Vote mode not enabled",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Not a teacher",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Post or team not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<VoteStatusDto> getTeamVoteStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID teamId) {

        return ResponseEntity.ok(
                gradeVotingService.getTeamVoteStatus(courseId, postId, teamId, principal.getId()));
    }

    @PostMapping("/api/v1/courses/{courseId}/posts/{postId}/teams/{teamId}/grade-vote/finalize")
    @Operation(
            summary = "Finalize voting and save individual grades (teacher only)",
            operationId = "finalizeGradeVote",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Voting finalized, individual grades saved",
                            content = @Content(schema = @Schema(implementation = TeamGradeDistributionDto.class))),
                    @ApiResponse(responseCode = "400", description = "No votes yet or grade not set",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Not a teacher",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Post or team not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<TeamGradeDistributionDto> finalizeVoting(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID teamId) {

        return ResponseEntity.ok(
                gradeVotingService.finalizeVoting(courseId, postId, teamId, principal.getId()));
    }
}
