package com.classroom.core.controller;

import com.classroom.core.dto.PageDto;
import com.classroom.core.dto.post.CreatePostRequest;
import com.classroom.core.dto.post.PostDto;
import com.classroom.core.dto.post.UpdatePostRequest;
import com.classroom.core.model.PostType;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping
    public ResponseEntity<PageDto<PostDto>> listPosts(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) PostType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var result = postService.listPosts(courseId, type, PageRequest.of(page, size), principal.getId());
        return ResponseEntity.ok(PageDto.from(result));
    }

    @PostMapping
    public ResponseEntity<PostDto> createPost(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreatePostRequest request) {

        var result = postService.createPost(courseId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{postId}")
    public ResponseEntity<PostDto> getPost(
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserPrincipal principal) {

        var result = postService.getPost(courseId, postId, principal.getId());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{postId}")
    public ResponseEntity<PostDto> updatePost(
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdatePostRequest request) {

        var result = postService.updatePost(courseId, postId, request, principal.getId());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserPrincipal principal) {

        postService.deletePost(courseId, postId, principal.getId());
    }
}