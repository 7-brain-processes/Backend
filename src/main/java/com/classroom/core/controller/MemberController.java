package com.classroom.core.controller;

import com.classroom.core.dto.PageDto;
import com.classroom.core.dto.member.MemberDto;
import com.classroom.core.model.CourseRole;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.CourseMemberService;
import io.swagger.v3.oas.annotations.Operation;
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
            security = @SecurityRequirement(name = "bearerAuth")
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
            summary = "Remove member from course",
            security = @SecurityRequirement(name = "bearerAuth")
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
            summary = "Leave course",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveCourse(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId) {

        courseMemberService.leaveCourse(courseId, principal.getId());
    }
}