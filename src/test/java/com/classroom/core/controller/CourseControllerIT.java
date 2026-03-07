package com.classroom.core.controller;

import com.classroom.core.TestcontainersConfig;
import com.classroom.core.dto.PageDto;
import com.classroom.core.dto.auth.AuthResponse;
import com.classroom.core.dto.auth.RegisterRequest;
import com.classroom.core.dto.course.CourseDto;
import com.classroom.core.dto.course.CreateCourseRequest;
import com.classroom.core.dto.course.UpdateCourseRequest;
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
class CourseControllerIT {

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

    private HttpEntity<CreateCourseRequest> authorizedCreateRequest(String token, String name, String description) {
        CreateCourseRequest request = new CreateCourseRequest();
        request.setName(name);
        request.setDescription(description);

        HttpHeaders headers = bearerHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        return new HttpEntity<>(request, headers);
    }

    private HttpEntity<UpdateCourseRequest> authorizedUpdateRequest(String token, String name, String description) {
        UpdateCourseRequest request = new UpdateCourseRequest();
        request.setName(name);
        request.setDescription(description);

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

    @Nested
    class CreateCourse {

        @Test
        void returns201_andCreatedCourse_onValidRequest() {
            String token = registerAndGetToken("teacher1", "password123");

            ResponseEntity<CourseDto> response = restTemplate.exchange(
                    "/api/v1/courses",
                    HttpMethod.POST,
                    authorizedCreateRequest(token, "Java Core", "Intro to Java"),
                    CourseDto.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isNotNull();
            assertThat(response.getBody().getName()).isEqualTo("Java Core");
            assertThat(response.getBody().getDescription()).isEqualTo("Intro to Java");
            assertThat(response.getBody().getCurrentUserRole()).isEqualTo(CourseRole.TEACHER);
            assertThat(response.getBody().getTeacherCount()).isEqualTo(1);
            assertThat(response.getBody().getStudentCount()).isEqualTo(0);
        }

        @Test
        void persistsCourse_andTeacherMembership() {
            String token = registerAndGetToken("teacher1", "password123");

            ResponseEntity<CourseDto> response = restTemplate.exchange(
                    "/api/v1/courses",
                    HttpMethod.POST,
                    authorizedCreateRequest(token, "Spring", "REST API"),
                    CourseDto.class
            );

            UUID courseId = response.getBody().getId();
            User teacher = userByUsername("teacher1");

            assertThat(courseRepository.findById(courseId)).isPresent();
            assertThat(courseMemberRepository.findByCourseIdAndUserId(courseId, teacher.getId())).isPresent();
            assertThat(courseMemberRepository.findByCourseIdAndUserId(courseId, teacher.getId()).orElseThrow().getRole())
                    .isEqualTo(CourseRole.TEACHER);
        }

        @Test
        void returns400_whenNameIsBlank() {
            String token = registerAndGetToken("teacher1", "password123");

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses",
                    HttpMethod.POST,
                    authorizedCreateRequest(token, "", "desc"),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void returns401_whenNoTokenProvided() {
            CreateCourseRequest request = new CreateCourseRequest();
            request.setName("Java Core");
            request.setDescription("Intro");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses",
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    class GetCourse {

        @Test
        void returns200_whenCurrentUserIsMember() {
            String token = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            Course course = createCourseEntity("Algorithms", "Base course");
            addMember(course, teacher, CourseRole.TEACHER);

            ResponseEntity<CourseDto> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId(),
                    HttpMethod.GET,
                    authorizedRequest(token),
                    CourseDto.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isEqualTo(course.getId());
            assertThat(response.getBody().getName()).isEqualTo("Algorithms");
            assertThat(response.getBody().getCurrentUserRole()).isEqualTo(CourseRole.TEACHER);
        }

        @Test
        void returns403_whenCurrentUserIsNotMember() {
            String token = registerAndGetToken("user1", "password123");
            User anotherUser = userByUsername("user1");

            Course course = createCourseEntity("Algorithms", "Base course");

            assertThat(courseMemberRepository.findByCourseIdAndUserId(course.getId(), anotherUser.getId())).isEmpty();

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId(),
                    HttpMethod.GET,
                    authorizedRequest(token),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns404_whenCourseDoesNotExist() {
            String token = registerAndGetToken("user1", "password123");

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + UUID.randomUUID(),
                    HttpMethod.GET,
                    authorizedRequest(token),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class ListMyCourses {

        @Test
        void returns200_andOnlyCurrentUsersCourses() {
            String token = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            Course course1 = createCourseEntity("Java", "Course 1");
            Course course2 = createCourseEntity("Spring", "Course 2");
            Course otherCourse = createCourseEntity("Python", "Other");

            addMember(course1, teacher, CourseRole.TEACHER);
            addMember(course2, teacher, CourseRole.STUDENT);

            User anotherUser = userRepository.save(User.builder()
                    .username("otheruser")
                    .passwordHash("hash")
                    .build());

            addMember(otherCourse, anotherUser, CourseRole.TEACHER);

            ResponseEntity<PageDto<CourseDto>> response = restTemplate.exchange(
                    "/api/v1/courses",
                    HttpMethod.GET,
                    authorizedRequest(token),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getContent()).hasSize(2);
        }

        @Test
        void filtersByRole_whenRoleProvided() {
            String token = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            Course teacherCourse = createCourseEntity("Java", "Teacher course");
            Course studentCourse = createCourseEntity("Spring", "Student course");

            addMember(teacherCourse, teacher, CourseRole.TEACHER);
            addMember(studentCourse, teacher, CourseRole.STUDENT);

            ResponseEntity<PageDto<CourseDto>> response = restTemplate.exchange(
                    "/api/v1/courses?role=TEACHER",
                    HttpMethod.GET,
                    authorizedRequest(token),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getContent()).hasSize(1);
            assertThat(response.getBody().getContent().get(0).getName()).isEqualTo("Java");
            assertThat(response.getBody().getContent().get(0).getCurrentUserRole()).isEqualTo(CourseRole.TEACHER);
        }

        @Test
        void supportsPagination() {
            String token = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            for (int i = 1; i <= 3; i++) {
                Course course = createCourseEntity("Course " + i, "Desc " + i);
                addMember(course, teacher, CourseRole.TEACHER);
            }

            ResponseEntity<PageDto<CourseDto>> response = restTemplate.exchange(
                    "/api/v1/courses?page=0&size=2",
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
        void returns401_whenNoTokenProvided() {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "/api/v1/courses",
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    class UpdateCourse {

        @Test
        void returns200_whenTeacherUpdatesCourse() {
            String token = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            Course course = createCourseEntity("Old Name", "Old Desc");
            addMember(course, teacher, CourseRole.TEACHER);

            ResponseEntity<CourseDto> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId(),
                    HttpMethod.PUT,
                    authorizedUpdateRequest(token, "New Name", "New Desc"),
                    CourseDto.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getName()).isEqualTo("New Name");
            assertThat(response.getBody().getDescription()).isEqualTo("New Desc");

            Course updated = courseRepository.findById(course.getId()).orElseThrow();
            assertThat(updated.getName()).isEqualTo("New Name");
            assertThat(updated.getDescription()).isEqualTo("New Desc");
        }

        @Test
        void returns403_whenStudentTriesToUpdateCourse() {
            String token = registerAndGetToken("student1", "password123");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Old Name", "Old Desc");
            addMember(course, student, CourseRole.STUDENT);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId(),
                    HttpMethod.PUT,
                    authorizedUpdateRequest(token, "New Name", "New Desc"),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns400_whenRequestInvalid() {
            String token = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            Course course = createCourseEntity("Old Name", "Old Desc");
            addMember(course, teacher, CourseRole.TEACHER);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId(),
                    HttpMethod.PUT,
                    authorizedUpdateRequest(token, "a".repeat(201), "New Desc"),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    class DeleteCourse {

        @Test
        void returns204_andDeletesCourse_whenTeacherDeletesCourse() {
            String token = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            Course course = createCourseEntity("To delete", "Desc");
            addMember(course, teacher, CourseRole.TEACHER);

            ResponseEntity<Void> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId(),
                    HttpMethod.DELETE,
                    authorizedRequest(token),
                    Void.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(courseRepository.findById(course.getId())).isEmpty();
        }

        @Test
        void returns403_whenStudentTriesToDeleteCourse() {
            String token = registerAndGetToken("student1", "password123");
            User student = userByUsername("student1");

            Course course = createCourseEntity("To delete", "Desc");
            addMember(course, student, CourseRole.STUDENT);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId(),
                    HttpMethod.DELETE,
                    authorizedRequest(token),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns404_whenCourseDoesNotExist() {
            String token = registerAndGetToken("teacher1", "password123");

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + UUID.randomUUID(),
                    HttpMethod.DELETE,
                    authorizedRequest(token),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}