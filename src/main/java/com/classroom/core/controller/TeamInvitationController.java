package com.classroom.core.controller;

import com.classroom.core.dto.ErrorResponse;
import com.classroom.core.dto.team.*;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.TeamInvitationService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/posts/{postId}/invitations")
@Tag(name = "Team Invitations", description = "Captain-based team formation invitations")
@RequiredArgsConstructor
public class TeamInvitationController {

    private final TeamInvitationService teamInvitationService;

    @GetMapping("/captain/students")
    @Operation(
            summary = "Get list of available students for captain to invite",
            operationId = "getAvailableStudentsForCaptain",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Available students",
                            content = @Content(schema = @Schema(implementation = AvailableStudentDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<List<AvailableStudentDto>> getAvailableStudents(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {

        List<AvailableStudentDto> result = teamInvitationService
                .getAvailableStudentsForCaptain(courseId, postId, principal.getId());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/captain/send")
    @Operation(
            summary = "Send invitation to student (captain only)",
            operationId = "sendInvitation",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Invitation sent",
                            content = @Content(schema = @Schema(implementation = TeamInvitationDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<TeamInvitationDto> sendInvitation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @Valid @RequestBody SendInvitationRequest request) {

        TeamInvitationDto result = teamInvitationService
                .sendInvitation(courseId, postId, request, principal.getId());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/captain")
    @Operation(
            summary = "Get captain's sent invitations",
            operationId = "getCaptainInvitations",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Captain's invitations",
                            content = @Content(schema = @Schema(implementation = TeamInvitationDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<List<TeamInvitationDto>> getCaptainInvitations(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {

        List<TeamInvitationDto> result = teamInvitationService
                .getCaptainTeamInvitations(courseId, postId, principal.getId());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/captain/team")
    @Operation(
            summary = "Get current team composition for captain",
            operationId = "getCaptainTeam",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Captain's current team",
                            content = @Content(schema = @Schema(implementation = StudentTeamDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<StudentTeamDto> getCaptainTeam(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {

        StudentTeamDto result = teamInvitationService
                .getCaptainTeam(courseId, postId, principal.getId());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/student")
    @Operation(
            summary = "Get student's received invitations",
            operationId = "getStudentInvitations",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Student's invitations",
                            content = @Content(schema = @Schema(implementation = TeamInvitationDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<List<TeamInvitationDto>> getStudentInvitations(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {

        List<TeamInvitationDto> result = teamInvitationService
                .getStudentInvitations(courseId, postId, principal.getId());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{invitationId}/respond")
    @Operation(
            summary = "Accept or decline invitation (student only)",
            operationId = "respondToInvitation",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Invitation responded",
                            content = @Content(schema = @Schema(implementation = TeamInvitationDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<TeamInvitationDto> respondToInvitation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID invitationId,
            @Valid @RequestBody RespondInvitationRequest request) {

        TeamInvitationDto result = teamInvitationService
                .respondToInvitation(courseId, postId, invitationId, request, principal.getId());
        return ResponseEntity.ok(result);
    }
}