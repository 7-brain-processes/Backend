package com.classroom.core.controller;

import com.classroom.core.dto.file.FileDto;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.PostMaterialService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/posts/{postId}/materials")
@RequiredArgsConstructor
public class PostMaterialController {

    private final PostMaterialService postMaterialService;

    @GetMapping
    public ResponseEntity<List<FileDto>> listPostMaterials(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PostMapping
    public ResponseEntity<FileDto> uploadPostMaterial(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @RequestParam("file") MultipartFile file) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @DeleteMapping("/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePostMaterial(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID fileId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> downloadPostMaterial(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID fileId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
