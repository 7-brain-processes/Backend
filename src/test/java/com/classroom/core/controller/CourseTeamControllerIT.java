package com.classroom.core.controller;

import com.classroom.core.TestcontainersConfig;
import com.classroom.core.dto.auth.AuthResponse;
import com.classroom.core.dto.auth.RegisterRequest;
import com.classroom.core.dto.team.CourseTeamDto;
import com.classroom.core.dto.team.CreateCourseTeamRequest;
import com.classroom.core.model.Course;
import com.classroom.core.model.CourseMember;
import com.classroom.core.model.CourseRole;
import com.classroom.core.model.CourseTeam;
import com.classroom.core.model.User;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
import com.classroom.core.repository.CourseTeamRepository;
import com.classroom.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class CourseTeamControllerIT {

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

    @BeforeEach
    void setUp() {
        courseMemberRepository.deleteAll();
        courseTeamRepository.deleteAll();
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

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpEntity<CreateCourseTeamRequest> authorizedCreateRequest(String token, String name, List<UUID> memberIds) {
        CreateCourseTeamRequest request = new CreateCourseTeamRequest();
        request.setName(name);
        request.setMemberIds(memberIds);

        HttpHeaders headers = bearerHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        return new HttpEntity<>(request, headers);
    }

    private HttpEntity<Void> authorizedRequest(String token) {
        return new HttpEntity<>(bearerHeaders(token));
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

    private CourseTeam createTeam(Course course, String name) {
        return courseTeamRepository.save(
                CourseTeam.builder()
                        .course(course)
                        .name(name)
                        .build()
        );
    }

    @Nested
    class CreateCourseTeam {

        @Test
        void returns201_andAssignsStudentsToTeam() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            registerAndGetToken("student1", "password123");
            registerAndGetToken("student2", "password123");

            User teacher = userByUsername("teacher1");
            User student1 = userByUsername("student1");
            User student2 = userByUsername("student2");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            addMember(course, student1, CourseRole.STUDENT);
            addMember(course, student2, CourseRole.STUDENT);

            ResponseEntity<CourseTeamDto> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/teams",
                    HttpMethod.POST,
                    authorizedCreateRequest(teacherToken, "Team A", List.of(student1.getId(), student2.getId())),
                    CourseTeamDto.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isNotNull();
            assertThat(response.getBody().getName()).isEqualTo("Team A");
            assertThat(response.getBody().getMembersCount()).isEqualTo(2);
            assertThat(response.getBody().getMembers()).hasSize(2);

            UUID teamId = response.getBody().getId();

            CourseMember student1Membership = courseMemberRepository
                    .findByCourseIdAndUserId(course.getId(), student1.getId())
                    .orElseThrow();
            CourseMember student2Membership = courseMemberRepository
                    .findByCourseIdAndUserId(course.getId(), student2.getId())
                    .orElseThrow();

            assertThat(student1Membership.getTeam()).isNotNull();
            assertThat(student2Membership.getTeam()).isNotNull();
            assertThat(student1Membership.getTeam().getId()).isEqualTo(teamId);
            assertThat(student2Membership.getTeam().getId()).isEqualTo(teamId);
        }

        @Test
        void returns403_whenStudentTriesToCreateTeam() {
            String studentToken = registerAndGetToken("student1", "password123");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, student, CourseRole.STUDENT);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/teams",
                    HttpMethod.POST,
                    authorizedCreateRequest(studentToken, "Team A", List.of()),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns404_whenMemberIsNotInCourse() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            registerAndGetToken("student1", "password123");
            registerAndGetToken("outsider", "password123");

            User teacher = userByUsername("teacher1");
            User student = userByUsername("student1");
            User outsider = userByUsername("outsider");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            addMember(course, student, CourseRole.STUDENT);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/teams",
                    HttpMethod.POST,
                    authorizedCreateRequest(teacherToken, "Team A", List.of(student.getId(), outsider.getId())),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void returns409_whenStudentAlreadyAssignedToAnotherTeam() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            registerAndGetToken("student1", "password123");

            User teacher = userByUsername("teacher1");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            CourseMember studentMember = addMember(course, student, CourseRole.STUDENT);

            CourseTeam existingTeam = createTeam(course, "Existing Team");
            studentMember.setTeam(existingTeam);
            courseMemberRepository.save(studentMember);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/teams",
                    HttpMethod.POST,
                    authorizedCreateRequest(teacherToken, "Team B", List.of(student.getId())),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Nested
    class ListCourseTeams {

        @Test
        void returns200_andTeamsForCourseMember() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            registerAndGetToken("student1", "password123");

            User teacher = userByUsername("teacher1");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            CourseMember studentMember = addMember(course, student, CourseRole.STUDENT);

            CourseTeam team = createTeam(course, "Team A");
            studentMember.setTeam(team);
            courseMemberRepository.save(studentMember);

            ResponseEntity<List<CourseTeamDto>> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/teams",
                    HttpMethod.GET,
                    authorizedRequest(teacherToken),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).getName()).isEqualTo("Team A");
            assertThat(response.getBody().get(0).getMembersCount()).isEqualTo(1);
        }

        @Test
        void returns403_whenCurrentUserIsNotMember() {
            String outsiderToken = registerAndGetToken("outsider", "password123");
            registerAndGetToken("teacher1", "password123");

            User teacher = userByUsername("teacher1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/teams",
                    HttpMethod.GET,
                    authorizedRequest(outsiderToken),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
