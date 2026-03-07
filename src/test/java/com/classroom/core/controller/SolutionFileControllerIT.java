package com.classroom.core.controller;

import com.classroom.core.TestcontainersConfig;
import com.classroom.core.dto.auth.AuthResponse;
import com.classroom.core.dto.auth.RegisterRequest;
import com.classroom.core.dto.file.FileDto;
import com.classroom.core.model.*;
import com.classroom.core.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class SolutionFileControllerIT {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseMemberRepository courseMemberRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private SolutionRepository solutionRepository;
    @Autowired private SolutionFileRepository solutionFileRepository;

    @BeforeEach
    void setUp() {
        solutionFileRepository.deleteAll();
        solutionRepository.deleteAll();
        postRepository.deleteAll();
        courseMemberRepository.deleteAll();
        courseRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String registerAndGetToken(String username) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setPassword("password123");
        req.setDisplayName(username);
        return restTemplate.postForEntity("/api/v1/auth/register", req, AuthResponse.class)
                .getBody().getToken();
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private HttpEntity<Void> auth(String token) {
        return new HttpEntity<>(bearerHeaders(token));
    }

    private User user(String username) {
        return userRepository.findByUsername(username).orElseThrow();
    }

    private Course createCourse(String name) {
        return courseRepository.save(Course.builder().name(name).build());
    }

    private CourseMember addMember(Course c, User u, CourseRole r) {
        return courseMemberRepository.save(CourseMember.builder().course(c).user(u).role(r).build());
    }

    private Post createTask(Course c, User author) {
        return postRepository.save(Post.builder()
                .course(c).author(author).title("Task").content("Do it")
                .type(PostType.TASK).files(new ArrayList<>()).comments(new ArrayList<>()).build());
    }

    private Solution createSolution(Post post, User student) {
        return solutionRepository.save(Solution.builder()
                .post(post).student(student).text("Answer")
                .status(SolutionStatus.SUBMITTED)
                .files(new ArrayList<>()).comments(new ArrayList<>()).build());
    }

    private SolutionFile createSolutionFile(Solution sol) {
        return solutionFileRepository.save(SolutionFile.builder()
                .solution(sol).originalName("answer.pdf").contentType("application/pdf")
                .sizeBytes(200).storagePath("/uploads/answer.pdf").build());
    }

    private String base(UUID courseId, UUID postId, UUID solutionId) {
        return "/api/v1/courses/" + courseId + "/posts/" + postId
                + "/solutions/" + solutionId + "/files";
    }

    private HttpEntity<MultiValueMap<String, Object>> uploadReq(String token) {
        HttpHeaders h = bearerHeaders(token);
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new org.springframework.core.io.ByteArrayResource("data".getBytes()) {
            @Override public String getFilename() { return "answer.pdf"; }
        });
        return new HttpEntity<>(body, h);
    }

    @Nested
    class ListSolutionFiles {

        @Test
        void returns200_withFiles() {
            String tToken = registerAndGetToken("teacher1");
            String sToken = registerAndGetToken("student1");
            User teacher = user("teacher1");
            User student = user("student1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post task = createTask(c, teacher);
            Solution sol = createSolution(task, student);
            createSolutionFile(sol);

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId(), sol.getId()),
                    HttpMethod.GET, auth(sToken),
                    new ParameterizedTypeReference<List<FileDto>>() {});

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).hasSize(1);
            assertThat(resp.getBody().get(0).getOriginalName()).isEqualTo("answer.pdf");
        }

        @Test
        void returns403_whenNotMember() {
            String tToken = registerAndGetToken("teacher1");
            String sToken = registerAndGetToken("student1");
            String outsiderToken = registerAndGetToken("outsider");
            User teacher = user("teacher1");
            User student = user("student1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post task = createTask(c, teacher);
            Solution sol = createSolution(task, student);

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId(), sol.getId()),
                    HttpMethod.GET, auth(outsiderToken), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns404_whenSolutionNotFound() {
            String token = registerAndGetToken("teacher1");
            User teacher = user("teacher1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            Post task = createTask(c, teacher);

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId(), UUID.randomUUID()),
                    HttpMethod.GET, auth(token), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class UploadSolutionFile {

        @Test
        void returns201_whenMemberUploads() {
            String tToken = registerAndGetToken("teacher1");
            String sToken = registerAndGetToken("student1");
            User teacher = user("teacher1");
            User student = user("student1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post task = createTask(c, teacher);
            Solution sol = createSolution(task, student);

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId(), sol.getId()),
                    HttpMethod.POST, uploadReq(sToken), FileDto.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().getOriginalName()).isEqualTo("answer.pdf");
        }

        @Test
        void returns403_whenNotMember() {
            String tToken = registerAndGetToken("teacher1");
            String sToken = registerAndGetToken("student1");
            String outsiderToken = registerAndGetToken("outsider");
            User teacher = user("teacher1");
            User student = user("student1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post task = createTask(c, teacher);
            Solution sol = createSolution(task, student);

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId(), sol.getId()),
                    HttpMethod.POST, uploadReq(outsiderToken), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns404_whenSolutionNotFound() {
            String token = registerAndGetToken("teacher1");
            User teacher = user("teacher1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            Post task = createTask(c, teacher);

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId(), UUID.randomUUID()),
                    HttpMethod.POST, uploadReq(token), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class DeleteSolutionFile {

        @Test
        void returns204_whenMemberDeletes() {
            String tToken = registerAndGetToken("teacher1");
            String sToken = registerAndGetToken("student1");
            User teacher = user("teacher1");
            User student = user("student1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post task = createTask(c, teacher);
            Solution sol = createSolution(task, student);
            SolutionFile sf = createSolutionFile(sol);

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId(), sol.getId()) + "/" + sf.getId(),
                    HttpMethod.DELETE, auth(sToken), Void.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(solutionFileRepository.findById(sf.getId())).isEmpty();
        }

        @Test
        void returns404_whenFileNotFound() {
            String tToken = registerAndGetToken("teacher1");
            String sToken = registerAndGetToken("student1");
            User teacher = user("teacher1");
            User student = user("student1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post task = createTask(c, teacher);
            Solution sol = createSolution(task, student);

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId(), sol.getId()) + "/" + UUID.randomUUID(),
                    HttpMethod.DELETE, auth(sToken), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void returns403_whenNotMember() {
            String tToken = registerAndGetToken("teacher1");
            String sToken = registerAndGetToken("student1");
            String outsiderToken = registerAndGetToken("outsider");
            User teacher = user("teacher1");
            User student = user("student1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post task = createTask(c, teacher);
            Solution sol = createSolution(task, student);
            SolutionFile sf = createSolutionFile(sol);

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId(), sol.getId()) + "/" + sf.getId(),
                    HttpMethod.DELETE, auth(outsiderToken), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
