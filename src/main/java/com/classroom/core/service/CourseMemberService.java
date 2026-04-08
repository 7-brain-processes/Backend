package com.classroom.core.service;

import com.classroom.core.dto.auth.UserDto;
import com.classroom.core.dto.course.CourseCategoryDto;
import com.classroom.core.dto.member.MemberDto;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.Course;
import com.classroom.core.model.CourseMember;
import com.classroom.core.model.CourseRole;
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
@Transactional(readOnly = true)
public class CourseMemberService {

    private final CourseMemberRepository courseMemberRepository;
    private final CourseRepository courseRepository;

    public Page<MemberDto> listMembers(UUID courseId, CourseRole role, Pageable pageable, UUID currentUserId) {
        getCourseOrThrow(courseId);
        ensureMember(courseId, currentUserId);

        Page<CourseMember> page = (role == null)
                ? courseMemberRepository.findByCourseId(courseId, pageable)
                : courseMemberRepository.findByCourseIdAndRole(courseId, role, pageable);

        return page.map(this::toDto);
    }

    @Transactional
    public void removeMember(UUID courseId, UUID targetUserId, UUID currentUserId) {
        getCourseOrThrow(courseId);

        CourseMember currentMember = courseMemberRepository.findByCourseIdAndUserId(courseId, currentUserId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this course"));

        if (currentMember.getRole() != CourseRole.TEACHER) {
            throw new ForbiddenException("Only teachers can remove members");
        }

        CourseMember targetMember = courseMemberRepository.findByCourseIdAndUserId(courseId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));

        courseMemberRepository.delete(targetMember);
    }

    @Transactional
    public void leaveCourse(UUID courseId, UUID userId) {
        getCourseOrThrow(courseId);

        CourseMember member = courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("You are not a member of this course"));

        courseMemberRepository.delete(member);
    }

    private Course getCourseOrThrow(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
    }

    private CourseMember ensureMember(UUID courseId, UUID userId) {
        return courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this course"));
    }

    private MemberDto toDto(CourseMember member) {
        return MemberDto.builder()
                .user(UserDto.from(member.getUser()))
                .role(member.getRole())
                .joinedAt(member.getJoinedAt())
                .category(member.getCategory() == null ? null : CourseCategoryDto.builder()
                        .id(member.getCategory().getId())
                        .title(member.getCategory().getTitle())
                        .description(member.getCategory().getDescription())
                        .active(member.getCategory().isActive())
                        .createdAt(member.getCategory().getCreatedAt())
                        .build())
                .build();
    }
}
