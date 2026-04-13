package com.classroom.core.controller;

import com.classroom.core.TestcontainersConfig;
import com.classroom.core.dto.auth.AuthResponse;
import com.classroom.core.dto.auth.RegisterRequest;
import com.classroom.core.dto.team.AutoFormationStudentDto;
import com.classroom.core.dto.team.AutoTeamFormationRequest;
import com.classroom.core.dto.team.AutoTeamFormationResultDto;
import com.classroom.core.dto.team.TeamFormationModeDto;
import com.classroom.core.model.Course;
import com.classroom.core.model.CourseCategory;
import com.classroom.core.model.CourseMember;
import com.classroom.core.model.CourseRole;
import com.classroom.core.model.CourseTeam;
import com.classroom.core.model.Post;
import com.classroom.core.model.PostType;
import com.classroom.core.model.TeamFormationMode;
import com.classroom.core.model.User;
import com.classroom.core.repository.CourseCategoryRepository;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
import com.classroom.core.repository.CourseTeamRepository;
import com.classroom.core.repository.PostRepository;
import com.classroom.core.repository.TeamGradeRepository;
import com.classroom.core.repository.TeamStudentGradeRepository;
import com.classroom.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class TeamFormationControllerIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseMemberRepository courseMemberRepository;

    @Autowired
    private CourseTeamRepository courseTeamRepository;

    @Autowired
    private CourseCategoryRepository courseCategoryRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private TeamGradeRepository teamGradeRepository;

    @Autowired
    private TeamStudentGradeRepository teamStudentGradeRepository;

    @BeforeEach
    void setUp() {
        teamStudentGradeRepository.deleteAll();
        teamGradeRepository.deleteAll();
        courseMemberRepository.deleteAll();
        courseTeamRepository.deleteAll();
        postRepository.deleteAll();
        courseCategoryRepository.deleteAll();
        courseRepository.deleteAll();
        userRepository.deleteAll();
    }

    private RegisterRequest registerRequest(String username, String password, String displayName) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setPassword(password);
        req.setDisplayName(displayName);
        return req;
    }

    private String registerAndGetToken(String username, String password) {
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/register",
                registerRequest(username, password, username),
                AuthResponse.class
        );
        return response.getBody().getToken();
    }

    private User userByUsername(String username) {
        return userRepository.findByUsername(username).orElseThrow();
    }

    private Course createCourseEntity(String name, String description) {
        return courseRepository.save(
                Course.builder()
                        .name(name)
                        .description(description)
                        .build()
        );
    }

    private CourseMember addMember(Course course, User user, CourseRole role) {
        return courseMemberRepository.save(
                CourseMember.builder()
                        .course(course)
                        .user(user)
                        .role(role)
                        .build()
        );
    }

    private Post createTaskPost(Course course, User teacher, String title) {
        return postRepository.save(
                Post.builder()
                        .course(course)
                        .author(teacher)
                        .title(title)
                        .content("content")
                        .type(PostType.TASK)
                        .build()
        );
    }

    private CourseCategory createCategory(Course course, String title) {
        return courseCategoryRepository.save(
                CourseCategory.builder()
                        .course(course)
                        .title(title)
                        .description("desc")
                        .active(true)
                        .build()
        );
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpEntity<Void> authorizedRequest(String token) {
        return new HttpEntity<>(bearerHeaders(token));
    }

    private HttpEntity<AutoTeamFormationRequest> autoFormationRequest(String token,
                                                                       Integer minSize,
                                                                       Integer maxSize,
                                                                       boolean balanceByCategory,
                                                                       boolean balanceByRole,
                                                                       boolean reshuffle) {
        AutoTeamFormationRequest request = new AutoTeamFormationRequest();
        request.setMinTeamSize(minSize);
        request.setMaxTeamSize(maxSize);
        request.setBalanceByCategory(balanceByCategory);
        request.setBalanceByRole(balanceByRole);
        request.setReshuffle(reshuffle);

        HttpHeaders headers = bearerHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(request, headers);
    }

    private HttpEntity<Map<String, String>> modeRequest(String token, TeamFormationMode mode) {
        HttpHeaders headers = bearerHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(Map.of("mode", mode.name()), headers);
    }

    @Nested
    class ModeEndpoints {

        @Test
        void setAndGetTeamFormationMode() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            Post post = createTaskPost(course, teacher, "Task 1");

            ResponseEntity<TeamFormationModeDto> setResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId() + "/team-formation/mode",
                    HttpMethod.PUT,
                    modeRequest(teacherToken, TeamFormationMode.RANDOM_SHUFFLE),
                    TeamFormationModeDto.class
            );

            assertThat(setResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(setResponse.getBody()).isNotNull();
            assertThat(setResponse.getBody().getMode()).isEqualTo(TeamFormationMode.RANDOM_SHUFFLE);

            ResponseEntity<TeamFormationModeDto> getResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId() + "/team-formation/mode",
                    HttpMethod.GET,
                    authorizedRequest(teacherToken),
                    TeamFormationModeDto.class
            );

            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getResponse.getBody()).isNotNull();
            assertThat(getResponse.getBody().getMode()).isEqualTo(TeamFormationMode.RANDOM_SHUFFLE);
        }
    }

    @Nested
    class AutoFormation {

        @Test
        void formsTeamsWithMinMaxAndPersistsResult() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            registerAndGetToken("student1", "password123");
            registerAndGetToken("student2", "password123");
            registerAndGetToken("student3", "password123");
            registerAndGetToken("student4", "password123");
            registerAndGetToken("student5", "password123");

            User teacher = userByUsername("teacher1");
            User s1 = userByUsername("student1");
            User s2 = userByUsername("student2");
            User s3 = userByUsername("student3");
            User s4 = userByUsername("student4");
            User s5 = userByUsername("student5");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            addMember(course, s1, CourseRole.STUDENT);
            addMember(course, s2, CourseRole.STUDENT);
            addMember(course, s3, CourseRole.STUDENT);
            addMember(course, s4, CourseRole.STUDENT);
            addMember(course, s5, CourseRole.STUDENT);
            Post post = createTaskPost(course, teacher, "Task 1");

            ResponseEntity<AutoTeamFormationResultDto> runResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId() + "/team-formation/auto",
                    HttpMethod.POST,
                    autoFormationRequest(teacherToken, 2, 3, false, false, false),
                    AutoTeamFormationResultDto.class
            );

            assertThat(runResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(runResponse.getBody()).isNotNull();
            assertThat(runResponse.getBody().getFormedTeams()).isEqualTo(2);
            assertThat(runResponse.getBody().getAssignedStudents()).isEqualTo(5);
            assertThat(runResponse.getBody().getUnassignedStudents()).isEqualTo(0);

            List<CourseTeam> postTeams = courseTeamRepository.findByPostId(post.getId());
            assertThat(postTeams).hasSize(2);

            List<Integer> sizes = postTeams.stream()
                    .map(team -> courseMemberRepository.countByTeamId(team.getId()))
                    .toList();
            assertThat(sizes).allSatisfy(size -> assertThat(size).isBetween(2, 3));

            ResponseEntity<AutoTeamFormationResultDto> resultResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId() + "/team-formation/auto/result",
                    HttpMethod.GET,
                    authorizedRequest(teacherToken),
                    AutoTeamFormationResultDto.class
            );

            assertThat(resultResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resultResponse.getBody()).isNotNull();
            assertThat(resultResponse.getBody().getFormedTeams()).isEqualTo(2);
            assertThat(resultResponse.getBody().getAssignedStudents()).isEqualTo(5);
            assertThat(resultResponse.getBody().getUnassignedStudents()).isEqualTo(0);
        }

        @Test
        void listAvailableStudentsExcludesTeacherAndAssignedMembers() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            registerAndGetToken("student1", "password123");
            registerAndGetToken("student2", "password123");

            User teacher = userByUsername("teacher1");
            User s1 = userByUsername("student1");
            User s2 = userByUsername("student2");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            addMember(course, s1, CourseRole.STUDENT);
            addMember(course, s2, CourseRole.STUDENT);
            Post post = createTaskPost(course, teacher, "Task 1");

            ResponseEntity<List<AutoFormationStudentDto>> before = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId() + "/team-formation/auto/students",
                    HttpMethod.GET,
                    authorizedRequest(teacherToken),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(before.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(before.getBody()).isNotNull();
            assertThat(before.getBody()).hasSize(2);
            assertThat(before.getBody()).extracting(AutoFormationStudentDto::getUsername)
                    .containsExactlyInAnyOrder("student1", "student2");

            ResponseEntity<AutoTeamFormationResultDto> runResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId() + "/team-formation/auto",
                    HttpMethod.POST,
                    autoFormationRequest(teacherToken, 1, 2, false, true, false),
                    AutoTeamFormationResultDto.class
            );
            assertThat(runResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

            ResponseEntity<List<AutoFormationStudentDto>> after = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId() + "/team-formation/auto/students",
                    HttpMethod.GET,
                    authorizedRequest(teacherToken),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(after.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(after.getBody()).isNotNull();
            assertThat(after.getBody()).isEmpty();
        }

        @Test
        void supportsCategoryBalancingAndReshuffleProtection() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            registerAndGetToken("student1", "password123");
            registerAndGetToken("student2", "password123");
            registerAndGetToken("student3", "password123");
            registerAndGetToken("student4", "password123");

            User teacher = userByUsername("teacher1");
            User s1 = userByUsername("student1");
            User s2 = userByUsername("student2");
            User s3 = userByUsername("student3");
            User s4 = userByUsername("student4");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            CourseMember m1 = addMember(course, s1, CourseRole.STUDENT);
            CourseMember m2 = addMember(course, s2, CourseRole.STUDENT);
            CourseMember m3 = addMember(course, s3, CourseRole.STUDENT);
            CourseMember m4 = addMember(course, s4, CourseRole.STUDENT);

            CourseCategory categoryA = createCategory(course, "A");
            CourseCategory categoryB = createCategory(course, "B");

            m1.setCategory(categoryA);
            m2.setCategory(categoryA);
            m3.setCategory(categoryB);
            m4.setCategory(categoryB);
            courseMemberRepository.saveAll(List.of(m1, m2, m3, m4));

            Post post = createTaskPost(course, teacher, "Task 1");

            ResponseEntity<AutoTeamFormationResultDto> firstRun = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId() + "/team-formation/auto",
                    HttpMethod.POST,
                    autoFormationRequest(teacherToken, 2, 2, true, false, false),
                    AutoTeamFormationResultDto.class
            );
            assertThat(firstRun.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

            List<CourseTeam> teams = courseTeamRepository.findByPostId(post.getId());
            assertThat(teams).hasSize(2);

            for (CourseTeam team : teams) {
                List<CourseMember> members = courseMemberRepository
                        .findByCourseIdAndTeamIdOrderByJoinedAtAsc(course.getId(), team.getId());
                Set<UUID> categories = members.stream()
                        .map(CourseMember::getCategory)
                        .map(CourseCategory::getId)
                        .collect(Collectors.toSet());
                assertThat(categories).containsExactlyInAnyOrder(categoryA.getId(), categoryB.getId());
            }

            ResponseEntity<String> secondRunNoReshuffle = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId() + "/team-formation/auto",
                    HttpMethod.POST,
                    autoFormationRequest(teacherToken, 2, 2, true, false, false),
                    String.class
            );
            assertThat(secondRunNoReshuffle.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            ResponseEntity<AutoTeamFormationResultDto> reshuffledRun = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId() + "/team-formation/auto",
                    HttpMethod.POST,
                    autoFormationRequest(teacherToken, 2, 2, true, false, true),
                    AutoTeamFormationResultDto.class
            );
            assertThat(reshuffledRun.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(courseTeamRepository.findByPostId(post.getId())).hasSize(2);
        }
    }
}
