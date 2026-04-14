package com.classroom.core.controller;

import com.classroom.core.TestcontainersConfig;
import com.classroom.core.dto.auth.AuthResponse;
import com.classroom.core.dto.auth.RegisterRequest;
import com.classroom.core.dto.team.TeamRequirementTemplateDto;
import com.classroom.core.dto.team.TemplateApplyResultDto;
import com.classroom.core.model.Course;
import com.classroom.core.model.CourseCategory;
import com.classroom.core.model.CourseMember;
import com.classroom.core.model.CourseRole;
import com.classroom.core.model.CourseTeam;
import com.classroom.core.model.Post;
import com.classroom.core.model.PostType;
import com.classroom.core.model.TeamFormationMode;
import com.classroom.core.model.User;
import com.classroom.core.repository.CourseCategoryRepository;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
import com.classroom.core.repository.CourseTeamRepository;
import com.classroom.core.repository.PostRepository;
import com.classroom.core.repository.TeamGradeRepository;
import com.classroom.core.repository.TeamRequirementTemplateRepository;
import com.classroom.core.repository.TeamStudentGradeRepository;
import com.classroom.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class TeamRequirementTemplateControllerIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseMemberRepository courseMemberRepository;

    @Autowired
    private CourseCategoryRepository courseCategoryRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CourseTeamRepository courseTeamRepository;

    @Autowired
    private TeamGradeRepository teamGradeRepository;

    @Autowired
    private TeamRequirementTemplateRepository teamRequirementTemplateRepository;

    @Autowired
    private TeamStudentGradeRepository teamStudentGradeRepository;

    @BeforeEach
    void setUp() {
        teamStudentGradeRepository.deleteAll();
        teamGradeRepository.deleteAll();
        teamRequirementTemplateRepository.deleteAll();
        courseMemberRepository.deleteAll();
        courseTeamRepository.deleteAll();
        postRepository.deleteAll();
        courseCategoryRepository.deleteAll();
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

    private CourseCategory createCategory(Course course, String title) {
        return courseCategoryRepository.save(
                CourseCategory.builder()
                        .course(course)
                        .title(title)
                        .description("desc")
                        .active(true)
                        .build()
        );
    }

    private Post createTaskPost(Course course, User teacher, String title) {
        return postRepository.save(
                Post.builder()
                        .course(course)
                        .author(teacher)
                        .title(title)
                        .content("content")
                        .type(PostType.TASK)
                        .teamFormationMode(TeamFormationMode.FREE)
                        .build()
        );
    }

    private CourseTeam createPostTeam(Course course, Post post, String name) {
        return courseTeamRepository.save(
                CourseTeam.builder()
                        .course(course)
                        .post(post)
                        .name(name)
                        .selfEnrollmentEnabled(false)
                        .build()
        );
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpEntity<Map<String, Object>> createTemplateRequest(String token,
                                                                  String name,
                                                                  Integer min,
                                                                  Integer max,
                                                                  String requiredCategoryId) {
        HttpHeaders headers = bearerHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = Map.of(
                "name", name,
                "description", "template",
                "minTeamSize", min,
                "maxTeamSize", max,
                "requiredCategoryId", requiredCategoryId,
                "requireAudio", false,
                "requireVideo", false
        );
        return new HttpEntity<>(payload, headers);
    }

    private HttpEntity<Map<String, String>> applyTemplateRequest(String token, String postId) {
        HttpHeaders headers = bearerHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(Map.of("postId", postId), headers);
    }

    @Nested
    class TemplateFlow {

        @Test
        void createAndApplyTemplateToAssignment() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            registerAndGetToken("student1", "password123");
            registerAndGetToken("student2", "password123");

            User teacher = userByUsername("teacher1");
            User student1 = userByUsername("student1");
            User student2 = userByUsername("student2");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            CourseMember m1 = addMember(course, student1, CourseRole.STUDENT);
            CourseMember m2 = addMember(course, student2, CourseRole.STUDENT);

            CourseCategory categoryA = createCategory(course, "A");
            m1.setCategory(categoryA);
            m2.setCategory(categoryA);
            courseMemberRepository.saveAll(List.of(m1, m2));

            Post post = createTaskPost(course, teacher, "Task 1");
            CourseTeam team = createPostTeam(course, post, "Team A");
            m1.setTeam(team);
            m2.setTeam(team);
            courseMemberRepository.saveAll(List.of(m1, m2));

            ResponseEntity<TeamRequirementTemplateDto> createResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/team-requirement-templates",
                    HttpMethod.POST,
                    createTemplateRequest(teacherToken, "Template A", 1, 2, categoryA.getId().toString()),
                    TeamRequirementTemplateDto.class
            );

            assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(createResponse.getBody()).isNotNull();
            assertThat(createResponse.getBody().getId()).isNotNull();

            ResponseEntity<TemplateApplyResultDto> applyResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/team-requirement-templates/" + createResponse.getBody().getId() + "/apply",
                    HttpMethod.POST,
                    applyTemplateRequest(teacherToken, post.getId().toString()),
                    TemplateApplyResultDto.class
            );

            assertThat(applyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(applyResponse.getBody()).isNotNull();
                assertThat(applyResponse.getBody().getAppliedMode()).isEqualTo(TeamFormationMode.DRAFT);

                Post refreshedPost = postRepository.findById(post.getId()).orElseThrow();
                assertThat(refreshedPost.getTeamFormationMode()).isEqualTo(TeamFormationMode.DRAFT);
                assertThat(refreshedPost.getTeamRequirementTemplate()).isNotNull();
                assertThat(refreshedPost.getTeamRequirementTemplate().getId()).isEqualTo(createResponse.getBody().getId());

            CourseTeam refreshedTeam = courseTeamRepository.findByIdWithCategories(team.getId()).orElseThrow();
            assertThat(refreshedTeam.getMaxSize()).isEqualTo(2);
            assertThat(refreshedTeam.getCategories()).extracting(CourseCategory::getId).containsExactly(categoryA.getId());
        }

            @Test
            void rejectsTemplateCreationWhenMinGreaterThanMax() {
                String teacherToken = registerAndGetToken("teacher1", "password123");
                User teacher = userByUsername("teacher1");

                Course course = createCourseEntity("Java", "Course");
                addMember(course, teacher, CourseRole.TEACHER);
                CourseCategory categoryA = createCategory(course, "A");

                ResponseEntity<String> createResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/team-requirement-templates",
                    HttpMethod.POST,
                    createTemplateRequest(teacherToken, "Invalid Template", 4, 2, categoryA.getId().toString()),
                    String.class
                );

                assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            }

            @Test
            void rejectsIncompatibleTemplateWhenExistingTeamExceedsMaxSize() {
                String teacherToken = registerAndGetToken("teacher1", "password123");
                registerAndGetToken("student1", "password123");
                registerAndGetToken("student2", "password123");
                registerAndGetToken("student3", "password123");

                User teacher = userByUsername("teacher1");
                User student1 = userByUsername("student1");
                User student2 = userByUsername("student2");
                User student3 = userByUsername("student3");

                Course course = createCourseEntity("Java", "Course");
                addMember(course, teacher, CourseRole.TEACHER);
                CourseMember m1 = addMember(course, student1, CourseRole.STUDENT);
                CourseMember m2 = addMember(course, student2, CourseRole.STUDENT);
                CourseMember m3 = addMember(course, student3, CourseRole.STUDENT);

                CourseCategory categoryA = createCategory(course, "A");
                m1.setCategory(categoryA);
                m2.setCategory(categoryA);
                m3.setCategory(categoryA);

                Post post = createTaskPost(course, teacher, "Task 1");
                CourseTeam team = createPostTeam(course, post, "Team A");
                m1.setTeam(team);
                m2.setTeam(team);
                m3.setTeam(team);
                courseMemberRepository.saveAll(List.of(m1, m2, m3));

                ResponseEntity<TeamRequirementTemplateDto> createResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/team-requirement-templates",
                    HttpMethod.POST,
                    createTemplateRequest(teacherToken, "Template Max 2", 1, 2, categoryA.getId().toString()),
                    TeamRequirementTemplateDto.class
                );
                assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

                ResponseEntity<String> applyResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/team-requirement-templates/" + createResponse.getBody().getId() + "/apply",
                    HttpMethod.POST,
                    applyTemplateRequest(teacherToken, post.getId().toString()),
                    String.class
                );

                assertThat(applyResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            }

        @Test
        void rejectsIncompatibleTemplateWhenRequiredCategoryHasNoStudents() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            registerAndGetToken("student1", "password123");

            User teacher = userByUsername("teacher1");
            User student1 = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            addMember(course, student1, CourseRole.STUDENT);

            CourseCategory categoryA = createCategory(course, "A");
            CourseCategory categoryB = createCategory(course, "B");
            CourseMember member = courseMemberRepository.findByCourseIdAndUserId(course.getId(), student1.getId()).orElseThrow();
            member.setCategory(categoryA);
            courseMemberRepository.save(member);

            Post post = createTaskPost(course, teacher, "Task 1");

            ResponseEntity<TeamRequirementTemplateDto> createResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/team-requirement-templates",
                    HttpMethod.POST,
                    createTemplateRequest(teacherToken, "Template B", 1, 2, categoryB.getId().toString()),
                    TeamRequirementTemplateDto.class
            );
            assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            ResponseEntity<String> applyResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/team-requirement-templates/" + createResponse.getBody().getId() + "/apply",
                    HttpMethod.POST,
                    applyTemplateRequest(teacherToken, post.getId().toString()),
                    String.class
            );

            assertThat(applyResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
