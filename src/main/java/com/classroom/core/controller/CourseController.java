package com.classroom.core.controller;

import com.classroom.core.dto.PageDto;
import com.classroom.core.dto.course.CourseDto;
import com.classroom.core.dto.course.CreateCourseRequest;
import com.classroom.core.dto.course.UpdateCourseRequest;
import com.classroom.core.model.CourseRole;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Courses", description = "Course management")
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    @Operation(
            summary = "List current user's courses",
            security = @SecurityRequirement(name = "bearerAuth")
    )
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
    @Operation(
            summary = "Create Course",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<CourseDto> createCourse(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateCourseRequest request) {

        CourseDto result = courseService.createCourse(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{courseId}")
    @Operation(
            summary = "Get course details",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<CourseDto> getCourse(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId) {

        CourseDto result = courseService.getCourse(courseId, principal.getId());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{courseId}")
    @Operation(
            summary = "Update course",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<CourseDto> updateCourse(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @Valid @RequestBody UpdateCourseRequest request) {

        CourseDto result = courseService.updateCourse(courseId, request, principal.getId());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{courseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete course",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public void deleteCourse(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId) {

        courseService.deleteCourse(courseId, principal.getId());
    }
}