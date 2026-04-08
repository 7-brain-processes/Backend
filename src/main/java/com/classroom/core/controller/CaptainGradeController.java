package com.classroom.core.controller;

import com.classroom.core.dto.ErrorResponse;
import com.classroom.core.dto.team.CaptainGradeDistributionRequest;
import com.classroom.core.dto.team.TeamGradeDistributionDto;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.CaptainGradeService;
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
@RequestMapping("/api/v1/courses/{courseId}/posts/{postId}/captain-grade")
@Tag(name = "Captain Grade Distribution", description = "Captain-driven grade distribution for team members")
@RequiredArgsConstructor
public class CaptainGradeController {

    private final CaptainGradeService captainGradeService;

    @GetMapping
    @Operation(
            summary = "Get grade distribution form for captain",
            operationId = "getCaptainGradeDistributionForm",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Distribution form with current grades",
                            content = @Content(schema = @Schema(implementation = TeamGradeDistributionDto.class))),
                    @ApiResponse(responseCode = "400", description = "Grade not set or mode not CAPTAIN_MANUAL",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "User is not a captain for this post",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Post or team not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<TeamGradeDistributionDto> getDistributionForm(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {

        return ResponseEntity.ok(captainGradeService.getDistributionForm(courseId, postId, principal.getId()));
    }

    @PutMapping
    @Operation(
            summary = "Save captain's grade distribution",
            operationId = "saveCaptainGradeDistribution",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Distribution saved",
                            content = @Content(schema = @Schema(implementation = TeamGradeDistributionDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid distribution (sum mismatch, missing members, etc.)",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "User is not a captain for this post",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Post or team not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<TeamGradeDistributionDto> saveDistribution(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @Valid @RequestBody CaptainGradeDistributionRequest request) {

        return ResponseEntity.ok(captainGradeService.saveDistribution(courseId, postId, request, principal.getId()));
    }
}
