package com.classroom.core.controller;

import com.classroom.core.dto.PageDto;
import com.classroom.core.dto.course.CourseDto;
import com.classroom.core.dto.course.CreateCourseRequest;
import com.classroom.core.dto.course.UpdateCourseRequest;
import com.classroom.core.model.CourseRole;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

        Page<CourseDto> result = courseService.listMyCourses(
                principal.getId(),
                role,
                PageRequest.of(page, size)
        );

        return ResponseEntity.ok(PageDto.from(result));
    }

    @PostMapping
    public ResponseEntity<CourseDto> createCourse(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateCourseRequest request) {

        CourseDto result = courseService.createCourse(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<CourseDto> getCourse(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId) {

        CourseDto result = courseService.getCourse(courseId, principal.getId());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{courseId}")
    public ResponseEntity<CourseDto> updateCourse(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @Valid @RequestBody UpdateCourseRequest request) {

        CourseDto result = courseService.updateCourse(courseId, request, principal.getId());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{courseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCourse(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId) {

        courseService.deleteCourse(courseId, principal.getId());
    }
}