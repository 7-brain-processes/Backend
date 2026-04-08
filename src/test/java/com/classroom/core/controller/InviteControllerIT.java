package com.classroom.core.controller;

import com.classroom.core.TestcontainersConfig;
import com.classroom.core.dto.auth.AuthResponse;
import com.classroom.core.dto.auth.RegisterRequest;
import com.classroom.core.dto.course.CourseDto;
import com.classroom.core.dto.invite.CreateInviteRequest;
import com.classroom.core.dto.invite.InviteDto;
import com.classroom.core.model.Course;
import com.classroom.core.model.CourseMember;
import com.classroom.core.model.CourseRole;
import com.classroom.core.model.Invite;
import com.classroom.core.model.User;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
import com.classroom.core.repository.InviteRepository;
import com.classroom.core.repository.UserRepository;
import com.classroom.core.support.TestDatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class InviteControllerIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseMemberRepository courseMemberRepository;

    @Autowired
    private InviteRepository inviteRepository;

    @Autowired
    private TestDatabaseCleaner testDatabaseCleaner;

    @BeforeEach
    void setUp() {
        testDatabaseCleaner.clean();
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

    private HttpEntity<CreateInviteRequest> authorizedCreateInviteRequest(
            String token,
            CourseRole role,
            Instant expiresAt,
            Integer maxUses
    ) {
        CreateInviteRequest request = new CreateInviteRequest();
        request.setRole(role);
        request.setExpiresAt(expiresAt);
        request.setMaxUses(maxUses);

        HttpHeaders headers = bearerHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        return new HttpEntity<>(request, headers);
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

    private Invite createInviteEntity(
            Course course,
            String code,
            CourseRole role,
            Instant expiresAt,
            Integer maxUses,
            int currentUses
    ) {
        return inviteRepository.save(
                Invite.builder()
                        .course(course)
                        .code(code)
                        .role(role)
                        .expiresAt(expiresAt)
                        .maxUses(maxUses)
                        .currentUses(currentUses)
                        .build()
        );
    }

    @Nested
    class ListInvites {

        @Test
        void returns200_andInvites_whenTeacherRequests() {
            String token = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            createInviteEntity(course, "ABC12345", CourseRole.STUDENT, null, 10, 0);

            ResponseEntity<InviteDto[]> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/invites",
                    HttpMethod.GET,
                    authorizedRequest(token),
                    InviteDto[].class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody()[0].getCode()).isEqualTo("ABC12345");
        }

        @Test
        void returns403_whenStudentRequestsInvites() {
            String token = registerAndGetToken("student1", "password123");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, student, CourseRole.STUDENT);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/invites",
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
                    "/api/v1/courses/" + UUID.randomUUID() + "/invites",
                    HttpMethod.GET,
                    authorizedRequest(token),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void returns401_whenNoTokenProvided_forListInvites() {
            Course course = createCourseEntity("Java", "Course");

            ResponseEntity<String> response = restTemplate.getForEntity(
                    "/api/v1/courses/" + course.getId() + "/invites",
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    class CreateInvite {

        @Test
        void returns201_andInvite_whenTeacherCreatesInvite() {
            String token = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);

            ResponseEntity<InviteDto> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/invites",
                    HttpMethod.POST,
                    authorizedCreateInviteRequest(token, CourseRole.STUDENT, null, 5),
                    InviteDto.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isNotBlank();
            assertThat(response.getBody().getRole()).isEqualTo(CourseRole.STUDENT);
            assertThat(response.getBody().getMaxUses()).isEqualTo(5);
        }

        @Test
        void returns403_whenStudentCreatesInvite() {
            String token = registerAndGetToken("student1", "password123");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, student, CourseRole.STUDENT);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/invites",
                    HttpMethod.POST,
                    authorizedCreateInviteRequest(token, CourseRole.STUDENT, null, 5),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns400_whenRequestInvalid() {
            String token = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);

            CreateInviteRequest request = new CreateInviteRequest();
            request.setRole(null);
            request.setMaxUses(0);

            HttpHeaders headers = bearerHeaders(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/invites",
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void returns401_whenNoTokenProvided_forCreateInvite() {
            Course course = createCourseEntity("Java", "Course");

            CreateInviteRequest request = new CreateInviteRequest();
            request.setRole(CourseRole.STUDENT);
            request.setMaxUses(5);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/invites",
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    class RevokeInvite {

        @Test
        void returns204_andDeletesInvite_whenTeacherRevokesInvite() {
            String token = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            Invite invite = createInviteEntity(course, "ABC12345", CourseRole.STUDENT, null, 10, 0);

            ResponseEntity<Void> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/invites/" + invite.getId(),
                    HttpMethod.DELETE,
                    authorizedRequest(token),
                    Void.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(inviteRepository.findById(invite.getId())).isEmpty();
        }

        @Test
        void returns403_whenStudentRevokesInvite() {
            String token = registerAndGetToken("student1", "password123");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, student, CourseRole.STUDENT);
            Invite invite = createInviteEntity(course, "ABC12345", CourseRole.STUDENT, null, 10, 0);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/invites/" + invite.getId(),
                    HttpMethod.DELETE,
                    authorizedRequest(token),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns404_whenInviteDoesNotExist() {
            String token = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/invites/" + UUID.randomUUID(),
                    HttpMethod.DELETE,
                    authorizedRequest(token),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void returns401_whenNoTokenProvided_forRevokeInvite() {
            registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            Invite invite = createInviteEntity(course, "ABC12345", CourseRole.STUDENT, null, 10, 0);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/invites/" + invite.getId(),
                    HttpMethod.DELETE,
                    HttpEntity.EMPTY,
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    class JoinCourse {

        @Test
        void returns200_andAddsUserToCourse() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            String studentToken = registerAndGetToken("student1", "password123");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            Invite invite = createInviteEntity(course, "JOIN1234", CourseRole.STUDENT, null, 10, 0);

            ResponseEntity<CourseDto> response = restTemplate.exchange(
                    "/api/v1/invites/" + invite.getCode() + "/join",
                    HttpMethod.POST,
                    authorizedRequest(studentToken),
                    CourseDto.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isEqualTo(course.getId());
            assertThat(courseMemberRepository.findByCourseIdAndUserId(course.getId(), student.getId())).isPresent();
        }

        @Test
        void incrementsInviteCurrentUses() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            String studentToken = registerAndGetToken("student1", "password123");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            Invite invite = createInviteEntity(course, "JOIN1234", CourseRole.STUDENT, null, 10, 2);

            restTemplate.exchange(
                    "/api/v1/invites/" + invite.getCode() + "/join",
                    HttpMethod.POST,
                    authorizedRequest(studentToken),
                    CourseDto.class
            );

            Invite updated = inviteRepository.findById(invite.getId()).orElseThrow();
            assertThat(updated.getCurrentUses()).isEqualTo(3);
        }

        @Test
        void returns404_whenInviteCodeInvalid() {
            String studentToken = registerAndGetToken("student1", "password123");

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/invites/INVALID123/join",
                    HttpMethod.POST,
                    authorizedRequest(studentToken),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void returns409_whenAlreadyMember() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            String studentToken = registerAndGetToken("student1", "password123");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            addMember(course, student, CourseRole.STUDENT);
            Invite invite = createInviteEntity(course, "JOIN1234", CourseRole.STUDENT, null, 10, 0);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/invites/" + invite.getCode() + "/join",
                    HttpMethod.POST,
                    authorizedRequest(studentToken),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void returns404_whenInviteExpired() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            String studentToken = registerAndGetToken("student1", "password123");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            Invite invite = createInviteEntity(
                    course,
                    "JOIN1234",
                    CourseRole.STUDENT,
                    Instant.now().minusSeconds(3600),
                    10,
                    0
            );

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/invites/" + invite.getCode() + "/join",
                    HttpMethod.POST,
                    authorizedRequest(studentToken),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void returns404_whenMaxUsesReached() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            String studentToken = registerAndGetToken("student1", "password123");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            Invite invite = createInviteEntity(course, "JOIN1234", CourseRole.STUDENT, null, 2, 2);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/invites/" + invite.getCode() + "/join",
                    HttpMethod.POST,
                    authorizedRequest(studentToken),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void returns401_whenNoTokenProvided_forJoinCourse() {
            Course course = createCourseEntity("Java", "Course");
            Invite invite = createInviteEntity(course, "JOIN1234", CourseRole.STUDENT, null, 10, 0);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/invites/" + invite.getCode() + "/join",
                    HttpMethod.POST,
                    HttpEntity.EMPTY,
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}