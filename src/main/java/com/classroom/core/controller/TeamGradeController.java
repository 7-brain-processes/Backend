package com.classroom.core.controller;

import com.classroom.core.dto.ErrorResponse;
import com.classroom.core.dto.team.SetTeamGradeDistributionModeRequest;
import com.classroom.core.dto.team.TeamGradeDistributionDto;
import com.classroom.core.dto.team.TeamGradeDto;
import com.classroom.core.dto.team.UpsertTeamGradeRequest;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.TeamGradeService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/posts/{postId}/teams/{teamId}/grade")
@Tag(name = "Team Grading", description = "Team-level grading for task posts")
@RequiredArgsConstructor
public class TeamGradeController {

    private final TeamGradeService teamGradeService;

    @GetMapping
    @Operation(
            summary = "Get team grade",
            operationId = "getTeamGrade",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Team grade",
                            content = @Content(schema = @Schema(implementation = TeamGradeDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<TeamGradeDto> getGrade(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID teamId) {

        TeamGradeDto result = teamGradeService.getTeamGrade(courseId, postId, teamId, principal.getId());
        return ResponseEntity.ok(result);
    }

    @PutMapping
    @Operation(
            summary = "Set or update team grade (teacher only)",
            operationId = "upsertTeamGrade",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Team grade saved",
                            content = @Content(schema = @Schema(implementation = TeamGradeDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<TeamGradeDto> upsertGrade(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID teamId,
            @Valid @RequestBody UpsertTeamGradeRequest request) {

        TeamGradeDto result = teamGradeService.upsertTeamGrade(courseId, postId, teamId, request, principal.getId());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/distribution")
    @Operation(
            summary = "Get team grade distribution",
            operationId = "getTeamGradeDistribution",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Distribution details",
                            content = @Content(schema = @Schema(implementation = TeamGradeDistributionDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<TeamGradeDistributionDto> getDistribution(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID teamId) {

        TeamGradeDistributionDto result = teamGradeService.getDistribution(courseId, postId, teamId, principal.getId());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/distribution")
    @Operation(
            summary = "Set team grade distribution mode (teacher only)",
            operationId = "setTeamGradeDistributionMode",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Distribution mode saved",
                            content = @Content(schema = @Schema(implementation = TeamGradeDistributionDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<TeamGradeDistributionDto> setDistributionMode(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID teamId,
            @Valid @RequestBody SetTeamGradeDistributionModeRequest request) {

        TeamGradeDistributionDto result = teamGradeService.setDistributionMode(courseId, postId, teamId, request, principal.getId());
        return ResponseEntity.ok(result);
    }
}
