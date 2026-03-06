package com.classroom.core.service;

import com.classroom.core.dto.post.CreatePostRequest;
import com.classroom.core.dto.post.PostDto;
import com.classroom.core.dto.post.UpdatePostRequest;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.*;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
import com.classroom.core.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseMemberRepository courseMemberRepository;

    @InjectMocks
    private PostService postService;

    private final UUID courseId = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private Course buildCourse() {
        return Course.builder().id(courseId).name("Test").createdAt(Instant.now()).build();
    }

    private User buildUser() {
        return User.builder().id(userId).username("teacher").passwordHash("h").createdAt(Instant.now()).build();
    }

    private CourseMember buildMember(Course course, CourseRole role) {
        return CourseMember.builder()
                .id(UUID.randomUUID())
                .course(course)
                .user(buildUser())
                .role(role)
                .joinedAt(Instant.now())
                .build();
    }

    private Post buildPost(Course course) {
        return Post.builder()
                .id(postId)
                .course(course)
                .author(buildUser())
                .title("Week 1")
                .content("Read chapter 1")
                .type(PostType.MATERIAL)
                .files(new ArrayList<>())
                .comments(new ArrayList<>())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
    @Test
    void createPost_createsAndReturnsDtoWhenTeacher() {
        Course course = buildCourse();
        CourseMember teacher = buildMember(course, CourseRole.TEACHER);
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("Week 1");
        request.setContent("Read chapter 1");
        request.setType(PostType.MATERIAL);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(teacher));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            p.setId(postId);
            p.setCreatedAt(Instant.now());
            p.setUpdatedAt(Instant.now());
            return p;
        });

        PostDto result = postService.createPost(courseId, request, userId);

        assertThat(result.getTitle()).isEqualTo("Week 1");
        assertThat(result.getType()).isEqualTo(PostType.MATERIAL);
        verify(postRepository).save(any(Post.class));
    }

    @Test
    void createPost_throwsForbiddenWhenStudent() {
        Course course = buildCourse();
        CourseMember student = buildMember(course, CourseRole.STUDENT);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(student));

        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("My Post");
        request.setType(PostType.MATERIAL);

        assertThatThrownBy(() -> postService.createPost(courseId, request, userId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createPost_throwsNotFoundWhenCourseDoesNotExist() {
        when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("Post");
        request.setType(PostType.MATERIAL);

        assertThatThrownBy(() -> postService.createPost(courseId, request, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
    @Test
    void listPosts_returnsPaginatedPosts() {
        Course course = buildCourse();
        CourseMember member = buildMember(course, CourseRole.STUDENT);
        Post post = buildPost(course);
        Pageable pageable = PageRequest.of(0, 20);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(member));
        when(postRepository.findByCourseId(courseId, pageable))
                .thenReturn(new PageImpl<>(List.of(post)));

        Page<PostDto> result = postService.listPosts(courseId, null, pageable, userId);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Week 1");
    }

    @Test
    void listPosts_filtersByType() {
        Course course = buildCourse();
        CourseMember member = buildMember(course, CourseRole.STUDENT);
        Pageable pageable = PageRequest.of(0, 20);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(member));
        when(postRepository.findByCourseIdAndType(courseId, PostType.TASK, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        Page<PostDto> result = postService.listPosts(courseId, PostType.TASK, pageable, userId);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void listPosts_throwsForbiddenWhenNotMember() {
        Course course = buildCourse();
        Pageable pageable = PageRequest.of(0, 20);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.listPosts(courseId, null, pageable, userId))
                .isInstanceOf(ForbiddenException.class);
    }
    @Test
    void getPost_returnsPostForMember() {
        Course course = buildCourse();
        CourseMember member = buildMember(course, CourseRole.STUDENT);
        Post post = buildPost(course);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(member));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        PostDto result = postService.getPost(courseId, postId, userId);

        assertThat(result.getId()).isEqualTo(postId);
        assertThat(result.getTitle()).isEqualTo("Week 1");
    }

    @Test
    void getPost_throwsNotFoundWhenPostDoesNotExist() {
        Course course = buildCourse();
        CourseMember member = buildMember(course, CourseRole.STUDENT);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(member));
        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPost(courseId, postId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
    @Test
    void updatePost_updatesAndReturnsDtoWhenTeacher() {
        Course course = buildCourse();
        CourseMember teacher = buildMember(course, CourseRole.TEACHER);
        Post post = buildPost(course);
        UpdatePostRequest request = new UpdatePostRequest();
        request.setTitle("Updated Title");

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(teacher));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        PostDto result = postService.updatePost(courseId, postId, request, userId);

        assertThat(result.getTitle()).isEqualTo("Updated Title");
    }

    @Test
    void updatePost_throwsForbiddenWhenStudent() {
        Course course = buildCourse();
        CourseMember student = buildMember(course, CourseRole.STUDENT);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(student));

        UpdatePostRequest request = new UpdatePostRequest();
        request.setTitle("Hack");

        assertThatThrownBy(() -> postService.updatePost(courseId, postId, request, userId))
                .isInstanceOf(ForbiddenException.class);
    }
    @Test
    void deletePost_deletesWhenTeacher() {
        Course course = buildCourse();
        CourseMember teacher = buildMember(course, CourseRole.TEACHER);
        Post post = buildPost(course);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(teacher));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        postService.deletePost(courseId, postId, userId);

        verify(postRepository).delete(post);
    }

    @Test
    void deletePost_throwsForbiddenWhenStudent() {
        Course course = buildCourse();
        CourseMember student = buildMember(course, CourseRole.STUDENT);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(student));

        assertThatThrownBy(() -> postService.deletePost(courseId, postId, userId))
                .isInstanceOf(ForbiddenException.class);
    }
}
