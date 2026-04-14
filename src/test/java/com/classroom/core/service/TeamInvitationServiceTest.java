package com.classroom.core.service;

import com.classroom.core.dto.team.RespondInvitationRequest;
import com.classroom.core.dto.team.SendInvitationRequest;
import com.classroom.core.dto.team.TeamInvitationDto;
import com.classroom.core.exception.BadRequestException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.model.*;
import com.classroom.core.repository.*;
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
class TeamInvitationServiceTest {

    @Mock private TeamInvitationRepository invitationRepository;
    @Mock private PostCaptainRepository postCaptainRepository;
    @Mock private CourseMemberRepository courseMemberRepository;
    @Mock private CourseTeamRepository courseTeamRepository;
    @Mock private PostRepository postRepository;
    @Mock private TeamRequirementTemplateService templateService;

    @InjectMocks
    private TeamInvitationService service;

    private UUID courseId;
    private UUID postId;
    private UUID captainId;
    private UUID studentId;
    private Course course;
    private Post post;
    private User captainUser;
    private User studentUser;
    private CourseMember studentMember;

    @BeforeEach
    void setUp() {
        courseId = UUID.randomUUID();
        postId = UUID.randomUUID();
        captainId = UUID.randomUUID();
        studentId = UUID.randomUUID();

        course = Course.builder().id(courseId).build();
        post = Post.builder().id(postId).course(course).build();
        captainUser = User.builder().id(captainId).username("captain").displayName("Captain").build();
        studentUser = User.builder().id(studentId).username("student").displayName("Student").build();
        studentMember = CourseMember.builder().user(studentUser).course(course).role(CourseRole.STUDENT).build();
    }

  
    @Test
    void sendInvitation_notACaptain_throwsForbidden() {
        when(postCaptainRepository.existsByPostIdAndUserId(postId, captainId)).thenReturn(false);

        assertThatThrownBy(() -> service.sendInvitation(courseId, postId,
                SendInvitationRequest.builder().studentId(studentId).build(), captainId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void sendInvitation_studentNotInCourse_throwsBadRequest() {
        when(postCaptainRepository.existsByPostIdAndUserId(postId, captainId)).thenReturn(true);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, studentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendInvitation(courseId, postId,
                SendInvitationRequest.builder().studentId(studentId).build(), captainId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Student not found in course");
    }

    @Test
    void sendInvitation_studentAlreadyInTeam_throwsBadRequest() {
        when(postCaptainRepository.existsByPostIdAndUserId(postId, captainId)).thenReturn(true);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, studentId))
                .thenReturn(Optional.of(studentMember));
        when(courseTeamRepository.existsByPostIdAndMemberUserId(postId, studentId)).thenReturn(true);

        assertThatThrownBy(() -> service.sendInvitation(courseId, postId,
                SendInvitationRequest.builder().studentId(studentId).build(), captainId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Student is already in a team for this post");
    }

    @Test
    void sendInvitation_duplicateInvitation_throwsBadRequest() {
        when(postCaptainRepository.existsByPostIdAndUserId(postId, captainId)).thenReturn(true);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, studentId))
                .thenReturn(Optional.of(studentMember));
        when(courseTeamRepository.existsByPostIdAndMemberUserId(postId, studentId)).thenReturn(false);
        when(invitationRepository.findByCaptainIdAndStudentIdAndPostId(captainId, studentId, postId))
                .thenReturn(Optional.of(TeamInvitation.builder()
                        .status(TeamInvitation.InvitationStatus.PENDING)
                        .build()));

        assertThatThrownBy(() -> service.sendInvitation(courseId, postId,
                SendInvitationRequest.builder().studentId(studentId).build(), captainId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invitation already exists");
    }

    @Test
    void sendInvitation_declinedInvitation_reopensInvitation() {
        TeamInvitation declinedInvitation = TeamInvitation.builder()
                .id(UUID.randomUUID())
                .captain(captainUser)
                .student(studentUser)
                .post(post)
                .status(TeamInvitation.InvitationStatus.DECLINED)
                .build();

        when(postCaptainRepository.existsByPostIdAndUserId(postId, captainId)).thenReturn(true);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, studentId))
                .thenReturn(Optional.of(studentMember));
        when(courseTeamRepository.existsByPostIdAndMemberUserId(postId, studentId)).thenReturn(false);
        when(invitationRepository.findByCaptainIdAndStudentIdAndPostId(captainId, studentId, postId))
                .thenReturn(Optional.of(declinedInvitation));
        when(invitationRepository.existsByStudentIdAndPostIdAndStatus(studentId, postId,
                TeamInvitation.InvitationStatus.PENDING)).thenReturn(false);
        when(templateService.getAppliedTemplate(post)).thenReturn(null);
        when(invitationRepository.save(any(TeamInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TeamInvitationDto result = service.sendInvitation(courseId, postId,
                SendInvitationRequest.builder().studentId(studentId).build(), captainId);

        assertThat(result.getStatus()).isEqualTo("PENDING");
        verify(invitationRepository).save(any(TeamInvitation.class));
    }

    @Test
    void sendInvitation_studentHasPendingFromAnotherCaptain_throwsBadRequest() {
        when(postCaptainRepository.existsByPostIdAndUserId(postId, captainId)).thenReturn(true);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, studentId))
                .thenReturn(Optional.of(studentMember));
        when(courseTeamRepository.existsByPostIdAndMemberUserId(postId, studentId)).thenReturn(false);
        when(invitationRepository.findByCaptainIdAndStudentIdAndPostId(captainId, studentId, postId))
                .thenReturn(Optional.empty());
        when(invitationRepository.existsByStudentIdAndPostIdAndStatus(studentId, postId,
                TeamInvitation.InvitationStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.sendInvitation(courseId, postId,
                SendInvitationRequest.builder().studentId(studentId).build(), captainId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Student has pending invitation from another captain");
    }

    @Test
    void sendInvitation_teamSizeLimitExceeded_throwsBadRequest() {
        TeamRequirementTemplate template = TeamRequirementTemplate.builder()
                .maxTeamSize(2)
                .build();

        when(postCaptainRepository.existsByPostIdAndUserId(postId, captainId)).thenReturn(true);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, studentId))
                .thenReturn(Optional.of(studentMember));
        when(courseTeamRepository.existsByPostIdAndMemberUserId(postId, studentId)).thenReturn(false);
        when(invitationRepository.findByCaptainIdAndStudentIdAndPostId(captainId, studentId, postId))
                .thenReturn(Optional.empty());
        when(invitationRepository.existsByStudentIdAndPostIdAndStatus(studentId, postId,
                TeamInvitation.InvitationStatus.PENDING)).thenReturn(false);
        when(templateService.getAppliedTemplate(post)).thenReturn(template);
       
        when(invitationRepository.countActiveInvitationsByCaptainAndPost(captainId, postId)).thenReturn(1L);

        assertThatThrownBy(() -> service.sendInvitation(courseId, postId,
                SendInvitationRequest.builder().studentId(studentId).build(), captainId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Team size limit exceeded");
    }

    @Test
    void sendInvitation_studentCategoryMismatch_throwsBadRequest() {
        CourseCategory piano = CourseCategory.builder().id(UUID.randomUUID()).title("Piano").build();
        CourseCategory guitar = CourseCategory.builder().id(UUID.randomUUID()).title("Guitar").build();
        TeamRequirementTemplate template = TeamRequirementTemplate.builder()
                .requiredCategory(piano)
                .build();

        CourseMember guitarStudent = CourseMember.builder()
                .user(studentUser)
                .course(course)
                .role(CourseRole.STUDENT)
                .category(guitar)
                .build();

        when(postCaptainRepository.existsByPostIdAndUserId(postId, captainId)).thenReturn(true);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, studentId))
                .thenReturn(Optional.of(guitarStudent));
        when(courseTeamRepository.existsByPostIdAndMemberUserId(postId, studentId)).thenReturn(false);
        when(invitationRepository.findByCaptainIdAndStudentIdAndPostId(captainId, studentId, postId))
                .thenReturn(Optional.empty());
        when(invitationRepository.existsByStudentIdAndPostIdAndStatus(studentId, postId,
                TeamInvitation.InvitationStatus.PENDING)).thenReturn(false);
        when(templateService.getAppliedTemplate(post)).thenReturn(template);

        assertThatThrownBy(() -> service.sendInvitation(courseId, postId,
                SendInvitationRequest.builder().studentId(studentId).build(), captainId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Student does not match required category");
    }

    @Test
    void sendInvitation_studentHasNoCategory_throwsBadRequest() {
        CourseCategory piano = CourseCategory.builder().id(UUID.randomUUID()).title("Piano").build();
        TeamRequirementTemplate template = TeamRequirementTemplate.builder()
                .requiredCategory(piano)
                .build();

        when(postCaptainRepository.existsByPostIdAndUserId(postId, captainId)).thenReturn(true);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, studentId))
                .thenReturn(Optional.of(studentMember));
        when(courseTeamRepository.existsByPostIdAndMemberUserId(postId, studentId)).thenReturn(false);
        when(invitationRepository.findByCaptainIdAndStudentIdAndPostId(captainId, studentId, postId))
                .thenReturn(Optional.empty());
        when(invitationRepository.existsByStudentIdAndPostIdAndStatus(studentId, postId,
                TeamInvitation.InvitationStatus.PENDING)).thenReturn(false);
        when(templateService.getAppliedTemplate(post)).thenReturn(template);

        assertThatThrownBy(() -> service.sendInvitation(courseId, postId,
                SendInvitationRequest.builder().studentId(studentId).build(), captainId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Student does not match required category");
    }

    @Test
    void sendInvitation_valid_savesAndReturnsDto() {
        TeamInvitation saved = TeamInvitation.builder()
                .id(UUID.randomUUID())
                .captain(captainUser)
                .student(studentUser)
                .post(post)
                .status(TeamInvitation.InvitationStatus.PENDING)
                .build();

        when(postCaptainRepository.existsByPostIdAndUserId(postId, captainId)).thenReturn(true);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, studentId))
                .thenReturn(Optional.of(studentMember));
        when(courseTeamRepository.existsByPostIdAndMemberUserId(postId, studentId)).thenReturn(false);
        when(invitationRepository.findByCaptainIdAndStudentIdAndPostId(captainId, studentId, postId))
                .thenReturn(Optional.empty());
        when(invitationRepository.existsByStudentIdAndPostIdAndStatus(studentId, postId,
                TeamInvitation.InvitationStatus.PENDING)).thenReturn(false);
        when(templateService.getAppliedTemplate(post)).thenReturn(null);
        when(invitationRepository.save(any())).thenReturn(saved);

        TeamInvitationDto result = service.sendInvitation(courseId, postId,
                SendInvitationRequest.builder().studentId(studentId).build(), captainId);

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getStudentId()).isEqualTo(studentId);
    }


    @Test
    void respondToInvitation_notAStudent_throwsForbidden() {
        when(courseMemberRepository.existsByCourseIdAndUserIdAndRole(courseId, studentId, CourseRole.STUDENT))
                .thenReturn(false);

        assertThatThrownBy(() -> service.respondToInvitation(courseId, postId, UUID.randomUUID(),
                RespondInvitationRequest.builder().action("accept").build(), studentId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void respondToInvitation_invitationNotFound_throwsResourceNotFound() {
        when(courseMemberRepository.existsByCourseIdAndUserIdAndRole(courseId, studentId, CourseRole.STUDENT))
                .thenReturn(true);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(invitationRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.respondToInvitation(courseId, postId, UUID.randomUUID(),
                RespondInvitationRequest.builder().action("accept").build(), studentId))
                .isInstanceOf(com.classroom.core.exception.ResourceNotFoundException.class);
    }

    @Test
    void respondToInvitation_invalidAction_throwsBadRequest() {
        UUID invitationId = UUID.randomUUID();
        TeamInvitation invitation = TeamInvitation.builder()
                .id(invitationId)
                .captain(captainUser)
                .student(studentUser)
                .post(post)
                .status(TeamInvitation.InvitationStatus.PENDING)
                .build();

        when(courseMemberRepository.existsByCourseIdAndUserIdAndRole(courseId, studentId, CourseRole.STUDENT))
                .thenReturn(true);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.respondToInvitation(courseId, postId, invitationId,
                RespondInvitationRequest.builder().action("maybe").build(), studentId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid action");
    }

    @Test
    void respondToInvitation_alreadyResponded_throwsBadRequest() {
        UUID invitationId = UUID.randomUUID();
        TeamInvitation invitation = TeamInvitation.builder()
                .id(invitationId)
                .captain(captainUser)
                .student(studentUser)
                .post(post)
                .status(TeamInvitation.InvitationStatus.ACCEPTED)
                .build();

        when(courseMemberRepository.existsByCourseIdAndUserIdAndRole(courseId, studentId, CourseRole.STUDENT))
                .thenReturn(true);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.respondToInvitation(courseId, postId, invitationId,
                RespondInvitationRequest.builder().action("accept").build(), studentId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invitation is not pending");
    }

    @Test
    void respondToInvitation_decline_updatesStatus() {
        UUID invitationId = UUID.randomUUID();
        TeamInvitation invitation = TeamInvitation.builder()
                .id(invitationId)
                .captain(captainUser)
                .student(studentUser)
                .post(post)
                .status(TeamInvitation.InvitationStatus.PENDING)
                .build();

        when(courseMemberRepository.existsByCourseIdAndUserIdAndRole(courseId, studentId, CourseRole.STUDENT))
                .thenReturn(true);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any(TeamInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TeamInvitationDto result = service.respondToInvitation(courseId, postId, invitationId,
                RespondInvitationRequest.builder().action("decline").build(), studentId);

        verify(invitationRepository).save(any(TeamInvitation.class));
        assertThat(result.getStatus()).isEqualTo("DECLINED");
    }


    @Test
    void getStudentInvitations_notAStudent_throwsForbidden() {
        when(courseMemberRepository.existsByCourseIdAndUserIdAndRole(courseId, studentId, CourseRole.STUDENT))
                .thenReturn(false);

        assertThatThrownBy(() -> service.getStudentInvitations(courseId, postId, studentId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getStudentInvitations_returnsInvitationList() {
        TeamInvitation invitation = TeamInvitation.builder()
                .id(UUID.randomUUID())
                .captain(captainUser)
                .student(studentUser)
                .post(post)
                .status(TeamInvitation.InvitationStatus.PENDING)
                .build();

        when(courseMemberRepository.existsByCourseIdAndUserIdAndRole(courseId, studentId, CourseRole.STUDENT))
                .thenReturn(true);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(invitationRepository.findByStudentIdAndPostId(studentId, postId)).thenReturn(List.of(invitation));

        List<TeamInvitationDto> result = service.getStudentInvitations(courseId, postId, studentId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("PENDING");
    }

   
    @Test
    void getCaptainTeamInvitations_notACaptain_throwsForbidden() {
        when(postCaptainRepository.existsByPostIdAndUserId(postId, captainId)).thenReturn(false);

        assertThatThrownBy(() -> service.getCaptainTeamInvitations(courseId, postId, captainId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getCaptainTeamInvitations_returnsList() {
        TeamInvitation invitation = TeamInvitation.builder()
                .id(UUID.randomUUID())
                .captain(captainUser)
                .student(studentUser)
                .post(post)
                .status(TeamInvitation.InvitationStatus.PENDING)
                .build();

        when(postCaptainRepository.existsByPostIdAndUserId(postId, captainId)).thenReturn(true);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(invitationRepository.findByCaptainIdAndPostId(captainId, postId)).thenReturn(List.of(invitation));

        List<TeamInvitationDto> result = service.getCaptainTeamInvitations(courseId, postId, captainId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCaptainId()).isEqualTo(captainId);
    }
}
