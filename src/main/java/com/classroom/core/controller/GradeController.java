package com.classroom.core.controller;

import com.classroom.core.dto.ErrorResponse;
import com.classroom.core.dto.PageDto;
import com.classroom.core.dto.comment.CommentDto;
import com.classroom.core.dto.comment.CreateCommentRequest;
import com.classroom.core.dto.solution.GradeRequest;
import com.classroom.core.dto.solution.SolutionDto;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.CommentService;
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
@RequestMapping("/api/v1/courses/{courseId}/posts/{postId}/solutions/{solutionId}")
@Tag(name = "Grading", description = "Marks and teacher comments on solutions")
@RequiredArgsConstructor
public class GradeController {

    private final SolutionService solutionService;
    private final CommentService commentService;

    @PutMapping("/grade")
    @Operation(
            summary = "Set or update a grade for a solution (teacher only)",
            operationId = "gradeSolution",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Grade saved",
                            content = @Content(schema = @Schema(implementation = SolutionDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<SolutionDto> gradeSolution(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId,
            @Valid @RequestBody GradeRequest request) {
        SolutionDto result = solutionService.gradeSolution(courseId, postId, solutionId, request, principal.getId());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/grade")
    @Operation(
            summary = "Remove a grade from a solution (teacher only)",
            operationId = "removeGrade",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Grade removed",
                            content = @Content(schema = @Schema(implementation = SolutionDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<SolutionDto> removeGrade(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId) {
        SolutionDto result = solutionService.removeGrade(courseId, postId, solutionId, principal.getId());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/comments")
    @Operation(
            summary = "List teacher comments on a solution",
            operationId = "listSolutionComments",
            security = @SecurityRequirement(name = "bearerAuth"),
            parameters = {
                    @Parameter(name = "page", description = "Zero-based page index", schema = @Schema(type = "integer", defaultValue = "0")),
                    @Parameter(name = "size", description = "Page size", schema = @Schema(type = "integer", defaultValue = "20"))
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Paginated comments",
                            content = @Content(schema = @Schema(implementation = PageDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<PageDto<CommentDto>> listSolutionComments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = commentService.listSolutionComments(courseId, postId, solutionId,
                org.springframework.data.domain.PageRequest.of(page, size), principal.getId());
        return ResponseEntity.ok(PageDto.from(result));
    }

    @PostMapping("/comments")
    @Operation(
            summary = "Leave a comment on a solution (teacher only)",
            operationId = "createSolutionComment",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Comment created",
                            content = @Content(schema = @Schema(implementation = CommentDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<CommentDto> createSolutionComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId,
            @Valid @RequestBody CreateCommentRequest request) {
        CommentDto result = commentService.createSolutionComment(courseId, postId, solutionId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/comments/{commentId}")
    @Operation(
            summary = "Edit a solution comment (teacher only)",
            operationId = "updateSolutionComment",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Comment updated",
                            content = @Content(schema = @Schema(implementation = CommentDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<CommentDto> updateSolutionComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId,
            @PathVariable UUID commentId,
            @Valid @RequestBody CreateCommentRequest request) {
        CommentDto result = commentService.updateSolutionComment(courseId, postId, solutionId, commentId, request, principal.getId());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete a solution comment (teacher only)",
            operationId = "deleteSolutionComment",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "Deleted"),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public void deleteSolutionComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId,
            @PathVariable UUID commentId) {
        commentService.deleteSolutionComment(courseId, postId, solutionId, commentId, principal.getId());
    }
}
