package com.classroom.core.service;

import com.classroom.core.dto.comment.CommentDto;
import com.classroom.core.dto.comment.CreateCommentRequest;
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
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public CommentDto createPostComment(UUID courseId, UUID postId, CreateCommentRequest request, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public CommentDto updatePostComment(UUID courseId, UUID postId, UUID commentId,
                                        CreateCommentRequest request, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void deletePostComment(UUID courseId, UUID postId, UUID commentId, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Page<CommentDto> listSolutionComments(UUID courseId, UUID postId, UUID solutionId,
                                                  Pageable pageable, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public CommentDto createSolutionComment(UUID courseId, UUID postId, UUID solutionId,
                                            CreateCommentRequest request, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public CommentDto updateSolutionComment(UUID courseId, UUID postId, UUID solutionId, UUID commentId,
                                            CreateCommentRequest request, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void deleteSolutionComment(UUID courseId, UUID postId, UUID solutionId,
                                      UUID commentId, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
