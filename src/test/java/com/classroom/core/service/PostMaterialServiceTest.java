package com.classroom.core.service;

import com.classroom.core.dto.file.FileDto;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.*;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.PostFileRepository;
import com.classroom.core.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostMaterialServiceTest {

    @Mock
    private PostFileRepository postFileRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private CourseMemberRepository courseMemberRepository;

    @InjectMocks
    private PostMaterialService postMaterialService;

    private final UUID courseId = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();
    private final UUID fileId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private Course buildCourse() {
        return Course.builder().id(courseId).name("Test").createdAt(Instant.now()).build();
    }

    private User buildUser() {
        return User.builder().id(userId).username("user").passwordHash("h").createdAt(Instant.now()).build();
    }

    private CourseMember buildMember(Course course, CourseRole role) {
        return CourseMember.builder()
                .id(UUID.randomUUID()).course(course).user(buildUser())
                .role(role).joinedAt(Instant.now()).build();
    }

    private Post buildPost(Course course) {
        return Post.builder()
                .id(postId).course(course).author(buildUser())
                .title("Post").type(PostType.MATERIAL)
                .files(new ArrayList<>()).comments(new ArrayList<>())
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private PostFile buildPostFile(Post post) {
        return PostFile.builder()
                .id(fileId).post(post).originalName("lecture.pdf")
                .contentType("application/pdf").sizeBytes(1024)
                .storagePath("/uploads/lecture.pdf").uploadedAt(Instant.now()).build();
    }
    @Test
    void listPostMaterials_returnsFilesForMember() {
        Course course = buildCourse();
        CourseMember member = buildMember(course, CourseRole.STUDENT);
        Post post = buildPost(course);
        PostFile file = buildPostFile(post);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(member));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postFileRepository.findByPostId(postId)).thenReturn(List.of(file));

        List<FileDto> result = postMaterialService.listPostMaterials(courseId, postId, userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOriginalName()).isEqualTo("lecture.pdf");
    }

    @Test
    void listPostMaterials_throwsForbiddenWhenNotMember() {
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postMaterialService.listPostMaterials(courseId, postId, userId))
                .isInstanceOf(ForbiddenException.class);
    }
    @Test
    void deletePostMaterial_deletesWhenTeacher() {
        Course course = buildCourse();
        CourseMember teacher = buildMember(course, CourseRole.TEACHER);
        Post post = buildPost(course);
        PostFile file = buildPostFile(post);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(teacher));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postFileRepository.findById(fileId)).thenReturn(Optional.of(file));

        postMaterialService.deletePostMaterial(courseId, postId, fileId, userId);

        verify(postFileRepository).delete(file);
    }

    @Test
    void deletePostMaterial_throwsForbiddenWhenStudent() {
        Course course = buildCourse();
        CourseMember student = buildMember(course, CourseRole.STUDENT);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(student));

        assertThatThrownBy(() -> postMaterialService.deletePostMaterial(courseId, postId, fileId, userId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deletePostMaterial_throwsNotFoundWhenFileDoesNotExist() {
        Course course = buildCourse();
        CourseMember teacher = buildMember(course, CourseRole.TEACHER);
        Post post = buildPost(course);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(teacher));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postFileRepository.findById(fileId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postMaterialService.deletePostMaterial(courseId, postId, fileId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
    @Test
    void downloadPostMaterial_throwsForbiddenWhenNotMember() {
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postMaterialService.downloadPostMaterial(courseId, postId, fileId, userId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void downloadPostMaterial_throwsNotFoundWhenFileDoesNotExist() {
        Course course = buildCourse();
        CourseMember member = buildMember(course, CourseRole.STUDENT);
        Post post = buildPost(course);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(member));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postFileRepository.findById(fileId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postMaterialService.downloadPostMaterial(courseId, postId, fileId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
