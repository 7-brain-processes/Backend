package com.classroom.core.controller;

import com.classroom.core.TestcontainersConfig;
import com.classroom.core.dto.PageDto;
import com.classroom.core.dto.auth.AuthResponse;
import com.classroom.core.dto.auth.RegisterRequest;
import com.classroom.core.dto.comment.CommentDto;
import com.classroom.core.dto.comment.CreateCommentRequest;
import com.classroom.core.dto.solution.GradeRequest;
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
class GradeControllerIT {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseMemberRepository courseMemberRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private SolutionRepository solutionRepository;
    @Autowired private CommentRepository commentRepository;

    @BeforeEach
    void setUp() {
        commentRepository.deleteAll();
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

    private Comment createSolutionComment(Solution sol, User author, String text) {
        return commentRepository.save(Comment.builder().solution(sol).author(author).text(text).build());
    }

    private String base(UUID courseId, UUID postId, UUID solutionId) {
        return "/api/v1/courses/" + courseId + "/posts/" + postId + "/solutions/" + solutionId;
    }

    private HttpEntity<GradeRequest> gradeReq(String token, int grade) {
        GradeRequest r = new GradeRequest();
        r.setGrade(grade);
        HttpHeaders h = bearerHeaders(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(r, h);
    }

    private HttpEntity<CreateCommentRequest> commentReq(String token, String text) {
        CreateCommentRequest r = new CreateCommentRequest();
        r.setText(text);
        HttpHeaders h = bearerHeaders(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(r, h);
    }

    @Nested
    class GradeSolution {

        @Test
        void returns200_whenTeacherGrades() {
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
                    base(c.getId(), task.getId(), sol.getId()) + "/grade",
                    HttpMethod.PUT, gradeReq(tToken, 85), SolutionDto.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().getGrade()).isEqualTo(85);
            assertThat(resp.getBody().getStatus()).isEqualTo(SolutionStatus.GRADED);
            assertThat(resp.getBody().getGradedAt()).isNotNull();
        }

        @Test
        void returns403_whenStudentGrades() {
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
                    base(c.getId(), task.getId(), sol.getId()) + "/grade",
                    HttpMethod.PUT, gradeReq(sToken, 100), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns400_whenGradeOutOfRange() {
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
                    base(c.getId(), task.getId(), sol.getId()) + "/grade",
                    HttpMethod.PUT, gradeReq(tToken, 150), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void returns404_whenSolutionNotFound() {
            String tToken = registerAndGetToken("teacher1");
            User teacher = user("teacher1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            Post task = createTask(c, teacher);

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId(), UUID.randomUUID()) + "/grade",
                    HttpMethod.PUT, gradeReq(tToken, 90), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class ListSolutionComments {

        @Test
        void returns200_withComments() {
            String tToken = registerAndGetToken("teacher1");
            String sToken = registerAndGetToken("student1");
            User teacher = user("teacher1");
            User student = user("student1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post task = createTask(c, teacher);
            Solution sol = createSolution(task, student);
            createSolutionComment(sol, teacher, "Good job");

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId(), sol.getId()) + "/comments",
                    HttpMethod.GET, auth(tToken),
                    new ParameterizedTypeReference<PageDto<CommentDto>>() {});

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().getContent()).hasSize(1);
            assertThat(resp.getBody().getContent().get(0).getText()).isEqualTo("Good job");
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
                    base(c.getId(), task.getId(), sol.getId()) + "/comments",
                    HttpMethod.GET, auth(outsiderToken), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    class CreateSolutionComment {

        @Test
        void returns201_whenTeacherComments() {
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
                    base(c.getId(), task.getId(), sol.getId()) + "/comments",
                    HttpMethod.POST, commentReq(tToken, "Needs work"),
                    CommentDto.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().getText()).isEqualTo("Needs work");
        }

        @Test
        void returns403_whenStudentComments() {
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
                    base(c.getId(), task.getId(), sol.getId()) + "/comments",
                    HttpMethod.POST, commentReq(sToken, "text"), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns400_whenBlankText() {
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
                    base(c.getId(), task.getId(), sol.getId()) + "/comments",
                    HttpMethod.POST, commentReq(tToken, ""), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    class UpdateSolutionComment {

        @Test
        void returns200_whenAuthorUpdates() {
            String tToken = registerAndGetToken("teacher1");
            String sToken = registerAndGetToken("student1");
            User teacher = user("teacher1");
            User student = user("student1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post task = createTask(c, teacher);
            Solution sol = createSolution(task, student);
            Comment comment = createSolutionComment(sol, teacher, "Old");

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId(), sol.getId()) + "/comments/" + comment.getId(),
                    HttpMethod.PUT, commentReq(tToken, "New"),
                    CommentDto.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().getText()).isEqualTo("New");
        }

        @Test
        void returns404_whenCommentNotFound() {
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
                    base(c.getId(), task.getId(), sol.getId()) + "/comments/" + UUID.randomUUID(),
                    HttpMethod.PUT, commentReq(tToken, "text"), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class DeleteSolutionComment {

        @Test
        void returns204_whenTeacherDeletes() {
            String tToken = registerAndGetToken("teacher1");
            String sToken = registerAndGetToken("student1");
            User teacher = user("teacher1");
            User student = user("student1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post task = createTask(c, teacher);
            Solution sol = createSolution(task, student);
            Comment comment = createSolutionComment(sol, teacher, "Delete me");

            var resp = restTemplate.exchange(
                    base(c.getId(), task.getId(), sol.getId()) + "/comments/" + comment.getId(),
                    HttpMethod.DELETE, auth(tToken), Void.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(commentRepository.findById(comment.getId())).isEmpty();
        }

        @Test
        void returns404_whenCommentNotFound() {
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
                    base(c.getId(), task.getId(), sol.getId()) + "/comments/" + UUID.randomUUID(),
                    HttpMethod.DELETE, auth(tToken), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
