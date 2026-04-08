package com.classroom.core.service;

import com.classroom.core.dto.team.CaptainGradeDistributionRequest;
import com.classroom.core.dto.team.CaptainStudentGradeEntry;
import com.classroom.core.dto.team.TeamGradeDistributionDto;
import com.classroom.core.exception.BadRequestException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaptainGradeServiceTest {

    @Mock
    private PostCaptainRepository postCaptainRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private CourseTeamRepository courseTeamRepository;
    @Mock
    private CourseMemberRepository courseMemberRepository;
    @Mock
    private TeamGradeRepository teamGradeRepository;
    @Mock
    private TeamStudentGradeRepository teamStudentGradeRepository;

    @InjectMocks
    private CaptainGradeService captainGradeService;

    private UUID courseId;
    private UUID postId;
    private UUID captainId;
    private UUID teamId;
    private Post post;
    private CourseTeam team;
    private TeamGrade teamGrade;

    @BeforeEach
    void setUp() {
        courseId = UUID.randomUUID();
        postId = UUID.randomUUID();
        captainId = UUID.randomUUID();
        teamId = UUID.randomUUID();

        Course course = Course.builder().id(courseId).build();
        post = Post.builder().id(postId).course(course).type(PostType.TASK).build();
        team = CourseTeam.builder().id(teamId).course(course).build();
        teamGrade = TeamGrade.builder()
                .id(UUID.randomUUID())
                .post(post)
                .team(team)
                .grade(90)
                .distributionMode(TeamGradeDistributionMode.CAPTAIN_MANUAL)
                .build();
    }

   

    @Test
    void getDistributionForm_notCaptain_throwsForbidden() {
        when(postCaptainRepository.existsByPostIdAndUserId(postId, captainId)).thenReturn(false);

        assertThatThrownBy(() -> captainGradeService.getDistributionForm(courseId, postId, captainId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("User is not a captain for this post");
    }

    @Test
    void saveDistribution_notCaptain_throwsForbidden() {
        when(postCaptainRepository.existsByPostIdAndUserId(postId, captainId)).thenReturn(false);

        assertThatThrownBy(() -> captainGradeService.saveDistribution(courseId, postId, new CaptainGradeDistributionRequest(), captainId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("User is not a captain for this post");
    }

    @Test
    void getDistributionForm_noTeam_throwsNotFound() {
        when(postCaptainRepository.existsByPostIdAndUserId(postId, captainId)).thenReturn(true);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseTeamRepository.findByPostIdAndCaptainId(postId, captainId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> captainGradeService.getDistributionForm(courseId, postId, captainId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Captain's team not found");
    }

    @Test
    void getDistributionForm_wrongMode_throwsBadRequest() {
        teamGrade.setDistributionMode(TeamGradeDistributionMode.AUTO_EQUAL);

        when(postCaptainRepository.existsByPostIdAndUserId(postId, captainId)).thenReturn(true);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseTeamRepository.findByPostIdAndCaptainId(postId, captainId)).thenReturn(Optional.of(team));
        when(teamGradeRepository.findByPostIdAndTeamId(postId, teamId)).thenReturn(Optional.of(teamGrade));

        assertThatThrownBy(() -> captainGradeService.getDistributionForm(courseId, postId, captainId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Captain grade distribution is not enabled for this post");
    }

    @Test
    void getDistributionForm_noGradeSet_throwsBadRequest() {
        when(postCaptainRepository.existsByPostIdAndUserId(postId, captainId)).thenReturn(true);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseTeamRepository.findByPostIdAndCaptainId(postId, captainId)).thenReturn(Optional.of(team));
        when(teamGradeRepository.findByPostIdAndTeamId(postId, teamId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> captainGradeService.getDistributionForm(courseId, postId, captainId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Team grade has not been set yet");
    }

   
    @Test
    void saveDistribution_sumMismatch_throwsBadRequest() {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();

        setupSaveDistributionMocks(s1, s2);

        CaptainGradeDistributionRequest request = buildRequest(s1, 50, s2, 30); // sum=80, teamGrade=90

        assertThatThrownBy(() -> captainGradeService.saveDistribution(courseId, postId, request, captainId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Sum of individual grades (80) must equal team grade (90)");
    }

    @Test
    void saveDistribution_missingMember_throwsBadRequest() {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();

        setupSaveDistributionMocks(s1, s2);

        CaptainGradeDistributionRequest request = buildRequest(s1, 45, unknown, 45); // unknown not in team

        assertThatThrownBy(() -> captainGradeService.saveDistribution(courseId, postId, request, captainId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Grade entries must cover all team members exactly");
    }

    @Test
    void saveDistribution_duplicateStudentEntry_throwsBadRequest() {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();

        setupSaveDistributionMocks(s1, s2);

        CaptainGradeDistributionRequest request = buildRequest(s1, 45, s1, 45); // duplicate s1

        assertThatThrownBy(() -> captainGradeService.saveDistribution(courseId, postId, request, captainId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Duplicate student entries in distribution");
    }

    @Test
    void saveDistribution_valid_returnsDtoWithGrades() {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();

        setupSaveDistributionMocks(s1, s2);
        when(teamStudentGradeRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        CaptainGradeDistributionRequest request = buildRequest(s1, 40, s2, 50); // sum=90

        TeamGradeDistributionDto result = captainGradeService.saveDistribution(courseId, postId, request, captainId);

        assertThat(result.getTeamGrade()).isEqualTo(90);
        assertThat(result.getDistributionMode()).isEqualTo(TeamGradeDistributionMode.CAPTAIN_MANUAL);
        assertThat(result.getStudents()).hasSize(2);
    }

    
    private void setupSaveDistributionMocks(UUID s1, UUID s2) {
        User u1 = User.builder().id(s1).username("u1").displayName("U1").build();
        User u2 = User.builder().id(s2).username("u2").displayName("U2").build();
        CourseMember m1 = CourseMember.builder().user(u1).course(post.getCourse()).role(CourseRole.STUDENT).build();
        CourseMember m2 = CourseMember.builder().user(u2).course(post.getCourse()).role(CourseRole.STUDENT).build();

        when(postCaptainRepository.existsByPostIdAndUserId(postId, captainId)).thenReturn(true);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseTeamRepository.findByPostIdAndCaptainId(postId, captainId)).thenReturn(Optional.of(team));
        when(teamGradeRepository.findByPostIdAndTeamId(postId, teamId)).thenReturn(Optional.of(teamGrade));
        when(courseMemberRepository.findByCourseIdAndTeamIdOrderByJoinedAtAsc(courseId, teamId))
                .thenReturn(List.of(m1, m2));
    }

    private CaptainGradeDistributionRequest buildRequest(UUID id1, int g1, UUID id2, int g2) {
        CaptainStudentGradeEntry e1 = new CaptainStudentGradeEntry();
        e1.setStudentId(id1);
        e1.setGrade(g1);

        CaptainStudentGradeEntry e2 = new CaptainStudentGradeEntry();
        e2.setStudentId(id2);
        e2.setGrade(g2);

        CaptainGradeDistributionRequest req = new CaptainGradeDistributionRequest();
        req.setGrades(List.of(e1, e2));
        return req;
    }
}
