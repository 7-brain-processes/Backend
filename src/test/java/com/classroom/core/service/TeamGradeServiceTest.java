package com.classroom.core.service;

import com.classroom.core.dto.team.MyTeamGradeDto;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.Course;
import com.classroom.core.model.CourseMember;
import com.classroom.core.model.CourseRole;
import com.classroom.core.model.CourseTeam;
import com.classroom.core.model.Post;
import com.classroom.core.model.PostType;
import com.classroom.core.model.TeamGrade;
import com.classroom.core.model.TeamGradeDistributionMode;
import com.classroom.core.model.TeamStudentGrade;
import com.classroom.core.model.User;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
import com.classroom.core.repository.CourseTeamRepository;
import com.classroom.core.repository.PostRepository;
import com.classroom.core.repository.TeamGradeRepository;
import com.classroom.core.repository.TeamGradeVoteRepository;
import com.classroom.core.repository.TeamStudentGradeRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamGradeServiceTest {

    @Mock CourseRepository courseRepository;
    @Mock CourseMemberRepository courseMemberRepository;
    @Mock PostRepository postRepository;
    @Mock CourseTeamRepository courseTeamRepository;
    @Mock TeamGradeRepository teamGradeRepository;
    @Mock TeamStudentGradeRepository teamStudentGradeRepository;
    @Mock TeamGradeVoteRepository teamGradeVoteRepository;

    @InjectMocks TeamGradeService service;

    private UUID courseId;
    private UUID postId;
    private UUID userId;
    private UUID teamId;
    private Course course;
    private Post post;
    private CourseTeam team;
    private User studentUser;
    private CourseMember studentMembership;

    @BeforeEach
    void setUp() {
        courseId = UUID.randomUUID();
        postId = UUID.randomUUID();
        userId = UUID.randomUUID();
        teamId = UUID.randomUUID();

        course = Course.builder().id(courseId).build();
        post = Post.builder().id(postId).course(course).type(PostType.TASK).build();
        team = CourseTeam.builder().id(teamId).course(course).name("Team A").build();
        studentUser = User.builder().id(userId).username("student").displayName("Student").build();
        studentMembership = CourseMember.builder()
                .course(course)
                .user(studentUser)
                .role(CourseRole.STUDENT)
                .team(team)
                .build();
    }

    @Test
    void getCurrentStudentTeamGrade_withoutTeamGrade_returnsEmptyStudentView() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(studentMembership));
        when(courseMemberRepository.findStudentTeamInPost(courseId, userId, postId))
                .thenReturn(Optional.of(studentMembership));
        when(teamGradeRepository.findByPostIdAndTeamId(postId, teamId))
                .thenReturn(Optional.empty());

        MyTeamGradeDto result = service.getCurrentStudentTeamGrade(courseId, postId, userId);

        assertThat(result.getTeamId()).isEqualTo(teamId);
        assertThat(result.getTeamName()).isEqualTo("Team A");
        assertThat(result.getTeamGrade()).isNull();
        assertThat(result.getDistributionMode()).isEqualTo(TeamGradeDistributionMode.MANUAL);
        assertThat(result.getMyGrade()).isNull();
        assertThat(result.isFinalized()).isFalse();
        assertThat(result.getFinalDistribution()).isNull();
    }

    @Test
    void getCurrentStudentTeamGrade_withSavedGrades_returnsStudentView() {
        TeamGrade grade = TeamGrade.builder()
                .id(UUID.randomUUID())
                .post(post)
                .team(team)
                .grade(85)
                .distributionMode(TeamGradeDistributionMode.AUTO_EQUAL)
                .build();

        User teammateUser = User.builder()
                .id(UUID.randomUUID())
                .username("teammate")
                .displayName("Teammate")
                .build();
        CourseMember teammateMembership = CourseMember.builder()
                .course(course)
                .user(teammateUser)
                .role(CourseRole.STUDENT)
                .team(team)
                .build();

        TeamStudentGrade mySavedGrade = TeamStudentGrade.builder()
                .teamGrade(grade)
                .student(studentUser)
                .grade(43)
                .build();
        TeamStudentGrade teammateSavedGrade = TeamStudentGrade.builder()
                .teamGrade(grade)
                .student(teammateUser)
                .grade(42)
                .build();

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(studentMembership));
        when(courseMemberRepository.findStudentTeamInPost(courseId, userId, postId))
                .thenReturn(Optional.of(studentMembership));
        when(teamGradeRepository.findByPostIdAndTeamId(postId, teamId))
                .thenReturn(Optional.of(grade));
        when(teamStudentGradeRepository.findByTeamGradeIdOrderByStudentIdAsc(grade.getId()))
                .thenReturn(List.of(mySavedGrade, teammateSavedGrade));
        when(teamStudentGradeRepository.findByTeamGradeIdAndStudentId(grade.getId(), userId))
                .thenReturn(Optional.of(mySavedGrade));
        when(courseMemberRepository.findByCourseIdAndTeamIdOrderByJoinedAtAsc(courseId, teamId))
                .thenReturn(List.of(studentMembership, teammateMembership));

        MyTeamGradeDto result = service.getCurrentStudentTeamGrade(courseId, postId, userId);

        assertThat(result.getTeamGrade()).isEqualTo(85);
        assertThat(result.getDistributionMode()).isEqualTo(TeamGradeDistributionMode.AUTO_EQUAL);
        assertThat(result.getMyGrade()).isEqualTo(43);
        assertThat(result.isFinalized()).isTrue();
        assertThat(result.getFinalDistribution()).hasSize(2);
    }

    @Test
    void getCurrentStudentTeamGrade_withoutTeam_throwsNotFound() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(studentMembership));
        when(courseMemberRepository.findStudentTeamInPost(courseId, userId, postId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentStudentTeamGrade(courseId, postId, userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Student is not in any team for this assignment");
    }
}
