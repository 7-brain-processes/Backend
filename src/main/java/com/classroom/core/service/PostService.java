package com.classroom.core.service;

import com.classroom.core.dto.auth.UserDto;
import com.classroom.core.dto.post.CreatePostRequest;
import com.classroom.core.dto.post.PostDto;
import com.classroom.core.dto.post.UpdatePostRequest;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.Course;
import com.classroom.core.model.CourseMember;
import com.classroom.core.model.CourseRole;
import com.classroom.core.model.Post;
import com.classroom.core.model.PostType;
import com.classroom.core.model.User;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
import com.classroom.core.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final CourseRepository courseRepository;
    private final CourseMemberRepository courseMemberRepository;

    public Page<PostDto> listPosts(UUID courseId, PostType type, Pageable pageable, UUID userId) {
        getCourseOrThrow(courseId);
        ensureMember(courseId, userId);

        Page<Post> posts = (type == null)
                ? postRepository.findByCourseId(courseId, pageable)
                : postRepository.findByCourseIdAndType(courseId, type, pageable);

        return posts.map(this::toDto);
    }

    @Transactional
    public PostDto createPost(UUID courseId, CreatePostRequest request, UUID userId) {
        Course course = getCourseOrThrow(courseId);
        ensureTeacher(courseId, userId);

        Post post = Post.builder()
                .course(course)
                .author(User.builder().id(userId).build())
                .title(request.getTitle())
                .content(request.getContent())
                .type(request.getType())
                .deadline(request.getDeadline())
                .build();

        Post saved = postRepository.save(post);
        return toDto(saved);
    }

    public PostDto getPost(UUID courseId, UUID postId, UUID userId) {
        Course course = getCourseOrThrow(courseId);
        ensureMember(courseId, userId);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (!post.getCourse().getId().equals(course.getId())) {
            throw new ResourceNotFoundException("Post not found");
        }

        return toDto(post);
    }

    @Transactional
    public PostDto updatePost(UUID courseId, UUID postId, UpdatePostRequest request, UUID userId) {
        Course course = getCourseOrThrow(courseId);
        ensureTeacher(courseId, userId);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (!post.getCourse().getId().equals(course.getId())) {
            throw new ResourceNotFoundException("Post not found");
        }

        if (request.getTitle() != null) {
            post.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            post.setContent(request.getContent());
        }
        if (request.getDeadline() != null) {
            post.setDeadline(request.getDeadline());
        }

        Post saved = postRepository.save(post);
        return toDto(saved);
    }

    @Transactional
    public void deletePost(UUID courseId, UUID postId, UUID userId) {
        Course course = getCourseOrThrow(courseId);
        ensureTeacher(courseId, userId);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (!post.getCourse().getId().equals(course.getId())) {
            throw new ResourceNotFoundException("Post not found");
        }

        postRepository.delete(post);
        postRepository.flush();
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
        CourseMember member = courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this course"));

        if (member.getRole() != CourseRole.TEACHER) {
            throw new ForbiddenException("Only teachers can manage posts");
        }

        return member;
    }

    private PostDto toDto(Post post) {
        return PostDto.builder()
                .id(post.getId())
                .title(post.getTitle())
                .content(post.getContent())
                .type(post.getType())
                .deadline(post.getDeadline())
                .author(post.getAuthor() == null ? null : UserDto.from(post.getAuthor()))
                .materialsCount(post.getFiles() == null ? 0 : post.getFiles().size())
                .commentsCount(post.getComments() == null ? 0 : post.getComments().size())
                .solutionsCount(null)
                .mySolutionId(null)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}
