package com.classroom.core.service;

import com.classroom.core.dto.course.CourseDto;
import com.classroom.core.dto.invite.CreateInviteRequest;
import com.classroom.core.dto.invite.InviteDto;
import com.classroom.core.exception.DuplicateResourceException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.*;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
import com.classroom.core.repository.InviteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InviteServiceTest {

    @Mock
    private InviteRepository inviteRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseMemberRepository courseMemberRepository;

    @InjectMocks
    private InviteService inviteService;

    private final UUID courseId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private Course buildCourse() {
        return Course.builder()
                .id(courseId)
                .name("Test Course")
                .createdAt(Instant.now())
                .build();
    }

    private CourseMember buildMember(Course course, UUID uid, CourseRole role) {
        return CourseMember.builder()
                .id(UUID.randomUUID())
                .course(course)
                .user(User.builder().id(uid).username("user").build())
                .role(role)
                .joinedAt(Instant.now())
                .build();
    }
    @Test
    void listInvites_returnsInvitesWhenTeacher() {
        Course course = buildCourse();
        CourseMember teacher = buildMember(course, userId, CourseRole.TEACHER);
        Invite invite = Invite.builder()
                .id(UUID.randomUUID())
                .code("ABC123")
                .course(course)
                .role(CourseRole.STUDENT)
                .currentUses(0)
                .createdAt(Instant.now())
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(teacher));
        when(inviteRepository.findByCourseId(courseId)).thenReturn(List.of(invite));

        List<InviteDto> result = inviteService.listInvites(courseId, userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("ABC123");
    }

    @Test
    void listInvites_throwsForbiddenWhenStudent() {
        Course course = buildCourse();
        CourseMember student = buildMember(course, userId, CourseRole.STUDENT);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(student));

        assertThatThrownBy(() -> inviteService.listInvites(courseId, userId))
                .isInstanceOf(ForbiddenException.class);
    }
    @Test
    void createInvite_createsInviteWithCode() {
        Course course = buildCourse();
        CourseMember teacher = buildMember(course, userId, CourseRole.TEACHER);
        CreateInviteRequest request = new CreateInviteRequest();
        request.setRole(CourseRole.STUDENT);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(teacher));
        when(inviteRepository.save(any(Invite.class))).thenAnswer(inv -> {
            Invite i = inv.getArgument(0);
            i.setId(UUID.randomUUID());
            i.setCreatedAt(Instant.now());
            return i;
        });

        InviteDto result = inviteService.createInvite(courseId, request, userId);

        assertThat(result.getCode()).isNotBlank();
        assertThat(result.getRole()).isEqualTo(CourseRole.STUDENT);
        verify(inviteRepository).save(any(Invite.class));
    }

    @Test
    void createInvite_throwsForbiddenWhenNotTeacher() {
        Course course = buildCourse();
        CourseMember student = buildMember(course, userId, CourseRole.STUDENT);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(student));

        CreateInviteRequest request = new CreateInviteRequest();
        request.setRole(CourseRole.STUDENT);

        assertThatThrownBy(() -> inviteService.createInvite(courseId, request, userId))
                .isInstanceOf(ForbiddenException.class);
    }
    @Test
    void revokeInvite_deletesInviteWhenTeacher() {
        Course course = buildCourse();
        CourseMember teacher = buildMember(course, userId, CourseRole.TEACHER);
        UUID inviteId = UUID.randomUUID();
        Invite invite = Invite.builder()
                .id(inviteId)
                .course(course)
                .code("ABC")
                .role(CourseRole.STUDENT)
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(teacher));
        when(inviteRepository.findById(inviteId)).thenReturn(Optional.of(invite));

        inviteService.revokeInvite(courseId, inviteId, userId);

        verify(inviteRepository).delete(invite);
    }

    @Test
    void revokeInvite_throwsNotFoundWhenInviteDoesNotExist() {
        Course course = buildCourse();
        CourseMember teacher = buildMember(course, userId, CourseRole.TEACHER);
        UUID inviteId = UUID.randomUUID();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(teacher));
        when(inviteRepository.findById(inviteId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inviteService.revokeInvite(courseId, inviteId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
    @Test
    void joinCourse_addsUserAndReturnsCourse() {
        Course course = buildCourse();
        Invite invite = Invite.builder()
                .id(UUID.randomUUID())
                .code("JOIN123")
                .course(course)
                .role(CourseRole.STUDENT)
                .currentUses(0)
                .maxUses(10)
                .build();

        when(inviteRepository.findByCode("JOIN123")).thenReturn(Optional.of(invite));
        when(courseMemberRepository.existsByCourseIdAndUserId(courseId, userId)).thenReturn(false);
        when(courseMemberRepository.save(any(CourseMember.class))).thenAnswer(inv -> inv.getArgument(0));
        when(courseMemberRepository.countByCourseIdAndRole(courseId, CourseRole.TEACHER)).thenReturn(1);
        when(courseMemberRepository.countByCourseIdAndRole(courseId, CourseRole.STUDENT)).thenReturn(1);

        CourseDto result = inviteService.joinCourse("JOIN123", userId);

        assertThat(result.getId()).isEqualTo(courseId);
        verify(courseMemberRepository).save(any(CourseMember.class));
    }

    @Test
    void joinCourse_throwsNotFoundWhenCodeInvalid() {
        when(inviteRepository.findByCode("INVALID")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inviteService.joinCourse("INVALID", userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void joinCourse_throwsConflictWhenAlreadyMember() {
        Course course = buildCourse();
        Invite invite = Invite.builder()
                .id(UUID.randomUUID())
                .code("JOIN123")
                .course(course)
                .role(CourseRole.STUDENT)
                .currentUses(0)
                .build();

        when(inviteRepository.findByCode("JOIN123")).thenReturn(Optional.of(invite));
        when(courseMemberRepository.existsByCourseIdAndUserId(courseId, userId)).thenReturn(true);

        assertThatThrownBy(() -> inviteService.joinCourse("JOIN123", userId))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void joinCourse_throwsNotFoundWhenInviteExpired() {
        Course course = buildCourse();
        Invite invite = Invite.builder()
                .id(UUID.randomUUID())
                .code("EXPIRED")
                .course(course)
                .role(CourseRole.STUDENT)
                .expiresAt(Instant.now().minusSeconds(3600))
                .currentUses(0)
                .build();

        when(inviteRepository.findByCode("EXPIRED")).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> inviteService.joinCourse("EXPIRED", userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void joinCourse_throwsNotFoundWhenMaxUsesReached() {
        Course course = buildCourse();
        Invite invite = Invite.builder()
                .id(UUID.randomUUID())
                .code("MAXED")
                .course(course)
                .role(CourseRole.STUDENT)
                .maxUses(5)
                .currentUses(5)
                .build();

        when(inviteRepository.findByCode("MAXED")).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> inviteService.joinCourse("MAXED", userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void joinCourse_incrementsCurrentUses() {
        Course course = buildCourse();
        Invite invite = Invite.builder()
                .id(UUID.randomUUID())
                .code("JOIN123")
                .course(course)
                .role(CourseRole.STUDENT)
                .currentUses(2)
                .maxUses(10)
                .build();

        when(inviteRepository.findByCode("JOIN123")).thenReturn(Optional.of(invite));
        when(courseMemberRepository.existsByCourseIdAndUserId(courseId, userId)).thenReturn(false);
        when(courseMemberRepository.save(any(CourseMember.class))).thenAnswer(inv -> inv.getArgument(0));
        when(courseMemberRepository.countByCourseIdAndRole(any(), any())).thenReturn(0);

        inviteService.joinCourse("JOIN123", userId);

        assertThat(invite.getCurrentUses()).isEqualTo(3);
        verify(inviteRepository).save(invite);
    }
}
