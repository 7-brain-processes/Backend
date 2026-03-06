package com.classroom.core.service;

import com.classroom.core.dto.course.CourseDto;
import com.classroom.core.dto.course.CreateCourseRequest;
import com.classroom.core.dto.course.UpdateCourseRequest;
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
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseMemberRepository courseMemberRepository;

    @InjectMocks
    private CourseService courseService;

    private final UUID userId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();

    private Course buildCourse() {
        return Course.builder()
                .id(courseId)
                .name("Data Structures")
                .description("Spring 2026")
                .createdAt(Instant.now())
                .build();
    }

    private CourseMember buildMember(Course course, CourseRole role) {
        return CourseMember.builder()
                .id(UUID.randomUUID())
                .course(course)
                .user(User.builder().id(userId).username("johndoe").build())
                .role(role)
                .joinedAt(Instant.now())
                .build();
    }
    @Test
    void createCourse_savesAndReturnsDto() {
        CreateCourseRequest request = new CreateCourseRequest();
        request.setName("Data Structures");
        request.setDescription("Spring 2026");

        Course saved = buildCourse();
        when(courseRepository.save(any(Course.class))).thenReturn(saved);
        when(courseMemberRepository.save(any(CourseMember.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(courseMemberRepository.countByCourseIdAndRole(courseId, CourseRole.TEACHER)).thenReturn(1);
        when(courseMemberRepository.countByCourseIdAndRole(courseId, CourseRole.STUDENT)).thenReturn(0);

        CourseDto result = courseService.createCourse(request, userId);

        assertThat(result.getName()).isEqualTo("Data Structures");
        assertThat(result.getDescription()).isEqualTo("Spring 2026");
        assertThat(result.getId()).isEqualTo(courseId);
        verify(courseRepository).save(any(Course.class));
    }

    @Test
    void createCourse_addsCallerAsTeacher() {
        CreateCourseRequest request = new CreateCourseRequest();
        request.setName("Algorithms");

        Course saved = buildCourse();
        when(courseRepository.save(any(Course.class))).thenReturn(saved);
        when(courseMemberRepository.save(any(CourseMember.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(courseMemberRepository.countByCourseIdAndRole(any(), any())).thenReturn(0);

        courseService.createCourse(request, userId);

        var captor = org.mockito.ArgumentCaptor.forClass(CourseMember.class);
        verify(courseMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(CourseRole.TEACHER);
    }

    @Test
    void createCourse_returnsCurrentUserRoleAsTeacher() {
        CreateCourseRequest request = new CreateCourseRequest();
        request.setName("Algorithms");

        Course saved = buildCourse();
        when(courseRepository.save(any(Course.class))).thenReturn(saved);
        when(courseMemberRepository.save(any(CourseMember.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(courseMemberRepository.countByCourseIdAndRole(courseId, CourseRole.TEACHER)).thenReturn(1);
        when(courseMemberRepository.countByCourseIdAndRole(courseId, CourseRole.STUDENT)).thenReturn(0);

        CourseDto result = courseService.createCourse(request, userId);

        assertThat(result.getCurrentUserRole()).isEqualTo(CourseRole.TEACHER);
    }
    @Test
    void getCourse_returnsCourseWhenUserIsMember() {
        Course course = buildCourse();
        CourseMember member = buildMember(course, CourseRole.STUDENT);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(member));
        when(courseMemberRepository.countByCourseIdAndRole(courseId, CourseRole.TEACHER)).thenReturn(1);
        when(courseMemberRepository.countByCourseIdAndRole(courseId, CourseRole.STUDENT)).thenReturn(5);

        CourseDto result = courseService.getCourse(courseId, userId);

        assertThat(result.getId()).isEqualTo(courseId);
        assertThat(result.getName()).isEqualTo("Data Structures");
        assertThat(result.getCurrentUserRole()).isEqualTo(CourseRole.STUDENT);
        assertThat(result.getTeacherCount()).isEqualTo(1);
        assertThat(result.getStudentCount()).isEqualTo(5);
    }

    @Test
    void getCourse_throwsNotFoundWhenCourseDoesNotExist() {
        when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.getCourse(courseId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCourse_throwsForbiddenWhenUserIsNotMember() {
        Course course = buildCourse();
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.getCourse(courseId, userId))
                .isInstanceOf(ForbiddenException.class);
    }
    @Test
    void listMyCourses_returnsPaginatedCourses() {
        Course course = buildCourse();
        CourseMember member = buildMember(course, CourseRole.TEACHER);
        Pageable pageable = PageRequest.of(0, 20);

        when(courseMemberRepository.findByUserId(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(member)));
        when(courseMemberRepository.countByCourseIdAndRole(courseId, CourseRole.TEACHER)).thenReturn(1);
        when(courseMemberRepository.countByCourseIdAndRole(courseId, CourseRole.STUDENT)).thenReturn(0);

        Page<CourseDto> result = courseService.listMyCourses(userId, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Data Structures");
    }

    @Test
    void listMyCourses_filtersbyRole() {
        Course course = buildCourse();
        CourseMember member = buildMember(course, CourseRole.TEACHER);
        Pageable pageable = PageRequest.of(0, 20);

        when(courseMemberRepository.findByUserIdAndRole(userId, CourseRole.TEACHER, pageable))
                .thenReturn(new PageImpl<>(List.of(member)));
        when(courseMemberRepository.countByCourseIdAndRole(courseId, CourseRole.TEACHER)).thenReturn(1);
        when(courseMemberRepository.countByCourseIdAndRole(courseId, CourseRole.STUDENT)).thenReturn(0);

        Page<CourseDto> result = courseService.listMyCourses(userId, CourseRole.TEACHER, pageable);

        assertThat(result.getContent()).hasSize(1);
    }
    @Test
    void updateCourse_updatesNameAndDescription() {
        Course course = buildCourse();
        CourseMember member = buildMember(course, CourseRole.TEACHER);
        UpdateCourseRequest request = new UpdateCourseRequest();
        request.setName("Updated Name");
        request.setDescription("Updated Desc");

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(member));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));
        when(courseMemberRepository.countByCourseIdAndRole(courseId, CourseRole.TEACHER)).thenReturn(1);
        when(courseMemberRepository.countByCourseIdAndRole(courseId, CourseRole.STUDENT)).thenReturn(0);

        CourseDto result = courseService.updateCourse(courseId, request, userId);

        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getDescription()).isEqualTo("Updated Desc");
    }

    @Test
    void updateCourse_throwsForbiddenWhenNotTeacher() {
        Course course = buildCourse();
        CourseMember member = buildMember(course, CourseRole.STUDENT);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(member));

        UpdateCourseRequest request = new UpdateCourseRequest();
        request.setName("Updated");

        assertThatThrownBy(() -> courseService.updateCourse(courseId, request, userId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateCourse_throwsNotFoundWhenCourseDoesNotExist() {
        when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

        UpdateCourseRequest request = new UpdateCourseRequest();
        request.setName("Updated");

        assertThatThrownBy(() -> courseService.updateCourse(courseId, request, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
    @Test
    void deleteCourse_deletesWhenCallerIsTeacher() {
        Course course = buildCourse();
        CourseMember member = buildMember(course, CourseRole.TEACHER);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(member));

        courseService.deleteCourse(courseId, userId);

        verify(courseRepository).delete(course);
    }

    @Test
    void deleteCourse_throwsForbiddenWhenNotTeacher() {
        Course course = buildCourse();
        CourseMember member = buildMember(course, CourseRole.STUDENT);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> courseService.deleteCourse(courseId, userId))
                .isInstanceOf(ForbiddenException.class);

        verify(courseRepository, never()).delete(any());
    }

    @Test
    void deleteCourse_throwsNotFoundWhenCourseDoesNotExist() {
        when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.deleteCourse(courseId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
