package com.classroom.core.controller;

import com.classroom.core.TestcontainersConfig;
import com.classroom.core.dto.auth.AuthResponse;
import com.classroom.core.dto.auth.RegisterRequest;
import com.classroom.core.dto.file.FileDto;
import com.classroom.core.model.*;
import com.classroom.core.repository.*;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class PostMaterialControllerIT {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseMemberRepository courseMemberRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private PostFileRepository postFileRepository;
    @Autowired private TestDatabaseCleaner testDatabaseCleaner;

    @BeforeEach
    void setUp() {
        testDatabaseCleaner.clean();
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

    private PostFile createFile(Post post) {
        return postFileRepository.save(PostFile.builder()
                .post(post).originalName("test.txt").contentType("text/plain")
                .sizeBytes(100).storagePath("/uploads/test.txt").build());
    }

    private String base(UUID courseId, UUID postId) {
        return "/api/v1/courses/" + courseId + "/posts/" + postId + "/materials";
    }

    private HttpEntity<MultiValueMap<String, Object>> uploadReq(String token) {
        HttpHeaders h = bearerHeaders(token);
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new org.springframework.core.io.ByteArrayResource("hello".getBytes()) {
            @Override public String getFilename() { return "test.txt"; }
        });
        return new HttpEntity<>(body, h);
    }

    @Nested
    class ListPostMaterials {

        @Test
        void returns200_withFiles() {
            String token = registerAndGetToken("teacher1");
            User teacher = user("teacher1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            Post p = createPost(c, teacher);
            createFile(p);

            var resp = restTemplate.exchange(base(c.getId(), p.getId()),
                    HttpMethod.GET, auth(token),
                    new ParameterizedTypeReference<List<FileDto>>() {});

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).hasSize(1);
            assertThat(resp.getBody().get(0).getOriginalName()).isEqualTo("test.txt");
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
        void returns404_whenPostNotFound() {
            String token = registerAndGetToken("user1");
            User u = user("user1");
            Course c = createCourse("C1");
            addMember(c, u, CourseRole.STUDENT);

            var resp = restTemplate.exchange(base(c.getId(), UUID.randomUUID()),
                    HttpMethod.GET, auth(token), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class UploadPostMaterial {

        @Test
        void returns201_whenTeacherUploads() {
            String token = registerAndGetToken("teacher1");
            User teacher = user("teacher1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            Post p = createPost(c, teacher);

            var resp = restTemplate.exchange(base(c.getId(), p.getId()),
                    HttpMethod.POST, uploadReq(token), FileDto.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().getOriginalName()).isEqualTo("test.txt");
        }

        @Test
        void returns403_whenStudentUploads() {
            String tToken = registerAndGetToken("teacher1");
            String sToken = registerAndGetToken("student1");
            User teacher = user("teacher1");
            User student = user("student1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post p = createPost(c, teacher);

            var resp = restTemplate.exchange(base(c.getId(), p.getId()),
                    HttpMethod.POST, uploadReq(sToken), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns404_whenPostNotFound() {
            String token = registerAndGetToken("teacher1");
            User teacher = user("teacher1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);

            var resp = restTemplate.exchange(base(c.getId(), UUID.randomUUID()),
                    HttpMethod.POST, uploadReq(token), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class DeletePostMaterial {

        @Test
        void returns204_whenTeacherDeletes() {
            String token = registerAndGetToken("teacher1");
            User teacher = user("teacher1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            Post p = createPost(c, teacher);
            PostFile f = createFile(p);

            var resp = restTemplate.exchange(
                    base(c.getId(), p.getId()) + "/" + f.getId(),
                    HttpMethod.DELETE, auth(token), Void.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(postFileRepository.findById(f.getId())).isEmpty();
        }

        @Test
        void returns403_whenStudentDeletes() {
            String tToken = registerAndGetToken("teacher1");
            String sToken = registerAndGetToken("student1");
            User teacher = user("teacher1");
            User student = user("student1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post p = createPost(c, teacher);
            PostFile f = createFile(p);

            var resp = restTemplate.exchange(
                    base(c.getId(), p.getId()) + "/" + f.getId(),
                    HttpMethod.DELETE, auth(sToken), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns404_whenFileNotFound() {
            String token = registerAndGetToken("teacher1");
            User teacher = user("teacher1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            Post p = createPost(c, teacher);

            var resp = restTemplate.exchange(
                    base(c.getId(), p.getId()) + "/" + UUID.randomUUID(),
                    HttpMethod.DELETE, auth(token), String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
