package com.classroom.core.controller;

import com.classroom.core.dto.ErrorResponse;
import com.classroom.core.dto.PageDto;
import com.classroom.core.dto.solution.CreateSolutionRequest;
import com.classroom.core.dto.solution.SolutionDto;
import com.classroom.core.model.SolutionStatus;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.SolutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/posts/{postId}/solutions")
@Tag(name = "Solutions", description = "Student task solutions (answers)")
@RequiredArgsConstructor
public class SolutionController {

    private final SolutionService solutionService;

    @GetMapping
    @Operation(
            summary = "List all solutions for a task (teacher only)",
            operationId = "listSolutions",
            security = @SecurityRequirement(name = "bearerAuth"),
            parameters = {
                    @Parameter(name = "page", description = "Zero-based page index", schema = @Schema(type = "integer", defaultValue = "0")),
                    @Parameter(name = "size", description = "Page size", schema = @Schema(type = "integer", defaultValue = "20")),
                    @Parameter(name = "status", description = "Filter by solution status", schema = @Schema(implementation = SolutionStatus.class))
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Paginated solutions",
                            content = @Content(schema = @Schema(implementation = PageDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<PageDto<SolutionDto>> listSolutions(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) SolutionStatus status) {
        var result = solutionService.listSolutions(courseId, postId, status,
                org.springframework.data.domain.PageRequest.of(page, size), principal.getId());
        return ResponseEntity.ok(PageDto.from(result));
    }

    @PostMapping
    @Operation(
            summary = "Submit a solution for a task (student only)",
            operationId = "createSolution",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Solution submitted",
                            content = @Content(schema = @Schema(implementation = SolutionDto.class))),
                    @ApiResponse(responseCode = "400", description = "Post is not a task or deadline passed",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "Solution already exists",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<SolutionDto> createSolution(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @Valid @RequestBody CreateSolutionRequest request) {
        SolutionDto result = solutionService.createSolution(courseId, postId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/my")
    @Operation(
            summary = "Get the current student's solution for a task",
            operationId = "getMySolution",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Student's solution",
                            content = @Content(schema = @Schema(implementation = SolutionDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<SolutionDto> getMySolution(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {
        SolutionDto result = solutionService.getMySolution(courseId, postId, principal.getId());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{solutionId}")
    @Operation(
            summary = "Get a specific solution (teacher or owner)",
            operationId = "getSolution",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Solution details",
                            content = @Content(schema = @Schema(implementation = SolutionDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<SolutionDto> getSolution(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId) {
        SolutionDto result = solutionService.getSolution(courseId, postId, solutionId, principal.getId());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{solutionId}")
    @Operation(
            summary = "Update solution text (student, own only)",
            operationId = "updateSolution",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Solution updated",
                            content = @Content(schema = @Schema(implementation = SolutionDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<SolutionDto> updateSolution(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId,
            @Valid @RequestBody CreateSolutionRequest request) {
        SolutionDto result = solutionService.updateSolution(courseId, postId, solutionId, request, principal.getId());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{solutionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete a solution (student, own only)",
            operationId = "deleteSolution",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "Deleted"),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public void deleteSolution(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId) {
        solutionService.deleteSolution(courseId, postId, solutionId, principal.getId());
    }
}
