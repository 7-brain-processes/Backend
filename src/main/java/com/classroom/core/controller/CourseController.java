package com.classroom.core.controller;

import com.classroom.core.dto.PageDto;
import com.classroom.core.dto.course.CourseDto;
import com.classroom.core.dto.course.CreateCourseRequest;
import com.classroom.core.dto.course.UpdateCourseRequest;
import com.classroom.core.model.CourseRole;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    public ResponseEntity<PageDto<CourseDto>> listMyCourses(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) CourseRole role) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PostMapping
    public ResponseEntity<CourseDto> createCourse(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateCourseRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<CourseDto> getCourse(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PutMapping("/{courseId}")
    public ResponseEntity<CourseDto> updateCourse(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @Valid @RequestBody UpdateCourseRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @DeleteMapping("/{courseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCourse(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
