package com.classroom.core.controller;

import com.classroom.core.TestcontainersConfig;
import com.classroom.core.dto.PageDto;
import com.classroom.core.dto.auth.AuthResponse;
import com.classroom.core.dto.auth.RegisterRequest;
import com.classroom.core.dto.solution.CreateSolutionRequest;
import com.classroom.core.dto.solution.SolutionDto;
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

import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class SolutionControllerIT {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseMemberRepository courseMemberRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private SolutionRepository solutionRepository;

    @BeforeEach
    void setUp() {
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

    private Post createMaterial(Course c, User author) {
        return postRepository.save(Post.builder()
                .course(c).author(author).title("Material").content("Read")
                .type(PostType.MATERIAL).files(new ArrayList<>()).comments(new ArrayList<>()).build());
    }

    private Solution createSolution(Post post, User student, String text) {
        return solutionRepository.save(Solution.builder()
                .post(post).student(student).text(text)
                .status(SolutionStatus.SUBMITTED)
                .files(new ArrayList<>()).comments(new ArrayList<>()).build());
    }

    private String base(UUID courseId, UUID postId) {
        return "/api/v1/courses/" + courseId + "/posts/" + postId + "/solutions";
    }

    private HttpEntity<CreateSolutionRequest> solReq(String token, String text) {
        CreateSolutionRequest r = new CreateSolutionRequest();
        r.setText(text);
        HttpHeaders h = bearerHeaders(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(r, h);
    }

    @Nested
    class CreateSolution {

        @Test
        void returns201_whenStudentSubmits() {
            String tToken = registerAndGetToken("teacher1");
            String sToken = registerAndGetToken("student1");
            User teacher = user("teacher1");
            User student = user("student1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post task = createTask(c, teacher);

            var resp = restTemplate.exchange(base(c.getId(), task.getId()),
                    HttpMethod.POST, solReq(sToken, "My answer"), SolutionDto.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().getText()).isEqualTo("My answer");
            assertThat(resp.getBody().getStatus()).isEqualTo(SolutionStatus.SUBMITTED);
        }

        @Test
        void returns403_whenTeacherSubmits() {
            String token = registerAndGetToken("teacher1");
            User teacher = user("teacher1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            Post task = createTask(c, teacher);

            var resp = restTemplate.exchange(base(c.getId(), task.getId()),
                    HttpMethod.POST, solReq(token, "text"), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns400_whenPostIsMaterial() {
            String sToken = registerAndGetToken("student1");
            String tToken = registerAndGetToken("teacher1");
            User student = user("student1");
            User teacher = user("teacher1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post material = createMaterial(c, teacher);

            var resp = restTemplate.exchange(base(c.getId(), material.getId()),
                    HttpMethod.POST, solReq(sToken, "text"), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void returns409_whenDuplicateSolution() {
            String sToken = registerAndGetToken("student1");
            String tToken = registerAndGetToken("teacher1");
            User student = user("student1");
            User teacher = user("teacher1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post task = createTask(c, teacher);
            createSolution(task, student, "First");

            var resp = restTemplate.exchange(base(c.getId(), task.getId()),
                    HttpMethod.POST, solReq(sToken, "Second"), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Nested
    class ListSolutions {

        @Test
        void returns200_whenTeacherLists() {
            String tToken = registerAndGetToken("teacher1");
            String sToken = registerAndGetToken("student1");
            User teacher = user("teacher1");
            User student = user("student1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post task = createTask(c, teacher);
            createSolution(task, student, "Answer");

            var resp = restTemplate.exchange(base(c.getId(), task.getId()),
                    HttpMethod.GET, auth(tToken),
                    new ParameterizedTypeReference<PageDto<SolutionDto>>() {});

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().getContent()).hasSize(1);
        }

        @Test
        void returns403_whenStudentLists() {
            String tToken = registerAndGetToken("teacher1");
            String sToken = registerAndGetToken("student1");
            User teacher = user("teacher1");
            User student = user("student1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post task = createTask(c, teacher);

            var resp = restTemplate.exchange(base(c.getId(), task.getId()),
                    HttpMethod.GET, auth(sToken), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void filtersbyStatus() {
            String tToken = registerAndGetToken("teacher1");
            String s1Token = registerAndGetToken("student1");
            String s2Token = registerAndGetToken("student2");
            User teacher = user("teacher1");
            User s1 = user("student1");
            User s2 = user("student2");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, s1, CourseRole.STUDENT);
            addMember(c, s2, CourseRole.STUDENT);
            Post task = createTask(c, teacher);
            Solution sol = createSolution(task, s1, "A1");
            sol.setStatus(SolutionStatus.GRADED);
            solutionRepository.save(sol);
            createSolution(task, s2, "A2");

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId()) + "?status=GRADED",
                    HttpMethod.GET, auth(tToken),
                    new ParameterizedTypeReference<PageDto<SolutionDto>>() {});

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().getContent()).hasSize(1);
            assertThat(resp.getBody().getContent().get(0).getStatus()).isEqualTo(SolutionStatus.GRADED);
        }
    }

    @Nested
    class GetMySolution {

        @Test
        void returns200_whenStudentGetsMySolution() {
            String tToken = registerAndGetToken("teacher1");
            String sToken = registerAndGetToken("student1");
            User teacher = user("teacher1");
            User student = user("student1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post task = createTask(c, teacher);
            createSolution(task, student, "My answer");

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId()) + "/my",
                    HttpMethod.GET, auth(sToken), SolutionDto.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().getText()).isEqualTo("My answer");
        }

        @Test
        void returns404_whenNoSolution() {
            String sToken = registerAndGetToken("student1");
            String tToken = registerAndGetToken("teacher1");
            User student = user("student1");
            User teacher = user("teacher1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post task = createTask(c, teacher);

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId()) + "/my",
                    HttpMethod.GET, auth(sToken), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class GetSolution {

        @Test
        void returns200_whenTeacherGets() {
            String tToken = registerAndGetToken("teacher1");
            String sToken = registerAndGetToken("student1");
            User teacher = user("teacher1");
            User student = user("student1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post task = createTask(c, teacher);
            Solution sol = createSolution(task, student, "Answer");

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId()) + "/" + sol.getId(),
                    HttpMethod.GET, auth(tToken), SolutionDto.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().getText()).isEqualTo("Answer");
        }

        @Test
        void returns403_whenOtherStudentGets() {
            String tToken = registerAndGetToken("teacher1");
            String s1Token = registerAndGetToken("student1");
            String s2Token = registerAndGetToken("student2");
            User teacher = user("teacher1");
            User s1 = user("student1");
            User s2 = user("student2");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, s1, CourseRole.STUDENT);
            addMember(c, s2, CourseRole.STUDENT);
            Post task = createTask(c, teacher);
            Solution sol = createSolution(task, s1, "Private");

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId()) + "/" + sol.getId(),
                    HttpMethod.GET, auth(s2Token), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns404_whenSolutionNotFound() {
            String tToken = registerAndGetToken("teacher1");
            User teacher = user("teacher1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            Post task = createTask(c, teacher);

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId()) + "/" + UUID.randomUUID(),
                    HttpMethod.GET, auth(tToken), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class UpdateSolution {

        @Test
        void returns200_whenOwnerUpdates() {
            String tToken = registerAndGetToken("teacher1");
            String sToken = registerAndGetToken("student1");
            User teacher = user("teacher1");
            User student = user("student1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post task = createTask(c, teacher);
            Solution sol = createSolution(task, student, "Old");

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId()) + "/" + sol.getId(),
                    HttpMethod.PUT, solReq(sToken, "New"), SolutionDto.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().getText()).isEqualTo("New");
        }

        @Test
        void returns403_whenOtherStudentUpdates() {
            String tToken = registerAndGetToken("teacher1");
            String s1Token = registerAndGetToken("student1");
            String s2Token = registerAndGetToken("student2");
            User teacher = user("teacher1");
            User s1 = user("student1");
            User s2 = user("student2");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, s1, CourseRole.STUDENT);
            addMember(c, s2, CourseRole.STUDENT);
            Post task = createTask(c, teacher);
            Solution sol = createSolution(task, s1, "Original");

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId()) + "/" + sol.getId(),
                    HttpMethod.PUT, solReq(s2Token, "Hacked"), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    class DeleteSolution {

        @Test
        void returns204_whenOwnerDeletes() {
            String tToken = registerAndGetToken("teacher1");
            String sToken = registerAndGetToken("student1");
            User teacher = user("teacher1");
            User student = user("student1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post task = createTask(c, teacher);
            Solution sol = createSolution(task, student, "Bye");

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId()) + "/" + sol.getId(),
                    HttpMethod.DELETE, auth(sToken), Void.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(solutionRepository.findById(sol.getId())).isEmpty();
        }

        @Test
        void returns403_whenOtherStudentDeletes() {
            String tToken = registerAndGetToken("teacher1");
            String s1Token = registerAndGetToken("student1");
            String s2Token = registerAndGetToken("student2");
            User teacher = user("teacher1");
            User s1 = user("student1");
            User s2 = user("student2");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, s1, CourseRole.STUDENT);
            addMember(c, s2, CourseRole.STUDENT);
            Post task = createTask(c, teacher);
            Solution sol = createSolution(task, s1, "Mine");

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId()) + "/" + sol.getId(),
                    HttpMethod.DELETE, auth(s2Token), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns404_whenNotFound() {
            String tToken = registerAndGetToken("teacher1");
            String sToken = registerAndGetToken("student1");
            User teacher = user("teacher1");
            User student = user("student1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post task = createTask(c, teacher);

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId()) + "/" + UUID.randomUUID(),
                    HttpMethod.DELETE, auth(sToken), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
