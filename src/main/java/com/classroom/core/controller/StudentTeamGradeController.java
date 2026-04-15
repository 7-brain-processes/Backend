package com.classroom.core.controller;

import com.classroom.core.dto.ErrorResponse;
import com.classroom.core.dto.team.MyTeamGradeDto;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.TeamGradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Student Team Grades", description = "Student-facing team grade information")
@RequiredArgsConstructor
public class StudentTeamGradeController {

    private final TeamGradeService teamGradeService;

    @GetMapping("/api/v1/courses/{courseId}/posts/{postId}/my-team-grade")
    @Operation(
            summary = "Get current student's team grade information for an assignment",
            operationId = "getMyTeamGrade",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Team grade information",
                            content = @Content(schema = @Schema(implementation = MyTeamGradeDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<MyTeamGradeDto> getMyTeamGrade(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {

        return ResponseEntity.ok(teamGradeService.getCurrentStudentTeamGrade(courseId, postId, principal.getId()));
    }
}
