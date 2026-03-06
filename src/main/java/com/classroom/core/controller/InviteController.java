package com.classroom.core.controller;

import com.classroom.core.dto.course.CourseDto;
import com.classroom.core.dto.invite.CreateInviteRequest;
import com.classroom.core.dto.invite.InviteDto;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.InviteService;
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
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;

    @GetMapping("/courses/{courseId}/invites")
    public ResponseEntity<List<InviteDto>> listInvites(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId) {

        return ResponseEntity.ok(inviteService.listInvites(courseId, principal.getId()));
    }

    @PostMapping("/courses/{courseId}/invites")
    public ResponseEntity<InviteDto> createInvite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @Valid @RequestBody CreateInviteRequest request) {

        InviteDto result = inviteService.createInvite(courseId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @DeleteMapping("/courses/{courseId}/invites/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeInvite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID inviteId) {

        inviteService.revokeInvite(courseId, inviteId, principal.getId());
    }

    @PostMapping("/invites/{code}/join")
    public ResponseEntity<CourseDto> joinCourse(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String code) {

        return ResponseEntity.ok(inviteService.joinCourse(code, principal.getId()));
    }
}