package com.classroom.core.controller;

import com.classroom.core.dto.ErrorResponse;
import com.classroom.core.dto.grading.CriteriaGradeResultDto;
import com.classroom.core.dto.grading.CriteriaGradeSubmissionDto;
import com.classroom.core.dto.grading.GradingConfigDto;
import com.classroom.core.dto.grading.UpsertGradingConfigRequest;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.MultiCriteriaGradingService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/posts/{postId}")
@Tag(name = "Multi-Criteria Grading", description = "Assignment-level grading configuration (criteria and modifiers) and per-solution decomposed grade submission / viewing.")
@RequiredArgsConstructor
public class MultiCriteriaGradingController {

    private final MultiCriteriaGradingService gradingService;

    @GetMapping("/grading-config")
    @Operation(
            summary = "Get grading configuration for an assignment",
            operationId = "getGradingConfig",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Grading configuration",
                            content = @Content(schema = @Schema(implementation = GradingConfigDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<GradingConfigDto> getGradingConfig(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {

        GradingConfigDto result = gradingService.getGradingConfig(courseId, postId, principal.getId());
        if (result == null) {
            return ResponseEntity.ok(GradingConfigDto.builder().postId(postId).build());
        }
        return ResponseEntity.ok(result);
    }

    @PutMapping("/grading-config")
    @Operation(
            summary = "Set or update grading configuration (teacher only)",
            operationId = "upsertGradingConfig",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Configuration saved",
                            content = @Content(schema = @Schema(implementation = GradingConfigDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid configuration",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<GradingConfigDto> upsertGradingConfig(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @Valid @RequestBody UpsertGradingConfigRequest request) {

        GradingConfigDto result = gradingService.upsertGradingConfig(courseId, postId, request, principal.getId());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/grading-config")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Remove grading configuration (teacher only)",
            operationId = "deleteGradingConfig",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "Configuration removed"),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public void deleteGradingConfig(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {

        gradingService.deleteGradingConfig(courseId, postId, principal.getId());
    }

    @GetMapping("/solutions/{solutionId}/criteria-grades")
    @Operation(
            summary = "Get criteria grades for a solution",
            operationId = "getCriteriaGrades",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Criteria grades and computed result",
                            content = @Content(schema = @Schema(implementation = CriteriaGradeResultDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<CriteriaGradeResultDto> getCriteriaGrades(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId) {

        CriteriaGradeResultDto result = gradingService.getCriteriaGrades(courseId, postId, solutionId, principal.getId());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/criteria-grades/publish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Publish criteria grades for the assignment (teacher only)",
            description = "Makes grade decomposition visible to students. Equivalent to setting resultsVisible=true.",
            operationId = "publishCriteriaGrades",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "Grades published"),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public void publishCriteriaGrades(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {

        gradingService.setGradePublished(courseId, postId, principal.getId(), true);
    }

    @DeleteMapping("/criteria-grades/publish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Unpublish criteria grades for the assignment (teacher only)",
            description = "Hides grade decomposition from students.",
            operationId = "unpublishCriteriaGrades",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "Grades unpublished"),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public void unpublishCriteriaGrades(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {

        gradingService.setGradePublished(courseId, postId, principal.getId(), false);
    }

    @GetMapping("/solutions/{solutionId}/grade-decomposition")
    @Operation(
            summary = "Get full grade decomposition for a solution",
            description = "Returns criteria grades, modifier effects, and final score. " +
                    "Teachers can always access any solution. " +
                    "Students can only access their own solution when grades are published.",
            operationId = "getGradeDecomposition",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Full grade decomposition",
                            content = @Content(schema = @Schema(implementation = CriteriaGradeResultDto.class))),
                    @ApiResponse(responseCode = "403", description = "Grades not published or access denied",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<CriteriaGradeResultDto> getGradeDecomposition(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId) {

        CriteriaGradeResultDto result = gradingService.getGradeDecomposition(courseId, postId, solutionId, principal.getId());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/solutions/{solutionId}/criteria-grades")
    @Operation(
            summary = "Save or update criteria grades (teacher only)",
            operationId = "upsertCriteriaGrades",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Grades saved",
                            content = @Content(schema = @Schema(implementation = CriteriaGradeResultDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid grade submission",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<CriteriaGradeResultDto> upsertCriteriaGrades(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId,
            @Valid @RequestBody CriteriaGradeSubmissionDto request) {

        CriteriaGradeResultDto result = gradingService.upsertCriteriaGrades(courseId, postId, solutionId, request, principal.getId());
        return ResponseEntity.ok(result);
    }
}
