package com.classroom.core.service;

import com.classroom.core.dto.team.CaptainGradeDistributionRequest;
import com.classroom.core.dto.team.CaptainStudentGradeEntry;
import com.classroom.core.dto.team.StudentDistributedGradeDto;
import com.classroom.core.dto.team.VoteStatusDto;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradeVotingServiceTest {

    @Mock PostRepository postRepository;
    @Mock CourseTeamRepository courseTeamRepository;
    @Mock CourseMemberRepository courseMemberRepository;
    @Mock TeamGradeRepository teamGradeRepository;
    @Mock TeamStudentGradeRepository teamStudentGradeRepository;
    @Mock TeamGradeVoteRepository voteRepository;

    @InjectMocks GradeVotingService service;

    private UUID courseId, postId, teamId, userId;
    private Course course;
    private Post post;
    private CourseTeam team;
    private TeamGrade teamGrade;
    private UUID s1, s2;
    private User u1, u2;
    private CourseMember m1, m2;
    private CourseMember voterMembership;

    @BeforeEach
    void setUp() {
        courseId = UUID.randomUUID();
        postId   = UUID.randomUUID();
        teamId   = UUID.randomUUID();
        userId   = UUID.randomUUID();
        s1       = UUID.randomUUID();
        s2       = UUID.randomUUID();

        course = Course.builder().id(courseId).build();
        post   = Post.builder().id(postId).course(course).type(PostType.TASK).build();
        team   = CourseTeam.builder().id(teamId).course(course).name("T1").build();

        teamGrade = TeamGrade.builder()
                .id(UUID.randomUUID())
                .post(post)
                .team(team)
                .grade(90)
                .distributionMode(TeamGradeDistributionMode.TEAM_VOTE)
                .build();

        u1 = User.builder().id(s1).username("u1").displayName("U1").build();
        u2 = User.builder().id(s2).username("u2").displayName("U2").build();
        m1 = CourseMember.builder().user(u1).course(course).role(CourseRole.STUDENT).team(team).build();
        m2 = CourseMember.builder().user(u2).course(course).role(CourseRole.STUDENT).team(team).build();

        User voter = User.builder().id(userId).username("voter").displayName("Voter").build();
        voterMembership = CourseMember.builder()
                .user(voter).course(course).role(CourseRole.STUDENT).team(team).build();
    }

    
    @Test
    void submitVote_notTeamMember_throwsForbidden() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.findStudentTeamInPost(courseId, userId, postId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitVote(courseId, postId, new CaptainGradeDistributionRequest(), userId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not in a team for this post");
    }

    @Test
    void submitVote_wrongMode_throwsBadRequest() {
        teamGrade.setDistributionMode(TeamGradeDistributionMode.MANUAL);

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.findStudentTeamInPost(courseId, userId, postId))
                .thenReturn(Optional.of(voterMembership));
        when(teamGradeRepository.findByPostIdAndTeamId(postId, teamId))
                .thenReturn(Optional.of(teamGrade));

        assertThatThrownBy(() -> service.submitVote(courseId, postId, new CaptainGradeDistributionRequest(), userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Team vote distribution is not enabled for this post");
    }

    @Test
    void submitVote_alreadyVoted_throwsBadRequest() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.findStudentTeamInPost(courseId, userId, postId))
                .thenReturn(Optional.of(voterMembership));
        when(teamGradeRepository.findByPostIdAndTeamId(postId, teamId))
                .thenReturn(Optional.of(teamGrade));
        when(voteRepository.existsByTeamGradeIdAndVoterId(teamGrade.getId(), userId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.submitVote(courseId, postId, new CaptainGradeDistributionRequest(), userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You have already submitted your vote");
    }

    @Test
    void submitVote_sumMismatch_throwsBadRequest() {
        setupSubmitMocks();

        CaptainGradeDistributionRequest req = buildRequest(s1, 40, s2, 40); // sum=80, grade=90

        assertThatThrownBy(() -> service.submitVote(courseId, postId, req, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Sum of voted grades (80) must equal team grade (90)");
    }

    @Test
    void submitVote_missingMember_throwsBadRequest() {
        setupSubmitMocks();

        UUID unknown = UUID.randomUUID();
        CaptainGradeDistributionRequest req = buildRequest(s1, 45, unknown, 45);

        assertThatThrownBy(() -> service.submitVote(courseId, postId, req, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Vote must cover all team members exactly");
    }

    @Test
    void submitVote_duplicate_throwsBadRequest() {
        setupSubmitMocks();

        CaptainGradeDistributionRequest req = buildRequest(s1, 45, s1, 45); // duplicate

        assertThatThrownBy(() -> service.submitVote(courseId, postId, req, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Duplicate student entries in vote");
    }

    @Test
    void submitVote_valid_savesVoteAndReturnsStatus() {
        setupSubmitMocks();
        when(voteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(voteRepository.countByTeamGradeId(teamGrade.getId())).thenReturn(1L); // not last
        when(voteRepository.findByTeamGradeIdWithEntries(teamGrade.getId())).thenReturn(List.of());
        when(teamStudentGradeRepository.findByTeamGradeIdOrderByStudentIdAsc(teamGrade.getId()))
                .thenReturn(List.of());

        CaptainGradeDistributionRequest req = buildRequest(s1, 40, s2, 50); // sum=90

        VoteStatusDto result = service.submitVote(courseId, postId, req, userId);

        assertThat(result.getTeamId()).isEqualTo(teamId);
        assertThat(result.getTeamGrade()).isEqualTo(90);
        assertThat(result.isFinalized()).isFalse();
        verify(voteRepository).save(any());
    }


    @Test
    void computeFinalDistribution_twoVotesEqualGrades_returnsAverage() {
         List<TeamGradeVote> votes = List.of(
                buildVote(s1, 60, s2, 30),
                buildVote(s1, 60, s2, 30)
        );

        List<StudentDistributedGradeDto> result =
                service.computeFinalDistribution(List.of(m1, m2), votes, 90);

        assertThat(gradeFor(result, s1)).isEqualTo(60);
        assertThat(gradeFor(result, s2)).isEqualTo(30);
        assertThat(result.stream().mapToInt(StudentDistributedGradeDto::getGrade).sum()).isEqualTo(90);
    }

    @Test
    void computeFinalDistribution_differentVotes_returnsFloorAverage() {
        List<TeamGradeVote> votes = List.of(
                buildVote(s1, 70, s2, 20),
                buildVote(s1, 50, s2, 40)
        );

        List<StudentDistributedGradeDto> result =
                service.computeFinalDistribution(List.of(m1, m2), votes, 90);

        assertThat(result.stream().mapToInt(StudentDistributedGradeDto::getGrade).sum()).isEqualTo(90);
    }

    @Test
    void computeFinalDistribution_withRemainder_sumsToTeamGrade() {
        List<TeamGradeVote> votes = List.of(
                buildVote(s1, 61, s2, 29),
                buildVote(s1, 60, s2, 30)
        );

        List<StudentDistributedGradeDto> result =
                service.computeFinalDistribution(List.of(m1, m2), votes, 90);

        assertThat(result.stream().mapToInt(StudentDistributedGradeDto::getGrade).sum()).isEqualTo(90);
    }

    @Test
    void computeFinalDistribution_singleVote_returnsExact() {
        List<TeamGradeVote> votes = List.of(buildVote(s1, 55, s2, 35));

        List<StudentDistributedGradeDto> result =
                service.computeFinalDistribution(List.of(m1, m2), votes, 90);

        assertThat(gradeFor(result, s1)).isEqualTo(55);
        assertThat(gradeFor(result, s2)).isEqualTo(35);
    }

  
    @Test
    void finalizeVoting_notTeacher_throwsForbidden() {
        CourseMember student = CourseMember.builder()
                .user(u1).course(course).role(CourseRole.STUDENT).build();
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(student));

        assertThatThrownBy(() -> service.finalizeVoting(courseId, postId, teamId, userId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Only teachers can perform this action");
    }

    @Test
    void finalizeVoting_noVotes_throwsBadRequest() {
        setupTeacherMocks();
        when(voteRepository.findByTeamGradeIdWithEntries(teamGrade.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.finalizeVoting(courseId, postId, teamId, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("No votes have been submitted yet");
    }

    @Test
    void finalizeVoting_valid_savesGradesAndReturnsDto() {
        setupTeacherMocks();

        List<TeamGradeVote> votes = List.of(buildVote(s1, 50, s2, 40));
        when(voteRepository.findByTeamGradeIdWithEntries(teamGrade.getId())).thenReturn(votes);
        when(courseMemberRepository.findByCourseIdAndTeamIdOrderByJoinedAtAsc(courseId, teamId))
                .thenReturn(List.of(m1, m2));
        when(teamStudentGradeRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.finalizeVoting(courseId, postId, teamId, userId);

        assertThat(result.getTeamGrade()).isEqualTo(90);
        assertThat(result.getStudents()).hasSize(2);
        assertThat(result.getStudents().stream().mapToInt(StudentDistributedGradeDto::getGrade).sum())
                .isEqualTo(90);
        verify(teamStudentGradeRepository).deleteByTeamGradeId(teamGrade.getId());
        verify(teamStudentGradeRepository).saveAll(any());
    }

    
    private void setupSubmitMocks() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.findStudentTeamInPost(courseId, userId, postId))
                .thenReturn(Optional.of(voterMembership));
        when(teamGradeRepository.findByPostIdAndTeamId(postId, teamId))
                .thenReturn(Optional.of(teamGrade));
        when(voteRepository.existsByTeamGradeIdAndVoterId(teamGrade.getId(), userId))
                .thenReturn(false);
        when(courseMemberRepository.findByCourseIdAndTeamIdOrderByJoinedAtAsc(courseId, teamId))
                .thenReturn(List.of(m1, m2));
    }

    private void setupTeacherMocks() {
        User teacher = User.builder().id(userId).username("teacher").displayName("Teacher").build();
        CourseMember teacherMember = CourseMember.builder()
                .user(teacher).course(course).role(CourseRole.TEACHER).build();
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(teacherMember));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseTeamRepository.findByIdAndCourseId(teamId, courseId)).thenReturn(Optional.of(team));
        when(teamGradeRepository.findByPostIdAndTeamId(postId, teamId))
                .thenReturn(Optional.of(teamGrade));
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

    private TeamGradeVote buildVote(UUID sid1, int g1, UUID sid2, int g2) {
        TeamGradeVote vote = new TeamGradeVote();
        vote.setId(UUID.randomUUID());
        vote.setTeamGrade(teamGrade);
        User voter = User.builder().id(UUID.randomUUID()).username("v").displayName("V").build();
        vote.setVoter(voter);

        TeamGradeVoteEntry e1 = new TeamGradeVoteEntry();
        e1.setVote(vote);
        e1.setStudent(u1);
        e1.setGrade(g1);

        TeamGradeVoteEntry e2 = new TeamGradeVoteEntry();
        e2.setVote(vote);
        e2.setStudent(u2);
        e2.setGrade(g2);

        vote.setEntries(List.of(e1, e2));
        return vote;
    }

    private int gradeFor(List<StudentDistributedGradeDto> list, UUID studentId) {
        return list.stream()
                .filter(d -> d.getStudent().getId().equals(studentId))
                .findFirst()
                .orElseThrow()
                .getGrade();
    }
}
