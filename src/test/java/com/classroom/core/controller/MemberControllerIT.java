package com.classroom.core.controller;

import com.classroom.core.TestcontainersConfig;
import com.classroom.core.dto.PageDto;
import com.classroom.core.dto.auth.AuthResponse;
import com.classroom.core.dto.auth.RegisterRequest;
import com.classroom.core.dto.member.MemberDto;
import com.classroom.core.model.Course;
import com.classroom.core.model.CourseMember;
import com.classroom.core.model.CourseRole;
import com.classroom.core.model.User;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
import com.classroom.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class MemberControllerIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseMemberRepository courseMemberRepository;

    @BeforeEach
    void setUp() {
        courseMemberRepository.deleteAll();
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

    @Nested
    class ListMembers {

        @Test
        void returns200_andMembersForCourse_whenCurrentUserIsMember() {
            String token = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            registerAndGetToken("student1", "password123");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            addMember(course, student, CourseRole.STUDENT);

            ResponseEntity<PageDto<MemberDto>> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/members",
                    HttpMethod.GET,
                    authorizedRequest(token),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getContent()).hasSize(2);
        }

        @Test
        void filtersByRole() {
            String token = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            registerAndGetToken("student1", "password123");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            addMember(course, student, CourseRole.STUDENT);

            ResponseEntity<PageDto<MemberDto>> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/members?role=STUDENT",
                    HttpMethod.GET,
                    authorizedRequest(token),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getContent()).hasSize(1);
            assertThat(response.getBody().getContent().get(0).getRole()).isEqualTo(CourseRole.STUDENT);
        }

        @Test
        void supportsPagination() {
            String token = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            registerAndGetToken("student1", "password123");
            User student1 = userByUsername("student1");

            registerAndGetToken("student2", "password123");
            User student2 = userByUsername("student2");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            addMember(course, student1, CourseRole.STUDENT);
            addMember(course, student2, CourseRole.STUDENT);

            ResponseEntity<PageDto<MemberDto>> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/members?page=0&size=2",
                    HttpMethod.GET,
                    authorizedRequest(token),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getContent()).hasSize(2);
            assertThat(response.getBody().getPage()).isEqualTo(0);
            assertThat(response.getBody().getSize()).isEqualTo(2);
            assertThat(response.getBody().getTotalElements()).isEqualTo(3);
            assertThat(response.getBody().getTotalPages()).isEqualTo(2);
        }

        @Test
        void returns403_whenCurrentUserIsNotMember() {
            String token = registerAndGetToken("outsider", "password123");

            Course course = createCourseEntity("Java", "Course");

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/members",
                    HttpMethod.GET,
                    authorizedRequest(token),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns404_whenCourseDoesNotExist() {
            String token = registerAndGetToken("teacher1", "password123");

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + UUID.randomUUID() + "/members",
                    HttpMethod.GET,
                    authorizedRequest(token),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void returns403_whenNoTokenProvided_forListMembers() {
            Course course = createCourseEntity("Java", "Course");

            ResponseEntity<String> response = restTemplate.getForEntity(
                    "/api/v1/courses/" + course.getId() + "/members",
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    class RemoveMember {

        @Test
        void returns204_andRemovesMember_whenCallerIsTeacher() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            registerAndGetToken("student1", "password123");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            addMember(course, student, CourseRole.STUDENT);

            ResponseEntity<Void> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/members/" + student.getId(),
                    HttpMethod.DELETE,
                    authorizedRequest(teacherToken),
                    Void.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(courseMemberRepository.findByCourseIdAndUserId(course.getId(), student.getId())).isEmpty();
        }

        @Test
        void returns403_whenCallerIsStudent() {
            String studentToken = registerAndGetToken("student1", "password123");
            User student = userByUsername("student1");

            registerAndGetToken("student2", "password123");
            User student2 = userByUsername("student2");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, student, CourseRole.STUDENT);
            addMember(course, student2, CourseRole.STUDENT);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/members/" + student2.getId(),
                    HttpMethod.DELETE,
                    authorizedRequest(studentToken),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns404_whenTargetMemberNotFound() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/members/" + UUID.randomUUID(),
                    HttpMethod.DELETE,
                    authorizedRequest(teacherToken),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void returns403_whenNoTokenProvided_forRemoveMember() {
            registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            registerAndGetToken("student1", "password123");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            addMember(course, student, CourseRole.STUDENT);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/members/" + student.getId(),
                    HttpMethod.DELETE,
                    HttpEntity.EMPTY,
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    class LeaveCourse {

        @Test
        void returns204_andRemovesCurrentUsersMembership() {
            String token = registerAndGetToken("student1", "password123");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, student, CourseRole.STUDENT);

            ResponseEntity<Void> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/leave",
                    HttpMethod.POST,
                    authorizedRequest(token),
                    Void.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(courseMemberRepository.findByCourseIdAndUserId(course.getId(), student.getId())).isEmpty();
        }

        @Test
        void returns404_whenUserIsNotMember() {
            String token = registerAndGetToken("student1", "password123");

            Course course = createCourseEntity("Java", "Course");

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/leave",
                    HttpMethod.POST,
                    authorizedRequest(token),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void returns404_whenCourseDoesNotExist() {
            String token = registerAndGetToken("student1", "password123");

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + UUID.randomUUID() + "/leave",
                    HttpMethod.POST,
                    authorizedRequest(token),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void returns403_whenNoTokenProvided_forLeaveCourse() {
            Course course = createCourseEntity("Java", "Course");

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/leave",
                    HttpMethod.POST,
                    HttpEntity.EMPTY,
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}