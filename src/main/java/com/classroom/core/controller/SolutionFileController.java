package com.classroom.core.controller;

import com.classroom.core.dto.file.FileDto;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.SolutionFileService;
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
@RequestMapping("/api/v1/courses/{courseId}/posts/{postId}/solutions/{solutionId}/files")
@RequiredArgsConstructor
public class SolutionFileController {

    private final SolutionFileService solutionFileService;

    @GetMapping
    public ResponseEntity<List<FileDto>> listSolutionFiles(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PostMapping
    public ResponseEntity<FileDto> uploadSolutionFile(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId,
            @RequestParam("file") MultipartFile file) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @DeleteMapping("/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSolutionFile(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId,
            @PathVariable UUID fileId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> downloadSolutionFile(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId,
            @PathVariable UUID fileId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
