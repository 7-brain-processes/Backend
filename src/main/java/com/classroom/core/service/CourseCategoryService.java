package com.classroom.core.service;

import com.classroom.core.dto.course.*;
import com.classroom.core.exception.BadRequestException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.Course;
import com.classroom.core.model.CourseCategory;
import com.classroom.core.model.CourseMember;
import com.classroom.core.model.CourseRole;
import com.classroom.core.repository.CourseCategoryRepository;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseCategoryService {

    private final CourseCategoryRepository courseCategoryRepository;
    private final CourseRepository courseRepository;
    private final CourseMemberRepository courseMemberRepository;

    public List<CourseCategoryDto> listCategories(UUID courseId, UUID currentUserId) {
        Course course = getCourseOrThrow(courseId);
        ensureMember(courseId, currentUserId);

        return courseCategoryRepository.findByCourseIdOrderByTitleAsc(courseId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public CourseCategoryDto getCategory(UUID courseId, UUID categoryId, UUID currentUserId) {
        getCourseOrThrow(courseId);
        ensureMember(courseId, currentUserId);

        CourseCategory category = courseCategoryRepository.findByIdAndCourseId(categoryId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        return toDto(category);
    }

    @Transactional
    public CourseCategoryDto createCategory(UUID courseId, CreateCourseCategoryRequest request, UUID currentUserId) {
        Course course = getCourseOrThrow(courseId);
        ensureTeacher(courseId, currentUserId);

        CourseCategory category = CourseCategory.builder()
                .course(course)
                .title(request.getTitle())
                .description(request.getDescription())
                .active(request.getActive() == null ? true : request.getActive())
                .build();

        category = courseCategoryRepository.save(category);
        return toDto(category);
    }

    @Transactional
    public CourseCategoryDto updateCategory(UUID courseId, UUID categoryId, UpdateCourseCategoryRequest request, UUID currentUserId) {
        getCourseOrThrow(courseId);
        ensureTeacher(courseId, currentUserId);

        CourseCategory category = courseCategoryRepository.findByIdAndCourseId(categoryId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (request.getTitle() != null) {
            category.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            category.setDescription(request.getDescription());
        }
        if (request.getActive() != null) {
            category.setActive(request.getActive());
        }
        category = courseCategoryRepository.save(category);
        return toDto(category);
    }

    @Transactional
    public void deleteCategory(UUID courseId, UUID categoryId, UUID currentUserId) {
        getCourseOrThrow(courseId);
        ensureTeacher(courseId, currentUserId);

        CourseCategory category = courseCategoryRepository.findByIdAndCourseId(categoryId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        courseCategoryRepository.delete(category);
    }

    public CourseCategoryDto getMemberCategory(UUID courseId, UUID userId, UUID currentUserId) {
        getCourseOrThrow(courseId);
        CourseMember currentMember = ensureMember(courseId, currentUserId);

        if (!currentMember.getUser().getId().equals(userId) && currentMember.getRole() != CourseRole.TEACHER) {
            throw new ForbiddenException("Only teacher can view other members' category");
        }

        CourseMember member = courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));

        if (member.getCategory() == null) {
            return null;
        }

        return toDto(member.getCategory());
    }

    @Transactional
    public CourseCategoryDto setMyCategory(UUID courseId, SetMyCategoryRequest request, UUID currentUserId) {
        getCourseOrThrow(courseId);
        CourseMember member = ensureMember(courseId, currentUserId);

        if (request.getCategoryId() == null) {
            member.setCategory(null);
            courseMemberRepository.save(member);
            return null;
        }

        CourseCategory category = courseCategoryRepository.findByIdAndCourseId(request.getCategoryId(), courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (!category.isActive()) {
            throw new BadRequestException("Category is inactive");
        }

        member.setCategory(category);
        courseMemberRepository.save(member);

        return toDto(category);
    }

    private CourseCategoryDto toDto(CourseCategory category) {
        return CourseCategoryDto.builder()
                .id(category.getId())
                .title(category.getTitle())
                .description(category.getDescription())
                .active(category.isActive())
                .createdAt(category.getCreatedAt())
                .build();
    }

    private Course getCourseOrThrow(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
    }

    private CourseMember ensureMember(UUID courseId, UUID userId) {
        return courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this course"));
    }

    private CourseMember ensureTeacher(UUID courseId, UUID userId) {
        CourseMember member = ensureMember(courseId, userId);

        if (member.getRole() != CourseRole.TEACHER) {
            throw new ForbiddenException("Only teachers can manage course categories");
        }

        return member;
    }
}
