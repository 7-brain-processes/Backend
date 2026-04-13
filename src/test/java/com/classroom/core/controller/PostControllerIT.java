package com.classroom.core.controller;

import com.classroom.core.TestcontainersConfig;
import com.classroom.core.dto.PageDto;
import com.classroom.core.dto.auth.AuthResponse;
import com.classroom.core.dto.auth.RegisterRequest;
import com.classroom.core.dto.post.CreatePostRequest;
import com.classroom.core.dto.post.PostDto;
import com.classroom.core.dto.post.UpdatePostRequest;
import com.classroom.core.model.Course;
import com.classroom.core.model.CourseMember;
import com.classroom.core.model.CourseRole;
import com.classroom.core.model.Post;
import com.classroom.core.model.PostType;
import com.classroom.core.model.TeamRequirementTemplate;
import com.classroom.core.model.User;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
import com.classroom.core.repository.PostRepository;
import com.classroom.core.repository.TeamRequirementTemplateRepository;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class PostControllerIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseMemberRepository courseMemberRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private TeamRequirementTemplateRepository teamRequirementTemplateRepository;

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

    private Post createPostEntity(Course course, User author, String title, PostType type) {
        return postRepository.save(
                Post.builder()
                        .course(course)
                        .author(author)
                        .title(title)
                        .content("content-" + title)
                        .type(type)
                        .deadline(null)
                        .files(new ArrayList<>())
                        .comments(new ArrayList<>())
                        .build()
        );
    }

    private HttpEntity<CreatePostRequest> authorizedCreateRequest(
            String token,
            String title,
            String content,
            PostType type
    ) {
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle(title);
        request.setContent(content);
        request.setType(type);

        HttpHeaders headers = bearerHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        return new HttpEntity<>(request, headers);
    }

    private HttpEntity<UpdatePostRequest> authorizedUpdateRequest(
            String token,
            String title,
            String content,
            Instant deadline
    ) {
        UpdatePostRequest request = new UpdatePostRequest();
        request.setTitle(title);
        request.setContent(content);
        request.setDeadline(deadline);

        HttpHeaders headers = bearerHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        return new HttpEntity<>(request, headers);
    }

    private HttpEntity<Void> authorizedRequest(String token) {
        return new HttpEntity<>(bearerHeaders(token));
    }

    @Nested
    class CreatePost {

        @Test
        void returns201_andCreatedPost_whenTeacherCreatesPost() {
            String token = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);

            ResponseEntity<PostDto> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts",
                    HttpMethod.POST,
                    authorizedCreateRequest(token, "Week 1", "Read chapter 1", PostType.MATERIAL),
                    PostDto.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isNotNull();
            assertThat(response.getBody().getTitle()).isEqualTo("Week 1");
            assertThat(response.getBody().getContent()).isEqualTo("Read chapter 1");
            assertThat(response.getBody().getType()).isEqualTo(PostType.MATERIAL);
        }

        @Test
        void returns403_whenStudentCreatesPost() {
            String token = registerAndGetToken("student1", "password123");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, student, CourseRole.STUDENT);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts",
                    HttpMethod.POST,
                    authorizedCreateRequest(token, "Week 1", "Read chapter 1", PostType.MATERIAL),
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

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts",
                    HttpMethod.POST,
                    authorizedCreateRequest(token, "", "Read chapter 1", PostType.MATERIAL),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void returns401_whenNoTokenProvided_forCreate() {
            CreatePostRequest request = new CreatePostRequest();
            request.setTitle("Week 1");
            request.setContent("Read chapter 1");
            request.setType(PostType.MATERIAL);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Course course = createCourseEntity("Java", "Course");

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts",
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void returns400_whenDeadlineIsInThePast() {
            String token = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);

            CreatePostRequest request = new CreatePostRequest();
            request.setTitle("Assignment 1");
            request.setContent("Submit solution");
            request.setType(PostType.TASK);
            request.setDeadline(Instant.now().minusSeconds(3600));

            HttpHeaders headers = bearerHeaders(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts",
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void returns201_andAppliesTemplate_whenTaskCreatedWithTemplateId() {
            String token = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            registerAndGetToken("student1", "password123");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            addMember(course, student, CourseRole.STUDENT);

            TeamRequirementTemplate template = teamRequirementTemplateRepository.save(
                    TeamRequirementTemplate.builder()
                            .course(course)
                            .name("Template A")
                        .minTeamSize(1)
                            .maxTeamSize(3)
                            .active(true)
                            .build()
            );

            CreatePostRequest request = new CreatePostRequest();
            request.setTitle("Task with template");
            request.setContent("Solve this task");
            request.setType(PostType.TASK);
            request.setTeamRequirementTemplateId(template.getId());

            HttpHeaders headers = bearerHeaders(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<PostDto> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts",
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    PostDto.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getTeamRequirementTemplateId()).isEqualTo(template.getId());
            assertThat(response.getBody().getTeamFormationMode()).isNotNull();
        }
    }

    @Nested
    class ListPosts {

        @Test
        void returns200_andPostsForMember() {
            String token = registerAndGetToken("student1", "password123");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, student, CourseRole.STUDENT);
            createPostEntity(course, student, "Week 1", PostType.MATERIAL);

            ResponseEntity<PageDto<PostDto>> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts",
                    HttpMethod.GET,
                    authorizedRequest(token),
                    new ParameterizedTypeReference<>() {}
            );
            System.out.println("STATUS = " + response.getStatusCode());
            System.out.println("BODY = " + response.getBody());


            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getContent()).hasSize(1);
            assertThat(response.getBody().getContent().get(0).getTitle()).isEqualTo("Week 1");
        }

        @Test
        void filtersByType() {
            String token = registerAndGetToken("student1", "password123");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, student, CourseRole.STUDENT);
            createPostEntity(course, student, "Material 1", PostType.MATERIAL);
            createPostEntity(course, student, "Task 1", PostType.TASK);

            ResponseEntity<PageDto<PostDto>> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts?type=TASK",
                    HttpMethod.GET,
                    authorizedRequest(token),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getContent()).hasSize(1);
            assertThat(response.getBody().getContent().get(0).getTitle()).isEqualTo("Task 1");
            assertThat(response.getBody().getContent().get(0).getType()).isEqualTo(PostType.TASK);
        }

        @Test
        void returns403_whenNotMember() {
            String token = registerAndGetToken("user1", "password123");

            Course course = createCourseEntity("Java", "Course");

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts",
                    HttpMethod.GET,
                    authorizedRequest(token),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns401_whenNoTokenProvided_forList() {
            Course course = createCourseEntity("Java", "Course");

            ResponseEntity<String> response = restTemplate.getForEntity(
                    "/api/v1/courses/" + course.getId() + "/posts",
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    class GetPost {

        @Test
        void returns200_whenMemberGetsPost() {
            String token = registerAndGetToken("student1", "password123");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, student, CourseRole.STUDENT);
            Post post = createPostEntity(course, student, "Week 1", PostType.MATERIAL);

            ResponseEntity<PostDto> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId(),
                    HttpMethod.GET,
                    authorizedRequest(token),
                    PostDto.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isEqualTo(post.getId());
            assertThat(response.getBody().getTitle()).isEqualTo("Week 1");
        }

        @Test
        void returns404_whenPostDoesNotExist() {
            String token = registerAndGetToken("student1", "password123");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, student, CourseRole.STUDENT);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + UUID.randomUUID(),
                    HttpMethod.GET,
                    authorizedRequest(token),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class UpdatePost {

        @Test
        void returns200_whenTeacherUpdatesPost() {
            String token = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            Post post = createPostEntity(course, teacher, "Old title", PostType.MATERIAL);

            ResponseEntity<PostDto> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId(),
                    HttpMethod.PUT,
                    authorizedUpdateRequest(token, "New title", "New content", null),
                    PostDto.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getTitle()).isEqualTo("New title");
            assertThat(response.getBody().getContent()).isEqualTo("New content");
        }

        @Test
        void returns403_whenStudentUpdatesPost() {
            String token = registerAndGetToken("student1", "password123");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, student, CourseRole.STUDENT);
            Post post = createPostEntity(course, student, "Old title", PostType.MATERIAL);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId(),
                    HttpMethod.PUT,
                    authorizedUpdateRequest(token, "Hack", "Hack", null),
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
            Post post = createPostEntity(course, teacher, "Old title", PostType.MATERIAL);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId(),
                    HttpMethod.PUT,
                    authorizedUpdateRequest(token, "a".repeat(301), "New content", null),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void returns400_whenUpdatingDeadlineToThePast() {
            String token = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            Post post = createPostEntity(course, teacher, "Assignment 1", PostType.TASK);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId(),
                    HttpMethod.PUT,
                    authorizedUpdateRequest(token, "Assignment 1", "Updated", Instant.now().minusSeconds(3600)),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    class DeletePost {

        @Test
        void returns204_whenTeacherDeletesPost() {
            String token = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            Post post = createPostEntity(course, teacher, "Delete me", PostType.MATERIAL);

            ResponseEntity<Void> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId(),
                    HttpMethod.DELETE,
                    authorizedRequest(token),
                    Void.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(postRepository.findById(post.getId())).isEmpty();
        }

        @Test
        void returns403_whenStudentDeletesPost() {
            String token = registerAndGetToken("student1", "password123");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, student, CourseRole.STUDENT);
            Post post = createPostEntity(course, student, "Delete me", PostType.MATERIAL);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId(),
                    HttpMethod.DELETE,
                    authorizedRequest(token),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns404_whenPostDoesNotExist() {
            String token = registerAndGetToken("teacher1", "password123");
            User teacher = userByUsername("teacher1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + UUID.randomUUID(),
                    HttpMethod.DELETE,
                    authorizedRequest(token),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}