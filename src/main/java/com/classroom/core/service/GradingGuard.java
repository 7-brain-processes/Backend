package com.classroom.core.service;

import com.classroom.core.exception.BadRequestException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.*;
import com.classroom.core.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GradingGuard {

    private final CourseRepository courseRepository;
    private final CourseMemberRepository courseMemberRepository;
    private final PostRepository postRepository;
    private final SolutionRepository solutionRepository;
    private final GradingConfigRepository gradingConfigRepository;

    public void ensureTeacher(UUID courseId, UUID userId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Course not found");
        }
        CourseMember member = courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this course"));
        if (member.getRole() != CourseRole.TEACHER) {
            throw new ForbiddenException("Only teachers can manage grading configuration");
        }
    }

    public void requireMember(UUID courseId, UUID userId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Course not found");
        }
        courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this course"));
    }

    public Post requireTaskPostInCourse(UUID courseId, UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        if (!post.getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Post not found");
        }
        if (post.getType() != PostType.TASK) {
            throw new BadRequestException("Grading configuration is available only for task posts");
        }
        return post;
    }

    public Solution requireSolutionInPost(UUID postId, UUID solutionId) {
        Solution solution = solutionRepository.findById(solutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Solution not found"));
        if (!solution.getPost().getId().equals(postId)) {
            throw new ResourceNotFoundException("Solution not found");
        }
        return solution;
    }

    public GradingConfig requireGradingConfig(UUID postId) {
        return gradingConfigRepository.findByPostId(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Grading configuration not found for this post"));
    }

    public boolean isTeacher(UUID courseId, UUID userId) {
        return courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .map(m -> m.getRole() == CourseRole.TEACHER)
                .orElse(false);
    }
}
