package com.classroom.core.controller;

import com.classroom.core.dto.PageDto;
import com.classroom.core.dto.comment.CommentDto;
import com.classroom.core.dto.comment.CreateCommentRequest;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.CommentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    public ResponseEntity<PageDto<CommentDto>> listPostComments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PostMapping
    public ResponseEntity<CommentDto> createPostComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @Valid @RequestBody CreateCommentRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<CommentDto> updatePostComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID commentId,
            @Valid @RequestBody CreateCommentRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePostComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID commentId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
