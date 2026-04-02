package com.classroom.core.controller;

import com.classroom.core.TestcontainersConfig;
import com.classroom.core.dto.auth.AuthResponse;
import com.classroom.core.dto.auth.RegisterRequest;
import com.classroom.core.dto.post.CreatePostRequest;
import com.classroom.core.dto.team.PostCaptainDto;
import com.classroom.core.dto.team.SelectCaptainsRequest;
import com.classroom.core.dto.team.SelectCaptainsResultDto;
import com.classroom.core.dto.team.SetTeamFormationModeRequest;
import com.classroom.core.model.*;
import com.classroom.core.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class TeamFormationCaptainControllerIT {

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
    private PostCaptainRepository postCaptainRepository;

    private String teacherToken;
    private String studentToken;
    private Course course;
    private Post post;
    private User teacher;
    private User student1;
    private User student2;

    @BeforeEach
    void setUp() {
        postCaptainRepository.deleteAll();
        postRepository.deleteAll();
        courseMemberRepository.deleteAll();
        courseRepository.deleteAll();
        userRepository.deleteAll();

        teacherToken = registerAndGetToken("teacher", "password");
        studentToken = registerAndGetToken("student1", "password");
        registerAndGetToken("student2", "password");

        teacher = userByUsername("teacher");
        student1 = userByUsername("student1");
        student2 = userByUsername("student2");

        course = createCourseEntity("Test Course", "Description");

        addMember(course, teacher, CourseRole.TEACHER);
        addMember(course, student1, CourseRole.STUDENT);
        addMember(course, student2, CourseRole.STUDENT);

        post = createPost(course, teacher, "Test Task", PostType.TASK, TeamFormationMode.RANDOM_CAPTAIN_SELECTION);
    }

    private String registerAndGetToken(String username, String password) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setPassword(password);
        req.setDisplayName(username);

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/register", req, AuthResponse.class);
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
                        .build());
    }

    private CourseMember addMember(Course course, User user, CourseRole role) {
        return courseMemberRepository.save(
                CourseMember.builder()
                        .course(course)
                        .user(user)
                        .role(role)
                        .build());
    }

    private Post createPost(Course course, User author, String title, PostType type, TeamFormationMode mode) {
        CreatePostRequest req = new CreatePostRequest();
        req.setTitle(title);
        req.setContent("Content");
        req.setType(type);
        req.setTeamFormationMode(mode);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(teacherToken);

        HttpEntity<CreatePostRequest> entity = new HttpEntity<>(req, headers);

        ResponseEntity<Post> response = restTemplate.postForEntity(
                "/api/v1/courses/{courseId}/posts", entity, Post.class, course.getId());

        return response.getBody();
    }

    @Test
    void getCaptains_shouldReturnEmptyList_whenNoCaptainsSelected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(teacherToken);

        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<List<PostCaptainDto>> response = restTemplate.exchange(
                "/api/v1/courses/{courseId}/posts/{postId}/team-formation/captains",
                HttpMethod.GET, entity,
                new ParameterizedTypeReference<List<PostCaptainDto>>() {},
                course.getId(), post.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void selectCaptains_shouldSelectCaptains_whenValidRequest() {
        SelectCaptainsRequest req = new SelectCaptainsRequest();
        req.setReshuffle(false);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(teacherToken);

        HttpEntity<SelectCaptainsRequest> entity = new HttpEntity<>(req, headers);

        ResponseEntity<SelectCaptainsResultDto> response = restTemplate.postForEntity(
                "/api/v1/courses/{courseId}/posts/{postId}/team-formation/captains",
                entity, SelectCaptainsResultDto.class, course.getId(), post.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SelectCaptainsResultDto result = response.getBody();
        assertThat(result.getCaptainCount()).isEqualTo(1); // 2 students -> 1 captain
        assertThat(result.getCaptains()).hasSize(1);
        assertThat(result.getCaptains().get(0).getPostId()).isEqualTo(post.getId());
    }

    @Test
    void selectCaptains_shouldFail_whenUserIsNotTeacher() {
        SelectCaptainsRequest req = new SelectCaptainsRequest();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(studentToken);

        HttpEntity<SelectCaptainsRequest> entity = new HttpEntity<>(req, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/courses/{courseId}/posts/{postId}/team-formation/captains",
                entity, String.class, course.getId(), post.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getCaptains_shouldReturnCaptains_afterSelection() {

        selectCaptains_shouldSelectCaptains_whenValidRequest();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(teacherToken);

        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<List<PostCaptainDto>> response = restTemplate.exchange(
                "/api/v1/courses/{courseId}/posts/{postId}/team-formation/captains",
                HttpMethod.GET, entity,
                new ParameterizedTypeReference<List<PostCaptainDto>>() {},
                course.getId(), post.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }
}