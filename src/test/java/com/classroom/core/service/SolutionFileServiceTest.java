package com.classroom.core.service;

import com.classroom.core.dto.file.FileDto;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.*;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.SolutionFileRepository;
import com.classroom.core.repository.SolutionRepository;
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
class SolutionFileServiceTest {

    @Mock
    private SolutionFileRepository solutionFileRepository;

    @Mock
    private SolutionRepository solutionRepository;

    @Mock
    private CourseMemberRepository courseMemberRepository;

    @InjectMocks
    private SolutionFileService solutionFileService;

    private final UUID courseId = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();
    private final UUID solutionId = UUID.randomUUID();
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
                .title("Task").type(PostType.TASK)
                .files(new ArrayList<>()).comments(new ArrayList<>())
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private Solution buildSolution(Post post, User student) {
        return Solution.builder()
                .id(solutionId).post(post).student(student)
                .status(SolutionStatus.SUBMITTED)
                .files(new ArrayList<>()).comments(new ArrayList<>())
                .submittedAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private SolutionFile buildSolutionFile(Solution solution) {
        return SolutionFile.builder()
                .id(fileId).solution(solution).originalName("answer.pdf")
                .contentType("application/pdf").sizeBytes(2048)
                .storagePath("/uploads/answer.pdf").uploadedAt(Instant.now()).build();
    }
    @Test
    void listSolutionFiles_returnsFilesForOwner() {
        Course course = buildCourse();
        User student = buildUser();
        CourseMember member = buildMember(course, CourseRole.STUDENT);
        Post post = buildPost(course);
        Solution solution = buildSolution(post, student);
        SolutionFile file = buildSolutionFile(solution);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(member));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));
        when(solutionFileRepository.findBySolutionId(solutionId)).thenReturn(List.of(file));

        List<FileDto> result = solutionFileService.listSolutionFiles(courseId, postId, solutionId, userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOriginalName()).isEqualTo("answer.pdf");
    }

    @Test
    void listSolutionFiles_throwsForbiddenWhenNotMember() {
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> solutionFileService.listSolutionFiles(courseId, postId, solutionId, userId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void listSolutionFiles_throwsNotFoundWhenSolutionMissing() {
        Course course = buildCourse();
        CourseMember member = buildMember(course, CourseRole.STUDENT);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(member));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> solutionFileService.listSolutionFiles(courseId, postId, solutionId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
    @Test
    void deleteSolutionFile_deletesWhenOwner() {
        Course course = buildCourse();
        User student = buildUser();
        CourseMember member = buildMember(course, CourseRole.STUDENT);
        Post post = buildPost(course);
        Solution solution = buildSolution(post, student);
        SolutionFile file = buildSolutionFile(solution);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(member));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));
        when(solutionFileRepository.findById(fileId)).thenReturn(Optional.of(file));

        solutionFileService.deleteSolutionFile(courseId, postId, solutionId, fileId, userId);

        verify(solutionFileRepository).delete(file);
    }

    @Test
    void deleteSolutionFile_throwsNotFoundWhenFileMissing() {
        Course course = buildCourse();
        User student = buildUser();
        CourseMember member = buildMember(course, CourseRole.STUDENT);
        Post post = buildPost(course);
        Solution solution = buildSolution(post, student);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(member));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));
        when(solutionFileRepository.findById(fileId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> solutionFileService.deleteSolutionFile(courseId, postId, solutionId, fileId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
    @Test
    void downloadSolutionFile_throwsForbiddenWhenNotMember() {
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> solutionFileService.downloadSolutionFile(courseId, postId, solutionId, fileId, userId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void downloadSolutionFile_throwsNotFoundWhenFileMissing() {
        Course course = buildCourse();
        User student = buildUser();
        CourseMember member = buildMember(course, CourseRole.STUDENT);
        Post post = buildPost(course);
        Solution solution = buildSolution(post, student);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(member));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));
        when(solutionFileRepository.findById(fileId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> solutionFileService.downloadSolutionFile(courseId, postId, solutionId, fileId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
