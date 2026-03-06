package com.classroom.core.service;

import com.classroom.core.dto.comment.CommentDto;
import com.classroom.core.dto.comment.CreateCommentRequest;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.*;
import com.classroom.core.repository.CommentRepository;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.PostRepository;
import com.classroom.core.repository.SolutionRepository;
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
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private SolutionRepository solutionRepository;

    @Mock
    private CourseMemberRepository courseMemberRepository;

    @InjectMocks
    private CommentService commentService;

    private final UUID courseId = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();
    private final UUID solutionId = UUID.randomUUID();
    private final UUID commentId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    private Course buildCourse() {
        return Course.builder().id(courseId).name("Test").createdAt(Instant.now()).build();
    }

    private User buildUser(UUID id) {
        return User.builder().id(id).username("user-" + id.toString().substring(0, 4))
                .passwordHash("h").createdAt(Instant.now()).build();
    }

    private CourseMember buildMember(Course course, UUID uid, CourseRole role) {
        return CourseMember.builder()
                .id(UUID.randomUUID()).course(course).user(buildUser(uid))
                .role(role).joinedAt(Instant.now()).build();
    }

    private Post buildPost(Course course) {
        return Post.builder()
                .id(postId).course(course).author(buildUser(userId))
                .title("Post").type(PostType.MATERIAL)
                .files(new ArrayList<>()).comments(new ArrayList<>())
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private Comment buildComment(UUID authorId) {
        Post post = buildPost(buildCourse());
        return Comment.builder()
                .id(commentId).post(post).author(buildUser(authorId))
                .text("A comment").createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }
    @Test
    void listPostComments_returnsPaginatedComments() {
        Course course = buildCourse();
        CourseMember member = buildMember(course, userId, CourseRole.STUDENT);
        Post post = buildPost(course);
        Comment comment = buildComment(userId);
        Pageable pageable = PageRequest.of(0, 20);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(member));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(commentRepository.findByPostId(postId, pageable))
                .thenReturn(new PageImpl<>(List.of(comment)));

        Page<CommentDto> result = commentService.listPostComments(courseId, postId, pageable, userId);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getText()).isEqualTo("A comment");
    }

    @Test
    void listPostComments_throwsForbiddenWhenNotMember() {
        Pageable pageable = PageRequest.of(0, 20);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.listPostComments(courseId, postId, pageable, userId))
                .isInstanceOf(ForbiddenException.class);
    }
    @Test
    void createPostComment_createsCommentForMember() {
        Course course = buildCourse();
        CourseMember member = buildMember(course, userId, CourseRole.STUDENT);
        Post post = buildPost(course);
        CreateCommentRequest request = new CreateCommentRequest();
        request.setText("Great lecture!");

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(member));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            c.setId(commentId);
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(Instant.now());
            return c;
        });

        CommentDto result = commentService.createPostComment(courseId, postId, request, userId);

        assertThat(result.getText()).isEqualTo("Great lecture!");
        verify(commentRepository).save(any(Comment.class));
    }

    @Test
    void createPostComment_throwsNotFoundWhenPostDoesNotExist() {
        Course course = buildCourse();
        CourseMember member = buildMember(course, userId, CourseRole.STUDENT);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(member));
        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        CreateCommentRequest request = new CreateCommentRequest();
        request.setText("Comment");

        assertThatThrownBy(() -> commentService.createPostComment(courseId, postId, request, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
    @Test
    void updatePostComment_updatesOwnComment() {
        Course course = buildCourse();
        CourseMember member = buildMember(course, userId, CourseRole.STUDENT);
        Comment comment = buildComment(userId);
        CreateCommentRequest request = new CreateCommentRequest();
        request.setText("Updated text");

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(member));
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        CommentDto result = commentService.updatePostComment(courseId, postId, commentId, request, userId);

        assertThat(result.getText()).isEqualTo("Updated text");
    }

    @Test
    void updatePostComment_throwsForbiddenForOthersComment() {
        Course course = buildCourse();
        CourseMember member = buildMember(course, userId, CourseRole.STUDENT);
        Comment comment = buildComment(otherUserId);
        CreateCommentRequest request = new CreateCommentRequest();
        request.setText("Hacked");

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(member));
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.updatePostComment(courseId, postId, commentId, request, userId))
                .isInstanceOf(ForbiddenException.class);
    }
    @Test
    void deletePostComment_deletesOwnComment() {
        Course course = buildCourse();
        CourseMember member = buildMember(course, userId, CourseRole.STUDENT);
        Comment comment = buildComment(userId);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(member));
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        commentService.deletePostComment(courseId, postId, commentId, userId);

        verify(commentRepository).delete(comment);
    }

    @Test
    void deletePostComment_teacherCanDeleteAnyComment() {
        Course course = buildCourse();
        CourseMember teacher = buildMember(course, userId, CourseRole.TEACHER);
        Comment comment = buildComment(otherUserId);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(teacher));
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        commentService.deletePostComment(courseId, postId, commentId, userId);

        verify(commentRepository).delete(comment);
    }

    @Test
    void deletePostComment_throwsForbiddenForOthersComment() {
        Course course = buildCourse();
        CourseMember student = buildMember(course, userId, CourseRole.STUDENT);
        Comment comment = buildComment(otherUserId);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(student));
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.deletePostComment(courseId, postId, commentId, userId))
                .isInstanceOf(ForbiddenException.class);
    }
    @Test
    void listSolutionComments_returnsPaginatedComments() {
        Course course = buildCourse();
        CourseMember member = buildMember(course, userId, CourseRole.TEACHER);
        Pageable pageable = PageRequest.of(0, 20);

        Solution solution = Solution.builder()
                .id(solutionId).post(buildPost(course)).student(buildUser(otherUserId))
                .status(SolutionStatus.SUBMITTED)
                .files(new ArrayList<>()).comments(new ArrayList<>())
                .submittedAt(Instant.now()).updatedAt(Instant.now()).build();

        Comment comment = Comment.builder()
                .id(commentId).solution(solution).author(buildUser(userId))
                .text("Fix this").createdAt(Instant.now()).updatedAt(Instant.now()).build();

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(member));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));
        when(commentRepository.findBySolutionId(solutionId, pageable))
                .thenReturn(new PageImpl<>(List.of(comment)));

        Page<CommentDto> result = commentService.listSolutionComments(
                courseId, postId, solutionId, pageable, userId);

        assertThat(result.getContent()).hasSize(1);
    }
    @Test
    void createSolutionComment_createsWhenTeacher() {
        Course course = buildCourse();
        CourseMember teacher = buildMember(course, userId, CourseRole.TEACHER);
        Solution solution = Solution.builder()
                .id(solutionId).post(buildPost(course)).student(buildUser(otherUserId))
                .status(SolutionStatus.SUBMITTED)
                .files(new ArrayList<>()).comments(new ArrayList<>())
                .submittedAt(Instant.now()).updatedAt(Instant.now()).build();

        CreateCommentRequest request = new CreateCommentRequest();
        request.setText("Needs improvement");

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(teacher));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(Instant.now());
            return c;
        });

        CommentDto result = commentService.createSolutionComment(
                courseId, postId, solutionId, request, userId);

        assertThat(result.getText()).isEqualTo("Needs improvement");
    }

    @Test
    void createSolutionComment_throwsForbiddenWhenStudent() {
        Course course = buildCourse();
        CourseMember student = buildMember(course, userId, CourseRole.STUDENT);

        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(student));

        CreateCommentRequest request = new CreateCommentRequest();
        request.setText("Comment");

        assertThatThrownBy(() -> commentService.createSolutionComment(
                courseId, postId, solutionId, request, userId))
                .isInstanceOf(ForbiddenException.class);
    }
}
