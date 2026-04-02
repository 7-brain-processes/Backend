package com.classroom.core.service;

import com.classroom.core.dto.team.PostCaptainDto;
import com.classroom.core.dto.team.SelectCaptainsResultDto;
import com.classroom.core.exception.BadRequestException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.*;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.PostCaptainRepository;
import com.classroom.core.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostCaptainServiceTest {

    @Mock
    private PostCaptainRepository postCaptainRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private CourseMemberRepository courseMemberRepository;

    @InjectMocks
    private PostCaptainService postCaptainService;

    private UUID courseId;
    private UUID postId;
    private UUID userId;
    private UUID teacherId;
    private Post post;
    private User teacher;
    private User student1;
    private User student2;
    private CourseMember teacherMember;
    private CourseMember studentMember1;
    private CourseMember studentMember2;

    @BeforeEach
    void setUp() {
        courseId = UUID.randomUUID();
        postId = UUID.randomUUID();
        userId = UUID.randomUUID();
        teacherId = UUID.randomUUID();

        Course course = Course.builder().id(courseId).build();
        teacher = User.builder().id(teacherId).build();
        student1 = User.builder().id(UUID.randomUUID()).build();
        student2 = User.builder().id(UUID.randomUUID()).build();

        post = Post.builder()
                .id(postId)
                .course(course)
                .teamFormationMode(TeamFormationMode.RANDOM_CAPTAIN_SELECTION)
                .build();

        teacherMember = CourseMember.builder()
                .course(course)
                .user(teacher)
                .role(CourseRole.TEACHER)
                .build();

        studentMember1 = CourseMember.builder()
                .course(course)
                .user(student1)
                .role(CourseRole.STUDENT)
                .build();

        studentMember2 = CourseMember.builder()
                .course(course)
                .user(student2)
                .role(CourseRole.STUDENT)
                .build();
    }

    @Test
    void calculateCaptainCount_shouldReturn1For1To5Students() {
        assertThat(postCaptainService.calculateCaptainCount(1)).isEqualTo(1);
        assertThat(postCaptainService.calculateCaptainCount(5)).isEqualTo(1);
    }

    @Test
    void calculateCaptainCount_shouldReturn2For6To10Students() {
        assertThat(postCaptainService.calculateCaptainCount(6)).isEqualTo(2);
        assertThat(postCaptainService.calculateCaptainCount(10)).isEqualTo(2);
    }

    @Test
    void calculateCaptainCount_shouldReturn3For11To15Students() {
        assertThat(postCaptainService.calculateCaptainCount(11)).isEqualTo(3);
        assertThat(postCaptainService.calculateCaptainCount(15)).isEqualTo(3);
    }

    @Test
    void getCaptains_shouldReturnCaptains_whenUserIsMember() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(studentMember1));

        PostCaptain captain = PostCaptain.builder()
                .id(UUID.randomUUID())
                .post(post)
                .captain(student1)
                .build();

        when(postCaptainRepository.findByPostId(postId)).thenReturn(List.of(captain));

        List<PostCaptainDto> result = postCaptainService.getCaptains(courseId, postId, userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCaptainId()).isEqualTo(student1.getId());
    }

    @Test
    void getCaptains_shouldThrowForbidden_whenUserIsNotMember() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postCaptainService.getCaptains(courseId, postId, userId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void selectCaptains_shouldSelectCaptains_whenValidRequest() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, teacherId))
                .thenReturn(Optional.of(teacherMember));
        when(postCaptainRepository.findByPostId(postId)).thenReturn(List.of());
        when(courseMemberRepository.findByCourseIdAndRole(courseId, CourseRole.STUDENT))
                .thenReturn(List.of(studentMember1, studentMember2));

        SelectCaptainsResultDto result = postCaptainService.selectCaptains(courseId, postId, false, teacherId);

        assertThat(result.getCaptainCount()).isEqualTo(1); // 2 students / 5 = 0.4 -> max(1, 0) = 1
        assertThat(result.getCaptains()).hasSize(1);
        verify(postCaptainRepository).saveAll(any());
    }

    @Test
    void selectCaptains_shouldThrowBadRequest_whenModeIsNotRandomCaptainSelection() {
        Post wrongModePost = Post.builder()
                .id(postId)
                .course(post.getCourse())
                .teamFormationMode(TeamFormationMode.FREE)
                .build();

        when(postRepository.findById(postId)).thenReturn(Optional.of(wrongModePost));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, teacherId))
                .thenReturn(Optional.of(teacherMember));

        assertThatThrownBy(() -> postCaptainService.selectCaptains(courseId, postId, false, teacherId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Post team formation mode must be RANDOM_CAPTAIN_SELECTION");
    }

    @Test
    void selectCaptains_shouldThrowBadRequest_whenCaptainsAlreadySelectedAndNotReshuffle() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, teacherId))
                .thenReturn(Optional.of(teacherMember));
        when(postCaptainRepository.findByPostId(postId)).thenReturn(List.of(mock(PostCaptain.class)));

        assertThatThrownBy(() -> postCaptainService.selectCaptains(courseId, postId, false, teacherId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Captains already selected for this post");
    }

    @Test
    void selectCaptains_shouldDeleteExisting_whenReshuffleTrue() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, teacherId))
                .thenReturn(Optional.of(teacherMember));
    
        when(courseMemberRepository.findByCourseIdAndRole(courseId, CourseRole.STUDENT))
                .thenReturn(List.of(studentMember1, studentMember2));

        postCaptainService.selectCaptains(courseId, postId, true, teacherId);

        verify(postCaptainRepository).deleteByPostId(postId);
        verify(postCaptainRepository).saveAll(any());
    }

    @Test
    void selectCaptains_shouldThrowForbidden_whenUserIsNotTeacher() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(studentMember1));

        assertThatThrownBy(() -> postCaptainService.selectCaptains(courseId, postId, false, userId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Only teachers can perform this action");
    }
}