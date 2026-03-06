package com.classroom.core.controller;

import com.classroom.core.dto.PageDto;
import com.classroom.core.dto.solution.CreateSolutionRequest;
import com.classroom.core.dto.solution.SolutionDto;
import com.classroom.core.model.SolutionStatus;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.SolutionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/posts/{postId}/solutions")
@RequiredArgsConstructor
public class SolutionController {

    private final SolutionService solutionService;

    @GetMapping
    public ResponseEntity<PageDto<SolutionDto>> listSolutions(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) SolutionStatus status) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PostMapping
    public ResponseEntity<SolutionDto> createSolution(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @Valid @RequestBody CreateSolutionRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @GetMapping("/my")
    public ResponseEntity<SolutionDto> getMySolution(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @GetMapping("/{solutionId}")
    public ResponseEntity<SolutionDto> getSolution(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PutMapping("/{solutionId}")
    public ResponseEntity<SolutionDto> updateSolution(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId,
            @Valid @RequestBody CreateSolutionRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @DeleteMapping("/{solutionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSolution(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID postId,
            @PathVariable UUID solutionId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
