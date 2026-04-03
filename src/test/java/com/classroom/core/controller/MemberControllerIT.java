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
import com.classroom.core.support.TestDatabaseCleaner;
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
        void returns401_whenNoTokenProvided_forListMembers() {
            Course course = createCourseEntity("Java", "Course");

            ResponseEntity<String> response = restTemplate.getForEntity(
                    "/api/v1/courses/" + course.getId() + "/members",
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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
        void returns401_whenNoTokenProvided_forRemoveMember() {
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

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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
        void returns401_whenNoTokenProvided_forLeaveCourse() {
            Course course = createCourseEntity("Java", "Course");

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/leave",
                    HttpMethod.POST,
                    HttpEntity.EMPTY,
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    class CourseCategorySelection {

        @Test
        void teacherCanCreateCategory_andStudentCanSelectIt() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            String studentToken = registerAndGetToken("student1", "password123");

            User teacher = userByUsername("teacher1");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            addMember(course, student, CourseRole.STUDENT);

            var createRequest = new com.classroom.core.dto.course.CreateCourseCategoryRequest();
            createRequest.setTitle("Beginner");
            createRequest.setDescription("New students");

            ResponseEntity<com.classroom.core.dto.course.CourseCategoryDto> createResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/categories",
                    HttpMethod.POST,
                    new HttpEntity<>(createRequest, bearerHeaders(teacherToken)),
                    com.classroom.core.dto.course.CourseCategoryDto.class
            );

            assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(createResponse.getBody()).isNotNull();
            UUID categoryId = createResponse.getBody().getId();

            var selectRequest = new com.classroom.core.dto.course.SetMyCategoryRequest();
            selectRequest.setCategoryId(categoryId);

            ResponseEntity<com.classroom.core.dto.course.CourseCategoryDto> setResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/members/me/category",
                    HttpMethod.PUT,
                    new HttpEntity<>(selectRequest, bearerHeaders(studentToken)),
                    com.classroom.core.dto.course.CourseCategoryDto.class
            );

            assertThat(setResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(setResponse.getBody()).isNotNull();
            assertThat(setResponse.getBody().getTitle()).isEqualTo("Beginner");

            ResponseEntity<com.classroom.core.dto.course.CourseCategoryDto> getResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/members/me/category",
                    HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(studentToken)),
                    com.classroom.core.dto.course.CourseCategoryDto.class
            );

            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getResponse.getBody()).isNotNull();
            assertThat(getResponse.getBody().getId()).isEqualTo(categoryId);

            ResponseEntity<PageDto<MemberDto>> members = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/members",
                    HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(teacherToken)),
                    new org.springframework.core.ParameterizedTypeReference<>() {}
            );

            assertThat(members.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(members.getBody()).isNotNull();
            assertThat(members.getBody().getContent()).hasSize(2);
            assertThat(members.getBody().getContent().stream()
                    .filter(m -> m.getUser().getId().equals(student.getId()))
                    .findFirst().orElseThrow()
                    .getCategory().getId()).isEqualTo(categoryId);
        }

        @Test
        void teacherCanCRUDECourseCategory() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);

            // create
            var createRequest = new com.classroom.core.dto.course.CreateCourseCategoryRequest();
            createRequest.setTitle("Advanced");
            createRequest.setDescription("For advanced students");

            ResponseEntity<com.classroom.core.dto.course.CourseCategoryDto> createResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/categories",
                    HttpMethod.POST,
                    new HttpEntity<>(createRequest, bearerHeaders(teacherToken)),
                    com.classroom.core.dto.course.CourseCategoryDto.class
            );

            assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(createResponse.getBody()).isNotNull();
            UUID categoryId = createResponse.getBody().getId();
            assertThat(createResponse.getBody().getTitle()).isEqualTo("Advanced");

            // get
            ResponseEntity<com.classroom.core.dto.course.CourseCategoryDto> getResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/categories/" + categoryId,
                    HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(teacherToken)),
                    com.classroom.core.dto.course.CourseCategoryDto.class
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getResponse.getBody()).isNotNull();
            assertThat(getResponse.getBody().getTitle()).isEqualTo("Advanced");

            // list
            ResponseEntity<java.util.List<com.classroom.core.dto.course.CourseCategoryDto>> listResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/categories",
                    HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(teacherToken)),
                    new org.springframework.core.ParameterizedTypeReference<java.util.List<com.classroom.core.dto.course.CourseCategoryDto>>() {}
            );
            assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(listResponse.getBody()).isNotNull();
            assertThat(listResponse.getBody().stream().anyMatch(c -> c.getId().equals(categoryId))).isTrue();

            // update
            var updateRequest = new com.classroom.core.dto.course.UpdateCourseCategoryRequest();
            updateRequest.setTitle("Advanced+1");

            ResponseEntity<com.classroom.core.dto.course.CourseCategoryDto> updateResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/categories/" + categoryId,
                    HttpMethod.PUT,
                    new HttpEntity<>(updateRequest, bearerHeaders(teacherToken)),
                    com.classroom.core.dto.course.CourseCategoryDto.class
            );
            assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(updateResponse.getBody()).isNotNull();
            assertThat(updateResponse.getBody().getTitle()).isEqualTo("Advanced+1");

            // delete
            ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/categories/" + categoryId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(bearerHeaders(teacherToken)),
                    Void.class
            );
            assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            // verify not found
            ResponseEntity<String> verifyResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/categories/" + categoryId,
                    HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(teacherToken)),
                    String.class
            );
            assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void studentCannotSelectCategoryFromAnotherCourse() {
            String teacher1Token = registerAndGetToken("teacher1", "password123");
            String teacher2Token = registerAndGetToken("teacher2", "password123");
            String studentToken = registerAndGetToken("student1", "password123");

            User teacher1 = userByUsername("teacher1");
            User teacher2 = userByUsername("teacher2");
            User student = userByUsername("student1");

            Course course1 = createCourseEntity("Java", "Course 1");
            Course course2 = createCourseEntity("Python", "Course 2");
            addMember(course1, teacher1, CourseRole.TEACHER);
            addMember(course2, teacher2, CourseRole.TEACHER);
            addMember(course1, student, CourseRole.STUDENT);
            addMember(course2, student, CourseRole.STUDENT);

            // Create category in course1
            var createRequest = new com.classroom.core.dto.course.CreateCourseCategoryRequest();
            createRequest.setTitle("Beginner");

            ResponseEntity<com.classroom.core.dto.course.CourseCategoryDto> createResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course1.getId() + "/categories",
                    HttpMethod.POST,
                    new HttpEntity<>(createRequest, bearerHeaders(teacher1Token)),
                    com.classroom.core.dto.course.CourseCategoryDto.class
            );

            assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            UUID categoryId = createResponse.getBody().getId();

            // Try to select category from course1 in course2 (category does not belong to course2)
            var selectRequest = new com.classroom.core.dto.course.SetMyCategoryRequest();
            selectRequest.setCategoryId(categoryId);

            ResponseEntity<String> setResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course2.getId() + "/members/me/category",
                    HttpMethod.PUT,
                    new HttpEntity<>(selectRequest, bearerHeaders(studentToken)),
                    String.class
            );

            // Should fail because category does not belong to course2
            assertThat(setResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void studentCannotCreateCourseCategory() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            String studentToken = registerAndGetToken("student1", "password123");

            User teacher = userByUsername("teacher1");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            addMember(course, student, CourseRole.STUDENT);

            var createRequest = new com.classroom.core.dto.course.CreateCourseCategoryRequest();
            createRequest.setTitle("Beginner");

            // Student tries to create category
            ResponseEntity<String> createResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/categories",
                    HttpMethod.POST,
                    new HttpEntity<>(createRequest, bearerHeaders(studentToken)),
                    String.class
            );

            // Should fail with Forbidden
            assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void studentCannotUpdateOrDeleteCourseCategory() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            String studentToken = registerAndGetToken("student1", "password123");

            User teacher = userByUsername("teacher1");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            addMember(course, student, CourseRole.STUDENT);

            // Teacher creates category
            var createRequest = new com.classroom.core.dto.course.CreateCourseCategoryRequest();
            createRequest.setTitle("Beginner");

            ResponseEntity<com.classroom.core.dto.course.CourseCategoryDto> createResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/categories",
                    HttpMethod.POST,
                    new HttpEntity<>(createRequest, bearerHeaders(teacherToken)),
                    com.classroom.core.dto.course.CourseCategoryDto.class
            );

            assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            UUID categoryId = createResponse.getBody().getId();

            // Student tries to update category
            var updateRequest = new com.classroom.core.dto.course.UpdateCourseCategoryRequest();
            updateRequest.setTitle("Updated");

            ResponseEntity<String> updateResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/categories/" + categoryId,
                    HttpMethod.PUT,
                    new HttpEntity<>(updateRequest, bearerHeaders(studentToken)),
                    String.class
            );

            assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            // Student tries to delete category
            ResponseEntity<String> deleteResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/categories/" + categoryId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(bearerHeaders(studentToken)),
                    String.class
            );

            assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}