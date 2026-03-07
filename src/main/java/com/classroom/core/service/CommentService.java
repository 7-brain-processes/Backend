package com.classroom.core.service;

import com.classroom.core.dto.auth.UserDto;
import com.classroom.core.dto.comment.CommentDto;
import com.classroom.core.dto.comment.CreateCommentRequest;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.Comment;
import com.classroom.core.model.CourseMember;
import com.classroom.core.model.CourseRole;
import com.classroom.core.model.Solution;
import com.classroom.core.repository.CommentRepository;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.PostRepository;
import com.classroom.core.repository.SolutionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final SolutionRepository solutionRepository;
    private final CourseMemberRepository courseMemberRepository;

    public Page<CommentDto> listPostComments(UUID courseId, UUID postId, Pageable pageable, UUID userId) {
        requireMember(courseId, userId);
        postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        return commentRepository.findByPostId(postId, pageable).map(this::toDto);
    }

    public CommentDto createPostComment(UUID courseId, UUID postId, CreateCommentRequest request, UUID userId) {
        CourseMember member = requireMember(courseId, userId);
        var post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        Comment comment = Comment.builder()
                .post(post)
                .author(member.getUser())
                .text(request.getText())
                .build();
        return toDto(commentRepository.save(comment));
    }

    public CommentDto updatePostComment(UUID courseId, UUID postId, UUID commentId,
                                        CreateCommentRequest request, UUID userId) {
        requireMember(courseId, userId);
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("Cannot update another user's comment");
        }
        comment.setText(request.getText());
        return toDto(commentRepository.save(comment));
    }

    public void deletePostComment(UUID courseId, UUID postId, UUID commentId, UUID userId) {
        CourseMember member = requireMember(courseId, userId);
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        if (member.getRole() != CourseRole.TEACHER && !comment.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("Cannot delete another user's comment");
        }
        commentRepository.delete(comment);
    }

    public Page<CommentDto> listSolutionComments(UUID courseId, UUID postId, UUID solutionId,
                                                  Pageable pageable, UUID userId) {
        requireMember(courseId, userId);
        solutionRepository.findById(solutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Solution not found"));
        return commentRepository.findBySolutionId(solutionId, pageable).map(this::toDto);
    }

    public CommentDto createSolutionComment(UUID courseId, UUID postId, UUID solutionId,
                                            CreateCommentRequest request, UUID userId) {
        CourseMember member = requireMember(courseId, userId);
        if (member.getRole() != CourseRole.TEACHER) {
            throw new ForbiddenException("Only teachers can comment on solutions");
        }
        Solution solution = solutionRepository.findById(solutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Solution not found"));
        Comment comment = Comment.builder()
                .solution(solution)
                .author(member.getUser())
                .text(request.getText())
                .build();
        return toDto(commentRepository.save(comment));
    }

    public CommentDto updateSolutionComment(UUID courseId, UUID postId, UUID solutionId, UUID commentId,
                                            CreateCommentRequest request, UUID userId) {
        requireMember(courseId, userId);
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("Cannot update another user's comment");
        }
        comment.setText(request.getText());
        return toDto(commentRepository.save(comment));
    }

    public void deleteSolutionComment(UUID courseId, UUID postId, UUID solutionId,
                                      UUID commentId, UUID userId) {
        CourseMember member = requireMember(courseId, userId);
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        if (member.getRole() != CourseRole.TEACHER && !comment.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("Cannot delete another user's comment");
        }
        commentRepository.delete(comment);
    }

    private CourseMember requireMember(UUID courseId, UUID userId) {
        return courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ForbiddenException("Not a member of this course"));
    }

    private CommentDto toDto(Comment comment) {
        return CommentDto.builder()
                .id(comment.getId())
                .text(comment.getText())
                .author(UserDto.from(comment.getAuthor()))
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
