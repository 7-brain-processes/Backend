package com.classroom.core.service;

import com.classroom.core.dto.course.CourseDto;
import com.classroom.core.dto.course.CreateCourseRequest;
import com.classroom.core.dto.course.UpdateCourseRequest;
import com.classroom.core.model.CourseRole;
import com.classroom.core.repository.CourseRepository;
import com.classroom.core.repository.CourseMemberRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseMemberRepository courseMemberRepository;

    public CourseDto createCourse(CreateCourseRequest request, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public CourseDto getCourse(UUID courseId, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Page<CourseDto> listMyCourses(UUID userId, CourseRole role, Pageable pageable) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public CourseDto updateCourse(UUID courseId, UpdateCourseRequest request, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void deleteCourse(UUID courseId, UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
