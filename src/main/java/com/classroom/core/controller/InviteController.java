package com.classroom.core.controller;

import com.classroom.core.dto.ErrorResponse;
import com.classroom.core.dto.course.CourseDto;
import com.classroom.core.dto.invite.CreateInviteRequest;
import com.classroom.core.dto.invite.InviteDto;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.InviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
@RequestMapping("/api/v1")
@Tag(name = "Invites", description = "Invite links for teachers and students")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;

    @GetMapping("/courses/{courseId}/invites")
    @Operation(
            summary = "List active invites for the course (teacher only)",
            operationId = "listInvites",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of invites",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = InviteDto.class)))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<List<InviteDto>> listInvites(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId) {

        return ResponseEntity.ok(inviteService.listInvites(courseId, principal.getId()));
    }

    @PostMapping("/courses/{courseId}/invites")
    @Operation(
            summary = "Create an invite code (teacher only)",
            operationId = "createInvite",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Invite created",
                            content = @Content(schema = @Schema(implementation = InviteDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<InviteDto> createInvite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @Valid @RequestBody CreateInviteRequest request) {

        InviteDto result = inviteService.createInvite(courseId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @DeleteMapping("/courses/{courseId}/invites/{inviteId}")
    @Operation(
            summary = "Revoke an invite (teacher only)",
            operationId = "revokeInvite",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "Invite revoked"),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeInvite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID inviteId) {

        inviteService.revokeInvite(courseId, inviteId, principal.getId());
    }

    @PostMapping("/invites/{code}/join")
    @Operation(
            summary = "Join a course using an invite code",
            operationId = "joinCourse",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully joined",
                            content = @Content(schema = @Schema(implementation = CourseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Invite not found or expired",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "Already a member",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<CourseDto> joinCourse(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String code) {

        return ResponseEntity.ok(inviteService.joinCourse(code, principal.getId()));
    }
}