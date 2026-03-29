package com.classroom.core.controller;

import com.classroom.core.dto.ErrorResponse;
import com.classroom.core.dto.course.CourseCategoryDto;
import com.classroom.core.dto.course.CreateCourseCategoryRequest;
import com.classroom.core.dto.course.SetMyCategoryRequest;
import com.classroom.core.dto.course.UpdateCourseCategoryRequest;
import com.classroom.core.service.CourseCategoryService;
import com.classroom.core.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}")
@Tag(name = "Course Categories", description = "Manage course student categories")
@RequiredArgsConstructor
public class CourseCategoryController {

    private final CourseCategoryService courseCategoryService;

    @GetMapping("/categories")
    @Operation(
            summary = "List categories for a course",
            operationId = "listCourseCategories",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Course categories",
                            content = @Content(schema = @Schema(implementation = CourseCategoryDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<List<CourseCategoryDto>> listCategories(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId) {

        List<CourseCategoryDto> result = courseCategoryService.listCategories(courseId, principal.getId());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/categories")
    @Operation(
            summary = "Create a course category (teacher only)",
            operationId = "createCourseCategory",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Category created",
                            content = @Content(schema = @Schema(implementation = CourseCategoryDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<CourseCategoryDto> createCategory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @Valid @RequestBody CreateCourseCategoryRequest request) {

        CourseCategoryDto category = courseCategoryService.createCategory(courseId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(category);
    }

    @GetMapping("/categories/{categoryId}")
    @Operation(
            summary = "Get course category",
            operationId = "getCourseCategory",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Category details",
                            content = @Content(schema = @Schema(implementation = CourseCategoryDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<CourseCategoryDto> getCategory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID categoryId) {

        CourseCategoryDto category = courseCategoryService.getCategory(courseId, categoryId, principal.getId());
        return ResponseEntity.ok(category);
    }

    @PutMapping("/categories/{categoryId}")
    @Operation(
            summary = "Update course category (teacher only)",
            operationId = "updateCourseCategory",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Category updated",
                            content = @Content(schema = @Schema(implementation = CourseCategoryDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<CourseCategoryDto> updateCategory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCourseCategoryRequest request) {

        CourseCategoryDto category = courseCategoryService.updateCategory(courseId, categoryId, request, principal.getId());
        return ResponseEntity.ok(category);
    }

    @DeleteMapping("/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete course category (teacher only)",
            operationId = "deleteCourseCategory",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "Deleted"),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public void deleteCategory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID categoryId) {

        courseCategoryService.deleteCategory(courseId, categoryId, principal.getId());
    }

    @GetMapping("/members/{memberId}/category")
    @Operation(
            summary = "Get member selected category",
            operationId = "getMemberCategory",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Member category",
                            content = @Content(schema = @Schema(implementation = CourseCategoryDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<CourseCategoryDto> getMemberCategory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID memberId) {

        CourseCategoryDto category = courseCategoryService.getMemberCategory(courseId, memberId, principal.getId());
        return ResponseEntity.ok(category);
    }

    @GetMapping("/members/me/category")
    @Operation(
            summary = "Get current user's selected category",
            operationId = "getMyCategory",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Member category",
                            content = @Content(schema = @Schema(implementation = CourseCategoryDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<CourseCategoryDto> getMyCategory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId) {

        CourseCategoryDto category = courseCategoryService.getMemberCategory(courseId, principal.getId(), principal.getId());
        return ResponseEntity.ok(category);
    }

    @PutMapping("/members/me/category")
    @Operation(
            summary = "Set current user's selected category",
            operationId = "setMyCategory",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Category set",
                            content = @Content(schema = @Schema(implementation = CourseCategoryDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<CourseCategoryDto> setMyCategory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @Valid @RequestBody SetMyCategoryRequest request) {

        CourseCategoryDto category = courseCategoryService.setMyCategory(courseId, request, principal.getId());
        return ResponseEntity.ok(category);
    }
}

