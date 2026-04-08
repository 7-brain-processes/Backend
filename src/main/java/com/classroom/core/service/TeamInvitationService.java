package com.classroom.core.service;

import com.classroom.core.dto.course.CourseCategoryDto;
import com.classroom.core.dto.team.AvailableStudentDto;
import com.classroom.core.dto.team.RespondInvitationRequest;
import com.classroom.core.dto.team.SendInvitationRequest;
import com.classroom.core.dto.team.TeamInvitationDto;
import com.classroom.core.exception.BadRequestException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.*;
import com.classroom.core.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamInvitationService {

    private final TeamInvitationRepository invitationRepository;
    private final PostCaptainRepository postCaptainRepository;
    private final CourseMemberRepository courseMemberRepository;
    private final CourseTeamRepository courseTeamRepository;
    private final PostRepository postRepository;
    private final TeamRequirementTemplateService templateService;

    public List<AvailableStudentDto> getAvailableStudentsForCaptain(UUID courseId, UUID postId, UUID captainId) {
        ensureIsCaptain(courseId, postId, captainId);

        Post post = getPostOrThrow(courseId, postId);
        List<CourseMember> students = courseMemberRepository
                .findByCourseIdAndRoleOrderByJoinedAtAsc(courseId, CourseRole.STUDENT);

        TeamRequirementTemplate template = templateService.getAppliedTemplate(post);

        return students.stream()
                .filter(student -> !isStudentInAnyTeam(postId, student.getUser().getId()))
                .filter(student -> !hasPendingInvitationFromThisCaptain(captainId, student.getUser().getId(), postId))
                .filter(student -> template == null || matchesTemplateRequirements(student, template))
                .map(this::toAvailableStudentDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public TeamInvitationDto sendInvitation(UUID courseId, UUID postId, SendInvitationRequest request, UUID captainId) {
        ensureIsCaptain(courseId, postId, captainId);

        Post post = getPostOrThrow(courseId, postId);
        UUID studentId = request.getStudentId();

        CourseMember student = courseMemberRepository
                .findByCourseIdAndUserId(courseId, studentId)
                .filter(member -> member.getRole() == CourseRole.STUDENT)
                .orElseThrow(() -> new BadRequestException("Student not found in course"));

        if (isStudentInAnyTeam(postId, studentId)) {
            throw new BadRequestException("Student is already in a team for this post");
        }

        if (invitationRepository.findByCaptainIdAndStudentIdAndPostId(captainId, studentId, postId).isPresent()) {
            throw new BadRequestException("Invitation already exists");
        }

        if (invitationRepository.existsByStudentIdAndPostIdAndStatus(studentId, postId, TeamInvitation.InvitationStatus.PENDING)) {
            throw new BadRequestException("Student has pending invitation from another captain");
        }

        TeamRequirementTemplate template = templateService.getAppliedTemplate(post);
        if (template != null) {
            validateInvitationAgainstTemplate(student, template, captainId, postId);
        }

        TeamInvitation invitation = TeamInvitation.builder()
                .captain(User.builder().id(captainId).build())
                .student(User.builder().id(studentId).build())
                .post(post)
                .status(TeamInvitation.InvitationStatus.PENDING)
                .build();

        invitation = invitationRepository.save(invitation);
        return toDto(invitation);
    }

    public List<TeamInvitationDto> getCaptainTeamInvitations(UUID courseId, UUID postId, UUID captainId) {
        ensureIsCaptain(courseId, postId, captainId);
        getPostOrThrow(courseId, postId);

        return invitationRepository.findByCaptainIdAndPostId(captainId, postId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<AvailableStudentDto> getCaptainTeam(UUID courseId, UUID postId, UUID captainId) {
        ensureIsCaptain(courseId, postId, captainId);

        return courseTeamRepository.findByPostIdAndCaptainId(postId, captainId)
                .map(team -> courseMemberRepository.findByCourseIdAndTeamIdOrderByJoinedAtAsc(courseId, team.getId())
                        .stream()
                        .map(this::toAvailableStudentDto)
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }

    public List<TeamInvitationDto> getStudentInvitations(UUID courseId, UUID postId, UUID studentId) {
        ensureIsStudent(courseId, studentId);
        getPostOrThrow(courseId, postId);

        return invitationRepository.findByStudentIdAndPostId(studentId, postId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public TeamInvitationDto respondToInvitation(UUID courseId, UUID postId, UUID invitationId,
                                                RespondInvitationRequest request, UUID studentId) {
        ensureIsStudent(courseId, studentId);
        getPostOrThrow(courseId, postId);

        TeamInvitation invitation = invitationRepository.findById(invitationId)
                .filter(inv -> inv.getStudent().getId().equals(studentId) && inv.getPost().getId().equals(postId))
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));

        if (invitation.getStatus() != TeamInvitation.InvitationStatus.PENDING) {
            throw new BadRequestException("Invitation is not pending");
        }

        TeamInvitation.InvitationStatus newStatus;
        if ("accept".equals(request.getAction())) {
            newStatus = TeamInvitation.InvitationStatus.ACCEPTED;
            createOrUpdateTeam(invitation);
        } else if ("decline".equals(request.getAction())) {
            newStatus = TeamInvitation.InvitationStatus.DECLINED;
        } else {
            throw new BadRequestException("Invalid action");
        }

        invitationRepository.updateStatus(invitationId, newStatus);
        invitation.setStatus(newStatus);
        return toDto(invitation);
    }

    private void createOrUpdateTeam(TeamInvitation invitation) {
        UUID captainId = invitation.getCaptain().getId();
        UUID postId = invitation.getPost().getId();
        UUID studentId = invitation.getStudent().getId();
        Post post = invitation.getPost();

        CourseTeam team = courseTeamRepository.findByPostIdAndCaptainId(postId, captainId)
                .orElseGet(() -> {
                    return courseTeamRepository.save(CourseTeam.builder()
                            .course(post.getCourse())
                            .post(post)
                            .name("Captain Team " + captainId.toString().substring(0, 8))
                            .maxSize(getMaxTeamSize(post))
                            .selfEnrollmentEnabled(false)
                            .build());
                });

        if (!isUserInTeam(team.getId(), captainId)) {
            CourseMember captainMember = courseMemberRepository
                    .findByCourseIdAndUserId(post.getCourse().getId(), captainId)
                    .orElseThrow(() -> new ResourceNotFoundException("Captain not found"));
            captainMember.setTeam(team);
            courseMemberRepository.save(captainMember);
        }

        CourseMember studentMember = courseMemberRepository
                .findByCourseIdAndUserId(post.getCourse().getId(), studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));
        studentMember.setTeam(team);
        courseMemberRepository.save(studentMember);
    }

    private Integer getMaxTeamSize(Post post) {
        TeamRequirementTemplate template = templateService.getAppliedTemplate(post);
        return template != null ? template.getMaxTeamSize() : 4;
    }

    private boolean isUserInTeam(UUID teamId, UUID userId) {
        return courseMemberRepository.existsByTeamIdAndUserId(teamId, userId);
    }

    private void validateInvitationAgainstTemplate(CourseMember student, TeamRequirementTemplate template,
                                                 UUID captainId, UUID postId) {
        if (template.getRequiredCategory() != null) {
            if (student.getCategory() == null || !student.getCategory().getId().equals(template.getRequiredCategory().getId())) {
                throw new BadRequestException("Student does not match required category");
            }
        }


        long activeInvitations = invitationRepository.countActiveInvitationsByCaptainAndPost(captainId, postId);
        if (template.getMaxTeamSize() != null && activeInvitations + 1 >= template.getMaxTeamSize()) {
            throw new BadRequestException("Team size limit exceeded");
        }

    }

    private boolean matchesTemplateRequirements(CourseMember student, TeamRequirementTemplate template) {
        if (template.getRequiredCategory() != null) {
            return student.getCategory() != null && student.getCategory().getId().equals(template.getRequiredCategory().getId());
        }
        return true;
    }

    private boolean isStudentInAnyTeam(UUID postId, UUID studentId) {
        return courseTeamRepository.existsByPostIdAndMemberUserId(postId, studentId);
    }

    private boolean hasPendingInvitationFromThisCaptain(UUID captainId, UUID studentId, UUID postId) {
        return invitationRepository.existsByCaptainIdAndStudentIdAndPostIdAndStatus(
                captainId, studentId, postId, TeamInvitation.InvitationStatus.PENDING);
    }

    private void ensureIsCaptain(UUID courseId, UUID postId, UUID userId) {
        if (!postCaptainRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new ForbiddenException("User is not a captain for this post");
        }
    }

    private void ensureIsStudent(UUID courseId, UUID userId) {
        boolean isStudent = courseMemberRepository.existsByCourseIdAndUserIdAndRole(courseId, userId, CourseRole.STUDENT);
        if (!isStudent) {
            throw new ForbiddenException("User is not a student in this course");
        }
    }

    private Post getPostOrThrow(UUID courseId, UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        if (!post.getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Post not found in course");
        }
        return post;
    }

    private AvailableStudentDto toAvailableStudentDto(CourseMember member) {
        return AvailableStudentDto.builder()
                .userId(member.getUser().getId())
                .username(member.getUser().getUsername())
                .displayName(member.getUser().getDisplayName())
                .category(member.getCategory() != null ? toCategoryDto(member.getCategory()) : null)
                .build();
    }

    private CourseCategoryDto toCategoryDto(CourseCategory category) {
        return CourseCategoryDto.builder()
                .id(category.getId())
                .title(category.getTitle())
                .build();
    }

    private TeamInvitationDto toDto(TeamInvitation invitation) {
        return TeamInvitationDto.builder()
                .id(invitation.getId())
                .captainId(invitation.getCaptain().getId())
                .captainUsername(invitation.getCaptain().getUsername())
                .captainDisplayName(invitation.getCaptain().getDisplayName())
                .studentId(invitation.getStudent().getId())
                .studentUsername(invitation.getStudent().getUsername())
                .studentDisplayName(invitation.getStudent().getDisplayName())
                .postId(invitation.getPost().getId())
                .status(invitation.getStatus().name())
                .createdAt(invitation.getCreatedAt())
                .respondedAt(invitation.getRespondedAt())
                .build();
    }
}