package com.classroom.core.controller;

import com.classroom.core.dto.ErrorResponse;
import com.classroom.core.dto.team.AutoFormationStudentDto;
import com.classroom.core.dto.team.AutoTeamFormationRequest;
import com.classroom.core.dto.team.AutoTeamFormationResultDto;
import com.classroom.core.dto.team.PostCaptainDto;
import com.classroom.core.dto.team.SelectCaptainsRequest;
import com.classroom.core.dto.team.SelectCaptainsResultDto;
import com.classroom.core.dto.team.SetTeamFormationModeRequest;
import com.classroom.core.dto.team.TeamFormationModeDto;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.PostCaptainService;
import com.classroom.core.service.PostService;
import com.classroom.core.service.TeamFormationService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/posts/{postId}/team-formation")
@Tag(name = "Team Formation", description = "Team formation settings for task posts")
@RequiredArgsConstructor
public class TeamFormationController {

    private final PostService postService;
        private final TeamFormationService teamFormationService;
    private final PostCaptainService postCaptainService;

    @GetMapping("/mode")
    @Operation(
            summary = "Get team formation mode for a task post",
            operationId = "getTeamFormationMode",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Current mode",
                            content = @Content(schema = @Schema(implementation = TeamFormationModeDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<TeamFormationModeDto> getMode(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {

        TeamFormationModeDto result = TeamFormationModeDto.builder()
                .mode(postService.getTeamFormationMode(courseId, postId, principal.getId()))
                .build();

        return ResponseEntity.ok(result);
    }

    @PutMapping("/mode")
    @Operation(
            summary = "Set team formation mode for a task post (teacher only)",
            operationId = "setTeamFormationMode",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Mode saved",
                            content = @Content(schema = @Schema(implementation = TeamFormationModeDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<TeamFormationModeDto> setMode(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @Valid @RequestBody SetTeamFormationModeRequest request) {

        TeamFormationModeDto result = TeamFormationModeDto.builder()
                .mode(postService.setTeamFormationMode(courseId, postId, request.getMode(), principal.getId()))
                .build();

        return ResponseEntity.ok(result);
    }

    @PostMapping("/auto")
    @Operation(
            summary = "Run automatic team formation for a task post (teacher only)",
            operationId = "runAutomaticTeamFormation",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "202", description = "Formation started",
                            content = @Content(schema = @Schema(implementation = AutoTeamFormationResultDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<AutoTeamFormationResultDto> runAutoFormation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @Valid @RequestBody AutoTeamFormationRequest request) {
        AutoTeamFormationResultDto result = teamFormationService
                .runAutomaticFormation(courseId, postId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @GetMapping("/auto/result")
    @Operation(
            summary = "Get automatic team formation result",
            operationId = "getAutomaticTeamFormationResult",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Last formation result",
                            content = @Content(schema = @Schema(implementation = AutoTeamFormationResultDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<AutoTeamFormationResultDto> getAutoFormationResult(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {
        AutoTeamFormationResultDto result = teamFormationService
                .getLastAutomaticFormationResult(courseId, postId, principal.getId());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/auto/students")
    @Operation(
            summary = "List students available for automatic team formation",
            operationId = "listStudentsForAutomaticTeamFormation",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Students available for team formation",
                            content = @Content(schema = @Schema(implementation = AutoFormationStudentDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<List<AutoFormationStudentDto>> listAvailableStudents(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {

        List<AutoFormationStudentDto> result = teamFormationService
                .listAvailableStudents(courseId, postId, principal.getId());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/captains/select")
    @Operation(
            summary = "Select captains randomly for captain-based team formation (teacher only)",
            operationId = "selectCaptains",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Captains selected",
                            content = @Content(schema = @Schema(implementation = SelectCaptainsResultDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<SelectCaptainsResultDto> selectCaptains(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @Valid @RequestBody SelectCaptainsRequest request) {

        SelectCaptainsResultDto result = postCaptainService
                .selectCaptains(courseId, postId, request, principal.getId());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/captains")
    @Operation(
            summary = "Get selected captains for a task post",
            operationId = "getCaptains",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Selected captains",
                            content = @Content(schema = @Schema(implementation = PostCaptainDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<List<PostCaptainDto>> getCaptains(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {

        List<PostCaptainDto> result = postCaptainService
                .getCaptains(courseId, postId, principal.getId());
        return ResponseEntity.ok(result);
    }
}
