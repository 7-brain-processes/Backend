package com.classroom.core.controller;

import com.classroom.core.dto.ErrorResponse;
import com.classroom.core.dto.PageDto;
import com.classroom.core.dto.member.MemberDto;
import com.classroom.core.model.CourseRole;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.CourseMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}")
@Tag(name = "Members", description = "Course member management")
@RequiredArgsConstructor
public class MemberController {

    private final CourseMemberService courseMemberService;

    @GetMapping("/members")
    @Operation(
            summary = "List course members",
            operationId = "listMembers",
            security = @SecurityRequirement(name = "bearerAuth"),
            parameters = {
                    @Parameter(name = "page", description = "Zero-based page index", schema = @Schema(type = "integer", defaultValue = "0")),
                    @Parameter(name = "size", description = "Page size", schema = @Schema(type = "integer", defaultValue = "20")),
                    @Parameter(name = "role", description = "Filter by role", schema = @Schema(implementation = CourseRole.class))
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Paginated members",
                            content = @Content(schema = @Schema(implementation = PageDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<PageDto<MemberDto>> listMembers(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) CourseRole role) {

        var result = courseMemberService.listMembers(
                courseId,
                role,
                PageRequest.of(page, size),
                principal.getId()
        );

        return ResponseEntity.ok(PageDto.from(result));
    }

    @DeleteMapping("/members/{userId}")
    @Operation(
            summary = "Remove a student from the course (teacher only)",
            operationId = "removeMember",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "Member removed"),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID userId) {

        courseMemberService.removeMember(courseId, userId, principal.getId());
    }

    @PostMapping("/leave")
    @Operation(
            summary = "Leave the course (current user)",
            operationId = "leaveCourse",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "Left the course"),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveCourse(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId) {

        courseMemberService.leaveCourse(courseId, principal.getId());
    }
}