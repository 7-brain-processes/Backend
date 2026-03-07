package com.classroom.core.controller;

import com.classroom.core.TestcontainersConfig;
import com.classroom.core.dto.PageDto;
import com.classroom.core.dto.auth.AuthResponse;
import com.classroom.core.dto.auth.RegisterRequest;
import com.classroom.core.dto.comment.CommentDto;
import com.classroom.core.dto.comment.CreateCommentRequest;
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
class CommentControllerIT {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseMemberRepository courseMemberRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private CommentRepository commentRepository;

    @BeforeEach
    void setUp() {
        commentRepository.deleteAll();
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

    private Post createPost(Course c, User author) {
        return postRepository.save(Post.builder()
                .course(c).author(author).title("Post").content("content")
                .type(PostType.MATERIAL).files(new ArrayList<>()).comments(new ArrayList<>()).build());
    }

    private Comment createComment(Post post, User author, String text) {
        return commentRepository.save(Comment.builder().post(post).author(author).text(text).build());
    }

    private String base(UUID courseId, UUID postId) {
        return "/api/v1/courses/" + courseId + "/posts/" + postId + "/comments";
    }

    private HttpEntity<CreateCommentRequest> commentReq(String token, String text) {
        CreateCommentRequest r = new CreateCommentRequest();
        r.setText(text);
        HttpHeaders h = bearerHeaders(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(r, h);
    }

    @Nested
    class ListPostComments {

        @Test
        void returns200_withComments() {
            String token = registerAndGetToken("user1");
            User u = user("user1");
            Course c = createCourse("C1");
            addMember(c, u, CourseRole.STUDENT);
            Post p = createPost(c, u);
            createComment(p, u, "Hello");

            var resp = restTemplate.exchange(base(c.getId(), p.getId()),
                    HttpMethod.GET, auth(token),
                    new ParameterizedTypeReference<PageDto<CommentDto>>() {});

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().getContent()).hasSize(1);
            assertThat(resp.getBody().getContent().get(0).getText()).isEqualTo("Hello");
        }

        @Test
        void returns403_whenNotMember() {
            String token = registerAndGetToken("user1");
            Course c = createCourse("C1");
            Post p = createPost(c, user("user1"));

            var resp = restTemplate.exchange(base(c.getId(), p.getId()),
                    HttpMethod.GET, auth(token), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns401_withoutToken() {
            Course c = courseRepository.save(Course.builder().name("C1").build());
            var resp = restTemplate.getForEntity(base(c.getId(), UUID.randomUUID()), String.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    class CreatePostComment {

        @Test
        void returns201_whenMemberCreatesComment() {
            String token = registerAndGetToken("user1");
            User u = user("user1");
            Course c = createCourse("C1");
            addMember(c, u, CourseRole.STUDENT);
            Post p = createPost(c, u);

            var resp = restTemplate.exchange(base(c.getId(), p.getId()),
                    HttpMethod.POST, commentReq(token, "Nice post"),
                    CommentDto.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().getText()).isEqualTo("Nice post");
            assertThat(resp.getBody().getId()).isNotNull();
        }

        @Test
        void returns403_whenNotMember() {
            String token = registerAndGetToken("user1");
            Course c = createCourse("C1");
            Post p = createPost(c, user("user1"));

            var resp = restTemplate.exchange(base(c.getId(), p.getId()),
                    HttpMethod.POST, commentReq(token, "text"), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns400_whenBlankText() {
            String token = registerAndGetToken("user1");
            User u = user("user1");
            Course c = createCourse("C1");
            addMember(c, u, CourseRole.STUDENT);
            Post p = createPost(c, u);

            var resp = restTemplate.exchange(base(c.getId(), p.getId()),
                    HttpMethod.POST, commentReq(token, ""), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void returns404_whenPostNotFound() {
            String token = registerAndGetToken("user1");
            User u = user("user1");
            Course c = createCourse("C1");
            addMember(c, u, CourseRole.STUDENT);

            var resp = restTemplate.exchange(base(c.getId(), UUID.randomUUID()),
                    HttpMethod.POST, commentReq(token, "text"), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class UpdatePostComment {

        @Test
        void returns200_whenAuthorUpdates() {
            String token = registerAndGetToken("user1");
            User u = user("user1");
            Course c = createCourse("C1");
            addMember(c, u, CourseRole.STUDENT);
            Post p = createPost(c, u);
            Comment comment = createComment(p, u, "Old");

            var resp = restTemplate.exchange(
                    base(c.getId(), p.getId()) + "/" + comment.getId(),
                    HttpMethod.PUT, commentReq(token, "New"),
                    CommentDto.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().getText()).isEqualTo("New");
        }

        @Test
        void returns403_whenNotAuthor() {
            String t1 = registerAndGetToken("user1");
            String t2 = registerAndGetToken("user2");
            User u1 = user("user1");
            User u2 = user("user2");
            Course c = createCourse("C1");
            addMember(c, u1, CourseRole.STUDENT);
            addMember(c, u2, CourseRole.STUDENT);
            Post p = createPost(c, u1);
            Comment comment = createComment(p, u1, "Mine");

            var resp = restTemplate.exchange(
                    base(c.getId(), p.getId()) + "/" + comment.getId(),
                    HttpMethod.PUT, commentReq(t2, "Hacked"), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns404_whenCommentNotFound() {
            String token = registerAndGetToken("user1");
            User u = user("user1");
            Course c = createCourse("C1");
            addMember(c, u, CourseRole.STUDENT);
            Post p = createPost(c, u);

            var resp = restTemplate.exchange(
                    base(c.getId(), p.getId()) + "/" + UUID.randomUUID(),
                    HttpMethod.PUT, commentReq(token, "text"), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class DeletePostComment {

        @Test
        void returns204_whenAuthorDeletes() {
            String token = registerAndGetToken("user1");
            User u = user("user1");
            Course c = createCourse("C1");
            addMember(c, u, CourseRole.STUDENT);
            Post p = createPost(c, u);
            Comment comment = createComment(p, u, "Bye");

            var resp = restTemplate.exchange(
                    base(c.getId(), p.getId()) + "/" + comment.getId(),
                    HttpMethod.DELETE, auth(token), Void.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(commentRepository.findById(comment.getId())).isEmpty();
        }

        @Test
        void returns204_whenTeacherDeletesAnyComment() {
            String tToken = registerAndGetToken("teacher1");
            String sToken = registerAndGetToken("student1");
            User teacher = user("teacher1");
            User student = user("student1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post p = createPost(c, teacher);
            Comment comment = createComment(p, student, "Student comment");

            var resp = restTemplate.exchange(
                    base(c.getId(), p.getId()) + "/" + comment.getId(),
                    HttpMethod.DELETE, auth(tToken), Void.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(commentRepository.findById(comment.getId())).isEmpty();
        }

        @Test
        void returns403_whenStudentDeletesOtherComment() {
            String t1 = registerAndGetToken("user1");
            String t2 = registerAndGetToken("user2");
            User u1 = user("user1");
            User u2 = user("user2");
            Course c = createCourse("C1");
            addMember(c, u1, CourseRole.STUDENT);
            addMember(c, u2, CourseRole.STUDENT);
            Post p = createPost(c, u1);
            Comment comment = createComment(p, u1, "Mine");

            var resp = restTemplate.exchange(
                    base(c.getId(), p.getId()) + "/" + comment.getId(),
                    HttpMethod.DELETE, auth(t2), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns404_whenCommentNotFound() {
            String token = registerAndGetToken("user1");
            User u = user("user1");
            Course c = createCourse("C1");
            addMember(c, u, CourseRole.STUDENT);
            Post p = createPost(c, u);

            var resp = restTemplate.exchange(
                    base(c.getId(), p.getId()) + "/" + UUID.randomUUID(),
                    HttpMethod.DELETE, auth(token), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
