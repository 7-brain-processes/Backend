package com.classroom.core.service;

import com.classroom.core.dto.course.CourseDto;
import com.classroom.core.dto.invite.CreateInviteRequest;
import com.classroom.core.dto.invite.InviteDto;
import com.classroom.core.exception.DuplicateResourceException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.Course;
import com.classroom.core.model.CourseMember;
import com.classroom.core.model.CourseRole;
import com.classroom.core.model.Invite;
import com.classroom.core.model.User;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
import com.classroom.core.repository.InviteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InviteService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final InviteRepository inviteRepository;
    private final CourseRepository courseRepository;
    private final CourseMemberRepository courseMemberRepository;

    public List<InviteDto> listInvites(UUID courseId, UUID userId) {
        getCourseOrThrow(courseId);
        ensureTeacher(courseId, userId);

        return inviteRepository.findByCourseId(courseId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public InviteDto createInvite(UUID courseId, CreateInviteRequest request, UUID userId) {
        Course course = getCourseOrThrow(courseId);
        ensureTeacher(courseId, userId);

        Invite invite = Invite.builder()
                .code(generateUniqueCode())
                .course(course)
                .role(request.getRole())
                .expiresAt(request.getExpiresAt())
                .maxUses(request.getMaxUses())
                .currentUses(0)
                .build();

        Invite saved = inviteRepository.save(invite);
        return toDto(saved);
    }

    @Transactional
    public void revokeInvite(UUID courseId, UUID inviteId, UUID userId) {
        Course course = getCourseOrThrow(courseId);
        ensureTeacher(courseId, userId);

        Invite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new ResourceNotFoundException("Invite not found"));

        if (!invite.getCourse().getId().equals(course.getId())) {
            throw new ResourceNotFoundException("Invite not found");
        }

        inviteRepository.delete(invite);
    }

    @Transactional
    public CourseDto joinCourse(String code, UUID userId) {
        Invite invite = inviteRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Invite not found"));

        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(Instant.now())) {
            throw new ResourceNotFoundException("Invite not found");
        }

        if (invite.getMaxUses() != null && invite.getCurrentUses() >= invite.getMaxUses()) {
            throw new ResourceNotFoundException("Invite not found");
        }

        Course course = invite.getCourse();

        if (courseMemberRepository.existsByCourseIdAndUserId(course.getId(), userId)) {
            throw new DuplicateResourceException("User is already a member of this course");
        }

        CourseMember member = CourseMember.builder()
                .course(course)
                .user(User.builder().id(userId).build())
                .role(invite.getRole())
                .build();

        courseMemberRepository.save(member);

        invite.setCurrentUses(invite.getCurrentUses() + 1);
        inviteRepository.save(invite);

        int teacherCount = courseMemberRepository.countByCourseIdAndRole(course.getId(), CourseRole.TEACHER);
        int studentCount = courseMemberRepository.countByCourseIdAndRole(course.getId(), CourseRole.STUDENT);

        return CourseDto.builder()
                .id(course.getId())
                .name(course.getName())
                .description(course.getDescription())
                .createdAt(course.getCreatedAt())
                .currentUserRole(invite.getRole())
                .teacherCount(teacherCount)
                .studentCount(studentCount)
                .build();
    }

    private Course getCourseOrThrow(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
    }

    private CourseMember ensureTeacher(UUID courseId, UUID userId) {
        CourseMember member = courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this course"));

        if (member.getRole() != CourseRole.TEACHER) {
            throw new ForbiddenException("Only teachers can manage invites");
        }

        return member;
    }

    private InviteDto toDto(Invite invite) {
        return InviteDto.builder()
                .id(invite.getId())
                .code(invite.getCode())
                .role(invite.getRole())
                .expiresAt(invite.getExpiresAt())
                .maxUses(invite.getMaxUses())
                .currentUses(invite.getCurrentUses())
                .createdAt(invite.getCreatedAt())
                .build();
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = randomCode();
        } while (inviteRepository.findByCode(code).isPresent());
        return code;
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}