package com.classroom.core.controller;

import com.classroom.core.TestcontainersConfig;
import com.classroom.core.dto.auth.AuthResponse;
import com.classroom.core.dto.auth.RegisterRequest;
import com.classroom.core.dto.team.TeamGradeDistributionDto;
import com.classroom.core.dto.team.TeamGradeDto;
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
import com.classroom.core.repository.TeamStudentGradeRepository;
import com.classroom.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class TeamGradeControllerIT {

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

    private CourseTeam createPostTeam(Course course, Post post, String name, Integer maxSize) {
        return courseTeamRepository.save(
                CourseTeam.builder()
                        .course(course)
                        .post(post)
                        .name(name)
                        .maxSize(maxSize)
                        .selfEnrollmentEnabled(false)
                        .build()
        );
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpEntity<Map<String, Object>> gradeRequest(String token, int grade, String comment) {
        HttpHeaders headers = bearerHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(Map.of("grade", grade, "comment", comment), headers);
    }

    private HttpEntity<Map<String, String>> distributionModeRequest(String token, String mode) {
        HttpHeaders headers = bearerHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(Map.of("distributionMode", mode), headers);
    }

    private HttpEntity<Void> authorizedRequest(String token) {
        return new HttpEntity<>(bearerHeaders(token));
    }

    @Nested
    class TeamGradeCrud {

        @Test
        void canSetAndUpdateTeamGrade() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            registerAndGetToken("student1", "password123");
            registerAndGetToken("student2", "password123");

            User teacher = userByUsername("teacher1");
            User student1 = userByUsername("student1");
            User student2 = userByUsername("student2");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            CourseMember m1 = addMember(course, student1, CourseRole.STUDENT);
            CourseMember m2 = addMember(course, student2, CourseRole.STUDENT);

            Post post = createTaskPost(course, teacher, "Task 1");
            CourseTeam team = createPostTeam(course, post, "Team A", 2);
            m1.setTeam(team);
            m2.setTeam(team);
            courseMemberRepository.saveAll(List.of(m1, m2));

            String base = "/api/v1/courses/" + course.getId() + "/posts/" + post.getId() + "/teams/" + team.getId() + "/grade";

            ResponseEntity<TeamGradeDto> createResponse = restTemplate.exchange(
                    base,
                    HttpMethod.PUT,
                    gradeRequest(teacherToken, 80, "initial"),
                    TeamGradeDto.class
            );

            assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(createResponse.getBody()).isNotNull();
            assertThat(createResponse.getBody().getGrade()).isEqualTo(80);

            ResponseEntity<TeamGradeDto> updateResponse = restTemplate.exchange(
                    base,
                    HttpMethod.PUT,
                    gradeRequest(teacherToken, 92, "updated"),
                    TeamGradeDto.class
            );

            assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(updateResponse.getBody()).isNotNull();
            assertThat(updateResponse.getBody().getGrade()).isEqualTo(92);
            assertThat(updateResponse.getBody().getComment()).isEqualTo("updated");
        }
    }

    @Nested
    class AutomaticDistribution {

        @Test
        void storesIndividualGradesForAutoDistribution() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            registerAndGetToken("student1", "password123");
            registerAndGetToken("student2", "password123");
            registerAndGetToken("student3", "password123");

            User teacher = userByUsername("teacher1");
            User student1 = userByUsername("student1");
            User student2 = userByUsername("student2");
            User student3 = userByUsername("student3");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            CourseMember m1 = addMember(course, student1, CourseRole.STUDENT);
            CourseMember m2 = addMember(course, student2, CourseRole.STUDENT);
            CourseMember m3 = addMember(course, student3, CourseRole.STUDENT);

            Post post = createTaskPost(course, teacher, "Task 1");
            CourseTeam team = createPostTeam(course, post, "Team A", 3);
            m1.setTeam(team);
            m2.setTeam(team);
            m3.setTeam(team);
            courseMemberRepository.saveAll(List.of(m1, m2, m3));

            String base = "/api/v1/courses/" + course.getId() + "/posts/" + post.getId() + "/teams/" + team.getId() + "/grade";

            ResponseEntity<TeamGradeDto> setGrade = restTemplate.exchange(
                    base,
                    HttpMethod.PUT,
                    gradeRequest(teacherToken, 100, "grade"),
                    TeamGradeDto.class
            );
            assertThat(setGrade.getStatusCode()).isEqualTo(HttpStatus.OK);

            ResponseEntity<TeamGradeDistributionDto> setMode = restTemplate.exchange(
                    base + "/distribution",
                    HttpMethod.PUT,
                    distributionModeRequest(teacherToken, "AUTO_EQUAL"),
                    TeamGradeDistributionDto.class
            );
            assertThat(setMode.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(setMode.getBody()).isNotNull();
            assertThat(setMode.getBody().getStudents()).hasSize(3);

            List<TeamStudentGrade> persisted = teamStudentGradeRepository.findAll();
            assertThat(persisted).hasSize(3);
            assertThat(persisted.stream().map(TeamStudentGrade::getGrade).reduce(0, Integer::sum)).isEqualTo(100);
        }

        @Test
        void recalculatesStoredIndividualGradesWhenTeamGradeChanges() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            registerAndGetToken("student1", "password123");
            registerAndGetToken("student2", "password123");
            registerAndGetToken("student3", "password123");

            User teacher = userByUsername("teacher1");
            User student1 = userByUsername("student1");
            User student2 = userByUsername("student2");
            User student3 = userByUsername("student3");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            CourseMember m1 = addMember(course, student1, CourseRole.STUDENT);
            CourseMember m2 = addMember(course, student2, CourseRole.STUDENT);
            CourseMember m3 = addMember(course, student3, CourseRole.STUDENT);

            Post post = createTaskPost(course, teacher, "Task 1");
            CourseTeam team = createPostTeam(course, post, "Team A", 3);
            m1.setTeam(team);
            m2.setTeam(team);
            m3.setTeam(team);
            courseMemberRepository.saveAll(List.of(m1, m2, m3));

            String base = "/api/v1/courses/" + course.getId() + "/posts/" + post.getId() + "/teams/" + team.getId() + "/grade";

            restTemplate.exchange(base, HttpMethod.PUT, gradeRequest(teacherToken, 100, "grade"), TeamGradeDto.class);
            restTemplate.exchange(base + "/distribution", HttpMethod.PUT,
                    distributionModeRequest(teacherToken, "AUTO_EQUAL"), TeamGradeDistributionDto.class);

            int initialSum = teamStudentGradeRepository.findAll().stream()
                    .map(TeamStudentGrade::getGrade)
                    .reduce(0, Integer::sum);
            assertThat(initialSum).isEqualTo(100);

                ResponseEntity<TeamGradeDto> updateGradeResponse = restTemplate.exchange(
                    base,
                    HttpMethod.PUT,
                    gradeRequest(teacherToken, 97, "recalculated"),
                    TeamGradeDto.class
                );

                assertThat(updateGradeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(updateGradeResponse.getBody()).isNotNull();
                assertThat(updateGradeResponse.getBody().getGrade()).isEqualTo(97);

                TeamGrade persistedTeamGrade = teamGradeRepository
                    .findByPostIdAndTeamId(post.getId(), team.getId())
                    .orElseThrow();
                assertThat(persistedTeamGrade.getGrade()).isEqualTo(97);
                assertThat(persistedTeamGrade.getDistributionMode()).isEqualTo(TeamGradeDistributionMode.AUTO_EQUAL);

            ResponseEntity<TeamGradeDistributionDto> distributionResponse = restTemplate.exchange(
                    base + "/distribution",
                    HttpMethod.GET,
                    authorizedRequest(teacherToken),
                    TeamGradeDistributionDto.class
            );

            assertThat(distributionResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(distributionResponse.getBody()).isNotNull();
            assertThat(distributionResponse.getBody().getStudents()).hasSize(3);

            int recalculatedSum = teamStudentGradeRepository.findAll().stream()
                    .map(TeamStudentGrade::getGrade)
                    .reduce(0, Integer::sum);
            assertThat(recalculatedSum).isEqualTo(97);
        }
    }
}
