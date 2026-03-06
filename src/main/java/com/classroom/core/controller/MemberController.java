package com.classroom.core.controller;

import com.classroom.core.dto.PageDto;
import com.classroom.core.dto.member.MemberDto;
import com.classroom.core.model.CourseRole;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.CourseMemberService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}")
@RequiredArgsConstructor
public class MemberController {

    private final CourseMemberService courseMemberService;

    @GetMapping("/members")
    public ResponseEntity<PageDto<MemberDto>> listMembers(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) CourseRole role) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @DeleteMapping("/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PostMapping("/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveCourse(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
