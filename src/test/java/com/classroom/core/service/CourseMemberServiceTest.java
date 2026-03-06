package com.classroom.core.service;

import com.classroom.core.dto.member.MemberDto;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.Course;
import com.classroom.core.model.CourseMember;
import com.classroom.core.model.CourseRole;
import com.classroom.core.model.User;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseMemberServiceTest {

    @Mock
    private CourseMemberRepository courseMemberRepository;

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private CourseMemberService courseMemberService;

    private final UUID courseId = UUID.randomUUID();
    private final UUID currentUserId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();

    private Course buildCourse() {
        return Course.builder()
                .id(courseId)
                .name("Test Course")
                .createdAt(Instant.now())
                .build();
    }

    private User buildUser(UUID id, String username) {
        return User.builder()
                .id(id)
                .username(username)
                .passwordHash("hash")
                .createdAt(Instant.now())
                .build();
    }

    private CourseMember buildMember(Course course, UUID userId, CourseRole role) {
        return CourseMember.builder()
                .id(UUID.randomUUID())
                .course(course)
                .user(buildUser(userId, "user-" + userId.toString().substring(0, 4)))
                .role(role)
                .joinedAt(Instant.now())
                .build();
    }
    @Test
    void listMembers_returnsPaginatedMembers() {
        Course course = buildCourse();
        CourseMember currentMember = buildMember(course, currentUserId, CourseRole.TEACHER);
        CourseMember otherMember = buildMember(course, targetUserId, CourseRole.STUDENT);
        Pageable pageable = PageRequest.of(0, 20);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, currentUserId))
                .thenReturn(Optional.of(currentMember));
        when(courseMemberRepository.findByCourseId(courseId, pageable))
                .thenReturn(new PageImpl<>(List.of(currentMember, otherMember)));

        Page<MemberDto> result = courseMemberService.listMembers(courseId, null, pageable, currentUserId);

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void listMembers_filtersByRole() {
        Course course = buildCourse();
        CourseMember currentMember = buildMember(course, currentUserId, CourseRole.TEACHER);
        Pageable pageable = PageRequest.of(0, 20);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, currentUserId))
                .thenReturn(Optional.of(currentMember));
        when(courseMemberRepository.findByCourseIdAndRole(courseId, CourseRole.STUDENT, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        Page<MemberDto> result = courseMemberService.listMembers(courseId, CourseRole.STUDENT, pageable, currentUserId);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void listMembers_throwsForbiddenWhenNotMember() {
        Course course = buildCourse();
        Pageable pageable = PageRequest.of(0, 20);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, currentUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseMemberService.listMembers(courseId, null, pageable, currentUserId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void listMembers_throwsNotFoundWhenCourseDoesNotExist() {
        Pageable pageable = PageRequest.of(0, 20);
        when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseMemberService.listMembers(courseId, null, pageable, currentUserId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
    @Test
    void removeMember_removesStudentWhenCallerIsTeacher() {
        Course course = buildCourse();
        CourseMember teacherMember = buildMember(course, currentUserId, CourseRole.TEACHER);
        CourseMember studentMember = buildMember(course, targetUserId, CourseRole.STUDENT);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, currentUserId))
                .thenReturn(Optional.of(teacherMember));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, targetUserId))
                .thenReturn(Optional.of(studentMember));

        courseMemberService.removeMember(courseId, targetUserId, currentUserId);

        verify(courseMemberRepository).delete(studentMember);
    }

    @Test
    void removeMember_throwsForbiddenWhenCallerIsStudent() {
        Course course = buildCourse();
        CourseMember studentMember = buildMember(course, currentUserId, CourseRole.STUDENT);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, currentUserId))
                .thenReturn(Optional.of(studentMember));

        assertThatThrownBy(() -> courseMemberService.removeMember(courseId, targetUserId, currentUserId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void removeMember_throwsNotFoundWhenTargetNotMember() {
        Course course = buildCourse();
        CourseMember teacherMember = buildMember(course, currentUserId, CourseRole.TEACHER);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, currentUserId))
                .thenReturn(Optional.of(teacherMember));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, targetUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseMemberService.removeMember(courseId, targetUserId, currentUserId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
    @Test
    void leaveCourse_removesCurrentUserMembership() {
        Course course = buildCourse();
        CourseMember member = buildMember(course, currentUserId, CourseRole.STUDENT);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, currentUserId))
                .thenReturn(Optional.of(member));

        courseMemberService.leaveCourse(courseId, currentUserId);

        verify(courseMemberRepository).delete(member);
    }

    @Test
    void leaveCourse_throwsNotFoundWhenNotMember() {
        Course course = buildCourse();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, currentUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseMemberService.leaveCourse(courseId, currentUserId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
