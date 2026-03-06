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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseMemberRepository courseMemberRepository;

    public CourseDto createCourse(CreateCourseRequest request, UUID userId) {

        Course course = Course.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();

        course = courseRepository.save(course);

        CourseMember member = CourseMember.builder()
                .course(course)
                .user(User.builder().id(userId).build())
                .role(CourseRole.TEACHER)
                .build();

        courseMemberRepository.save(member);

        int teacherCount = courseMemberRepository.countByCourseIdAndRole(course.getId(), CourseRole.TEACHER);
        int studentCount = courseMemberRepository.countByCourseIdAndRole(course.getId(), CourseRole.STUDENT);

        return toDto(course, CourseRole.TEACHER, teacherCount, studentCount);
    }

    public CourseDto getCourse(UUID courseId, UUID userId) {

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

        CourseMember member = courseMemberRepository
                .findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this course"));

        int teacherCount = courseMemberRepository.countByCourseIdAndRole(courseId, CourseRole.TEACHER);
        int studentCount = courseMemberRepository.countByCourseIdAndRole(courseId, CourseRole.STUDENT);

        return toDto(course, member.getRole(), teacherCount, studentCount);
    }

    @Transactional(readOnly = true)
    public Page<CourseDto> listMyCourses(UUID userId, CourseRole role, Pageable pageable) {

        Page<CourseMember> page;

        if (role == null) {
            page = courseMemberRepository.findByUserId(userId, pageable);
        } else {
            page = courseMemberRepository.findByUserIdAndRole(userId, role, pageable);
        }

        return page.map(member -> {

            Course course = member.getCourse();

            int teacherCount = courseMemberRepository
                    .countByCourseIdAndRole(course.getId(), CourseRole.TEACHER);

            int studentCount = courseMemberRepository
                    .countByCourseIdAndRole(course.getId(), CourseRole.STUDENT);

            return toDto(course, member.getRole(), teacherCount, studentCount);
        });
    }

    public CourseDto updateCourse(UUID courseId, UpdateCourseRequest request, UUID userId) {

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

        CourseMember member = courseMemberRepository
                .findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this course"));

        if (member.getRole() != CourseRole.TEACHER) {
            throw new ForbiddenException("Only teachers can update the course");
        }

        if (request.getName() != null) {
            course.setName(request.getName());
        }

        if (request.getDescription() != null) {
            course.setDescription(request.getDescription());
        }

        course = courseRepository.save(course);

        int teacherCount = courseMemberRepository.countByCourseIdAndRole(courseId, CourseRole.TEACHER);
        int studentCount = courseMemberRepository.countByCourseIdAndRole(courseId, CourseRole.STUDENT);

        return toDto(course, member.getRole(), teacherCount, studentCount);
    }

    public void deleteCourse(UUID courseId, UUID userId) {

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

        CourseMember member = courseMemberRepository
                .findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this course"));

        if (member.getRole() != CourseRole.TEACHER) {
            throw new ForbiddenException("Only teachers can delete the course");
        }


        courseRepository.delete(course);
    }

    private CourseDto toDto(Course course, CourseRole role, int teacherCount, int studentCount) {

        return CourseDto.builder()
                .id(course.getId())
                .name(course.getName())
                .description(course.getDescription())
                .createdAt(course.getCreatedAt())
                .currentUserRole(role)
                .teacherCount(teacherCount)
                .studentCount(studentCount)
                .build();
    }
}