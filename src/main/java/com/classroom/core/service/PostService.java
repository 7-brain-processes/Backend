package com.classroom.core.service;

import com.classroom.core.dto.post.CreatePostRequest;
import com.classroom.core.dto.post.PostDto;
import com.classroom.core.dto.post.UpdatePostRequest;
import com.classroom.core.model.PostType;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
import com.classroom.core.repository.PostRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final CourseRepository courseRepository;
    private final CourseMemberRepository courseMemberRepository;

    public Page<PostDto> listPosts(UUID courseId, PostType type, Pageable pageable, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public PostDto createPost(UUID courseId, CreatePostRequest request, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public PostDto getPost(UUID courseId, UUID postId, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public PostDto updatePost(UUID courseId, UUID postId, UpdatePostRequest request, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void deletePost(UUID courseId, UUID postId, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
