package com.classroom.core.controller;

import com.classroom.core.dto.PageDto;
import com.classroom.core.dto.comment.CommentDto;
import com.classroom.core.dto.comment.CreateCommentRequest;
import com.classroom.core.dto.solution.GradeRequest;
import com.classroom.core.dto.solution.SolutionDto;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.CommentService;
import com.classroom.core.service.SolutionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/posts/{postId}/solutions/{solutionId}")
@RequiredArgsConstructor
public class GradeController {

    private final SolutionService solutionService;
    private final CommentService commentService;

    @PutMapping("/grade")
    public ResponseEntity<SolutionDto> gradeSolution(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId,
            @Valid @RequestBody GradeRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @GetMapping("/comments")
    public ResponseEntity<PageDto<CommentDto>> listSolutionComments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PostMapping("/comments")
    public ResponseEntity<CommentDto> createSolutionComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId,
            @Valid @RequestBody CreateCommentRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PutMapping("/comments/{commentId}")
    public ResponseEntity<CommentDto> updateSolutionComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId,
            @PathVariable UUID commentId,
            @Valid @RequestBody CreateCommentRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSolutionComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId,
            @PathVariable UUID commentId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
