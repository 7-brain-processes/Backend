package com.classroom.core.service;

import com.classroom.core.dto.auth.UserDto;
import com.classroom.core.dto.solution.CreateSolutionRequest;
import com.classroom.core.dto.solution.GradeRequest;
import com.classroom.core.dto.solution.SolutionDto;
import com.classroom.core.exception.BadRequestException;
import com.classroom.core.exception.DuplicateResourceException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.*;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.PostRepository;
import com.classroom.core.repository.SolutionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SolutionService {

    private final SolutionRepository solutionRepository;
    private final PostRepository postRepository;
    private final CourseMemberRepository courseMemberRepository;

    public Page<SolutionDto> listSolutions(UUID courseId, UUID postId, SolutionStatus status,
                                           Pageable pageable, UUID userId) {
        CourseMember member = requireMember(courseId, userId);
        if (member.getRole() != CourseRole.TEACHER) {
            throw new ForbiddenException("Only teachers can list all solutions");
        }
        postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        Page<Solution> page = (status != null)
                ? solutionRepository.findByPostIdAndStatus(postId, status, pageable)
                : solutionRepository.findByPostId(postId, pageable);
        return page.map(this::toDto);
    }

    @Transactional
    public SolutionDto createSolution(UUID courseId, UUID postId, CreateSolutionRequest request, UUID userId) {
        CourseMember member = requireMember(courseId, userId);
        if (member.getRole() != CourseRole.STUDENT) {
            throw new ForbiddenException("Only students can submit solutions");
        }
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        if (post.getType() != PostType.TASK) {
            throw new BadRequestException("Can only submit solutions to task posts");
        }
        if (solutionRepository.existsByPostIdAndStudentId(postId, userId)) {
            throw new DuplicateResourceException("Solution already submitted");
        }
        Solution solution = Solution.builder()
                .post(post)
                .student(member.getUser())
                .text(request.getText())
                .status(SolutionStatus.SUBMITTED)
                .build();
        return toDto(solutionRepository.save(solution));
    }

    public SolutionDto getMySolution(UUID courseId, UUID postId, UUID userId) {
        requireMember(courseId, userId);
        postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        Solution solution = solutionRepository.findByPostIdAndStudentId(postId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Solution not found"));
        return toDto(solution);
    }

    public SolutionDto getSolution(UUID courseId, UUID postId, UUID solutionId, UUID userId) {
        CourseMember member = requireMember(courseId, userId);
        postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        Solution solution = solutionRepository.findById(solutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Solution not found"));
        if (member.getRole() != CourseRole.TEACHER
                && !solution.getStudent().getId().equals(userId)) {
            throw new ForbiddenException("Cannot view another student's solution");
        }
        return toDto(solution);
    }

    @Transactional
    public SolutionDto updateSolution(UUID courseId, UUID postId, UUID solutionId,
                                      CreateSolutionRequest request, UUID userId) {
        requireMember(courseId, userId);
        postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        Solution solution = solutionRepository.findById(solutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Solution not found"));
        if (!solution.getStudent().getId().equals(userId)) {
            throw new ForbiddenException("Cannot update another student's solution");
        }
        solution.setText(request.getText());
        return toDto(solutionRepository.save(solution));
    }

    @Transactional
    public void deleteSolution(UUID courseId, UUID postId, UUID solutionId, UUID userId) {
        requireMember(courseId, userId);
        postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        Solution solution = solutionRepository.findById(solutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Solution not found"));
        if (!solution.getStudent().getId().equals(userId)) {
            throw new ForbiddenException("Cannot delete another student's solution");
        }
        solutionRepository.delete(solution);
    }

    @Transactional
    public SolutionDto gradeSolution(UUID courseId, UUID postId, UUID solutionId,
                                     GradeRequest request, UUID userId) {
        CourseMember member = requireMember(courseId, userId);
        if (member.getRole() != CourseRole.TEACHER) {
            throw new ForbiddenException("Only teachers can grade solutions");
        }
        postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        Solution solution = solutionRepository.findById(solutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Solution not found"));
        solution.setGrade(request.getGrade());
        solution.setStatus(SolutionStatus.GRADED);
        solution.setGradedAt(Instant.now());
        return toDto(solutionRepository.save(solution));
    }

    @Transactional
    public SolutionDto removeGrade(UUID courseId, UUID postId, UUID solutionId, UUID userId) {
        CourseMember member = requireMember(courseId, userId);
        if (member.getRole() != CourseRole.TEACHER) {
            throw new ForbiddenException("Only teachers can remove grades");
        }
        postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        Solution solution = solutionRepository.findById(solutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Solution not found"));
        solution.setGrade(null);
        solution.setStatus(SolutionStatus.SUBMITTED);
        solution.setGradedAt(null);
        return toDto(solutionRepository.save(solution));
    }

    private CourseMember requireMember(UUID courseId, UUID userId) {
        return courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ForbiddenException("Not a member of this course"));
    }

    private SolutionDto toDto(Solution s) {
        return SolutionDto.builder()
                .id(s.getId())
                .text(s.getText())
                .status(s.getStatus())
                .grade(s.getGrade())
                .filesCount(s.getFiles() != null ? s.getFiles().size() : 0)
                .student(UserDto.from(s.getStudent()))
                .submittedAt(s.getSubmittedAt())
                .updatedAt(s.getUpdatedAt())
                .gradedAt(s.getGradedAt())
                .build();
    }
}
