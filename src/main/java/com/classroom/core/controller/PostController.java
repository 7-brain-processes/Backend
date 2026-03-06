package com.classroom.core.controller;

import com.classroom.core.dto.PageDto;
import com.classroom.core.dto.post.CreatePostRequest;
import com.classroom.core.dto.post.PostDto;
import com.classroom.core.dto.post.UpdatePostRequest;
import com.classroom.core.model.PostType;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.PostService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping
    public ResponseEntity<PageDto<PostDto>> listPosts(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) PostType type) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PostMapping
    public ResponseEntity<PostDto> createPost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @Valid @RequestBody CreatePostRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @GetMapping("/{postId}")
    public ResponseEntity<PostDto> getPost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PutMapping("/{postId}")
    public ResponseEntity<PostDto> updatePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @Valid @RequestBody UpdatePostRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
