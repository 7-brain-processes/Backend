package com.classroom.core.controller;

import com.classroom.core.dto.ErrorResponse;
import com.classroom.core.dto.team.CourseTeamDto;
import com.classroom.core.dto.team.CreateCourseTeamRequest;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.CourseTeamService;
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
@RequestMapping("/api/v1/courses/{courseId}")
@Tag(name = "Teams", description = "Course team management")
@RequiredArgsConstructor
public class CourseTeamController {

    private final CourseTeamService courseTeamService;

    @GetMapping("/teams")
    @Operation(
            summary = "List teams in a course",
            operationId = "listCourseTeams",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Course teams",
                            content = @Content(schema = @Schema(implementation = CourseTeamDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<List<CourseTeamDto>> listTeams(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId) {

        List<CourseTeamDto> result = courseTeamService.listTeams(courseId, principal.getId());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/teams")
    @Operation(
            summary = "Create a course team (teacher only)",
            operationId = "createCourseTeam",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Team created",
                            content = @Content(schema = @Schema(implementation = CourseTeamDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "Conflict",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<CourseTeamDto> createTeam(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @Valid @RequestBody CreateCourseTeamRequest request) {

        CourseTeamDto result = courseTeamService.createTeam(courseId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
