package com.classroom.core.service;

import com.classroom.core.dto.team.PostCaptainDto;
import com.classroom.core.dto.team.SelectCaptainsRequest;
import com.classroom.core.dto.team.SelectCaptainsResultDto;
import com.classroom.core.exception.BadRequestException;
import com.classroom.core.model.*;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.PostCaptainRepository;
import com.classroom.core.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostCaptainServiceTest {

    @Mock
    private PostCaptainRepository postCaptainRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private CourseMemberRepository courseMemberRepository;

    @Mock
    private TeamRequirementTemplateService templateService;

    @InjectMocks
    private PostCaptainService postCaptainService;

    private UUID courseId;
    private UUID postId;
    private UUID userId;
    private Post post;
    private CourseMember student;

    @BeforeEach
    void setUp() {
        courseId = UUID.randomUUID();
        postId = UUID.randomUUID();
        userId = UUID.randomUUID();

        Course course = Course.builder().id(courseId).build();
        post = Post.builder()
                .id(postId)
                .course(course)
                .teamFormationMode(TeamFormationMode.CAPTAIN_SELECTION)
                .build();

        User user = User.builder().id(userId).username("student").displayName("Student").build();
        student = CourseMember.builder()
                .user(user)
                .course(course)
                .role(CourseRole.STUDENT)
                .build();
    }

    private List<CourseMember> makeStudents(int count) {
        List<CourseMember> list = new ArrayList<>();
        Course course = Course.builder().id(courseId).build();
        for (int i = 0; i < count; i++) {
            User u = User.builder().id(UUID.randomUUID()).username("s" + i).displayName("S" + i).build();
            list.add(CourseMember.builder().user(u).course(course).role(CourseRole.STUDENT).build());
        }
        return list;
    }

    private List<CourseMember> makeStudentsWithCategory(int withCategory, int withoutCategory, CourseCategory category) {
        List<CourseMember> list = new ArrayList<>();
        Course course = Course.builder().id(courseId).build();
        for (int i = 0; i < withCategory; i++) {
            User u = User.builder().id(UUID.randomUUID()).username("sc" + i).displayName("SC" + i).build();
            list.add(CourseMember.builder().user(u).course(course).role(CourseRole.STUDENT).category(category).build());
        }
        for (int i = 0; i < withoutCategory; i++) {
            User u = User.builder().id(UUID.randomUUID()).username("s" + i).displayName("S" + i).build();
            list.add(CourseMember.builder().user(u).course(course).role(CourseRole.STUDENT).build());
        }
        return list;
    }

    @Test
    void calculateCaptainCount_with10Students_noTemplate_returns2() {
        int count = postCaptainService.calculateCaptainCount(makeStudents(10), null);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void calculateCaptainCount_with5Students_noTemplate_returns1() {
        int count = postCaptainService.calculateCaptainCount(makeStudents(5), null);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void calculateCaptainCount_withMinTeamSize_usesFloorDivision() {
        TeamRequirementTemplate template = TeamRequirementTemplate.builder()
                .minTeamSize(3)
                .build();
        int count = postCaptainService.calculateCaptainCount(makeStudents(12), template);
        assertThat(count).isEqualTo(4);
    }

    @Test
    void calculateCaptainCount_withRequiredCategory_limitedByCategoryCount() {
        CourseCategory piano = CourseCategory.builder().id(UUID.randomUUID()).title("Piano").build();
        TeamRequirementTemplate template = TeamRequirementTemplate.builder()
                .requiredCategory(piano)
                .minTeamSize(2)
                .build();
        List<CourseMember> students = makeStudentsWithCategory(3, 7, piano);
        int count = postCaptainService.calculateCaptainCount(students, template);
        assertThat(count).isEqualTo(3);
    }

    @Test
    void calculateCaptainCount_categoryAndMinTeamSize_minTeamSizeIsBottleneck() {
        CourseCategory piano = CourseCategory.builder().id(UUID.randomUUID()).title("Piano").build();
        TeamRequirementTemplate template = TeamRequirementTemplate.builder()
                .requiredCategory(piano)
                .minTeamSize(6)
                .build();
        
        List<CourseMember> students = makeStudentsWithCategory(3, 7, piano);
        int count = postCaptainService.calculateCaptainCount(students, template);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void calculateCaptainCount_withRequiredCategory_noCategoryStudents_returns1() {
        CourseCategory piano = CourseCategory.builder().id(UUID.randomUUID()).title("Piano").build();
        TeamRequirementTemplate template = TeamRequirementTemplate.builder()
                .requiredCategory(piano)
                .build();
        
        List<CourseMember> students = makeStudentsWithCategory(0, 10, piano);
        int count = postCaptainService.calculateCaptainCount(students, template);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void selectCaptains_invalidMode_throwsException() {
        post.setTeamFormationMode(TeamFormationMode.FREE);

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.existsByCourseIdAndUserIdAndRole(courseId, userId, CourseRole.TEACHER)).thenReturn(true);

        SelectCaptainsRequest request = SelectCaptainsRequest.builder().build();

        assertThatThrownBy(() -> postCaptainService.selectCaptains(courseId, postId, request, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Captain selection is only available for CAPTAIN_SELECTION mode");
    }

    @Test
    void selectCaptains_noStudents_throwsException() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(courseMemberRepository.existsByCourseIdAndUserIdAndRole(courseId, userId, CourseRole.TEACHER)).thenReturn(true);
        when(courseMemberRepository.findByCourseIdAndRoleOrderByJoinedAtAsc(courseId, CourseRole.STUDENT))
                .thenReturn(List.of());

        SelectCaptainsRequest request = SelectCaptainsRequest.builder().build();

        assertThatThrownBy(() -> postCaptainService.selectCaptains(courseId, postId, request, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("No students available for captain selection");
    }
}