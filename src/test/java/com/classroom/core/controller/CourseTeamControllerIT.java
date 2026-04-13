package com.classroom.core.controller;

import com.classroom.core.TestcontainersConfig;
import com.classroom.core.dto.auth.AuthResponse;
import com.classroom.core.dto.auth.RegisterRequest;
import com.classroom.core.dto.team.CourseTeamDto;
import com.classroom.core.dto.team.CourseTeamAvailabilityDto;
import com.classroom.core.dto.team.CreateCourseTeamRequest;
import com.classroom.core.dto.team.EnrollmentResponseDto;
import com.classroom.core.model.Course;
import com.classroom.core.model.CourseCategory;
import com.classroom.core.model.CourseMember;
import com.classroom.core.model.CourseRole;
import com.classroom.core.model.CourseTeam;
import com.classroom.core.model.Post;
import com.classroom.core.model.PostType;
import com.classroom.core.model.User;
import com.classroom.core.repository.CourseCategoryRepository;
import com.classroom.core.repository.CourseMemberRepository;
import com.classroom.core.repository.CourseRepository;
import com.classroom.core.repository.CourseTeamRepository;
import com.classroom.core.repository.PostRepository;
import com.classroom.core.repository.TeamGradeRepository;
import com.classroom.core.repository.TeamStudentGradeRepository;
import com.classroom.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class CourseTeamControllerIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseMemberRepository courseMemberRepository;

    @Autowired
    private CourseTeamRepository courseTeamRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CourseCategoryRepository courseCategoryRepository;

        @Autowired
        private TeamGradeRepository teamGradeRepository;

        @Autowired
        private TeamStudentGradeRepository teamStudentGradeRepository;

    @BeforeEach
    void setUp() {
        teamStudentGradeRepository.deleteAll();
        teamGradeRepository.deleteAll();
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

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpEntity<CreateCourseTeamRequest> authorizedCreateRequest(String token, String name, List<UUID> memberIds) {
        CreateCourseTeamRequest request = new CreateCourseTeamRequest();
        request.setName(name);
        request.setMemberIds(memberIds);

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

    private CourseTeam createTeam(Course course, String name) {
        return courseTeamRepository.save(
                CourseTeam.builder()
                        .course(course)
                        .name(name)
                        .build()
        );
    }

    private Post createPost(Course course, User author, String title) {
        return postRepository.save(
                Post.builder()
                        .course(course)
                        .author(author)
                        .title(title)
                        .content("content")
                        .type(PostType.TASK)
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

    @Nested
    class CreateCourseTeam {

        @Test
        void returns201_andAssignsStudentsToTeam() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            registerAndGetToken("student1", "password123");
            registerAndGetToken("student2", "password123");

            User teacher = userByUsername("teacher1");
            User student1 = userByUsername("student1");
            User student2 = userByUsername("student2");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            addMember(course, student1, CourseRole.STUDENT);
            addMember(course, student2, CourseRole.STUDENT);

            ResponseEntity<CourseTeamDto> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/teams",
                    HttpMethod.POST,
                    authorizedCreateRequest(teacherToken, "Team A", List.of(student1.getId(), student2.getId())),
                    CourseTeamDto.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isNotNull();
            assertThat(response.getBody().getName()).isEqualTo("Team A");
            assertThat(response.getBody().getMembersCount()).isEqualTo(2);
            assertThat(response.getBody().getMembers()).hasSize(2);

            UUID teamId = response.getBody().getId();

            CourseMember student1Membership = courseMemberRepository
                    .findByCourseIdAndUserId(course.getId(), student1.getId())
                    .orElseThrow();
            CourseMember student2Membership = courseMemberRepository
                    .findByCourseIdAndUserId(course.getId(), student2.getId())
                    .orElseThrow();

            assertThat(student1Membership.getTeam()).isNotNull();
            assertThat(student2Membership.getTeam()).isNotNull();
            assertThat(student1Membership.getTeam().getId()).isEqualTo(teamId);
            assertThat(student2Membership.getTeam().getId()).isEqualTo(teamId);
        }

        @Test
        void returns403_whenStudentTriesToCreateTeam() {
            String studentToken = registerAndGetToken("student1", "password123");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, student, CourseRole.STUDENT);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/teams",
                    HttpMethod.POST,
                    authorizedCreateRequest(studentToken, "Team A", List.of()),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void returns404_whenMemberIsNotInCourse() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            registerAndGetToken("student1", "password123");
            registerAndGetToken("outsider", "password123");

            User teacher = userByUsername("teacher1");
            User student = userByUsername("student1");
            User outsider = userByUsername("outsider");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            addMember(course, student, CourseRole.STUDENT);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/teams",
                    HttpMethod.POST,
                    authorizedCreateRequest(teacherToken, "Team A", List.of(student.getId(), outsider.getId())),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void returns409_whenStudentAlreadyAssignedToAnotherTeam() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            registerAndGetToken("student1", "password123");

            User teacher = userByUsername("teacher1");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            CourseMember studentMember = addMember(course, student, CourseRole.STUDENT);

            CourseTeam existingTeam = createTeam(course, "Existing Team");
            studentMember.setTeam(existingTeam);
            courseMemberRepository.save(studentMember);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/teams",
                    HttpMethod.POST,
                    authorizedCreateRequest(teacherToken, "Team B", List.of(student.getId())),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Nested
    class ListCourseTeams {

        @Test
        void returns200_andTeamsForCourseMember() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            registerAndGetToken("student1", "password123");

            User teacher = userByUsername("teacher1");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            CourseMember studentMember = addMember(course, student, CourseRole.STUDENT);

            CourseTeam team = createTeam(course, "Team A");
            studentMember.setTeam(team);
            courseMemberRepository.save(studentMember);

            ResponseEntity<List<CourseTeamDto>> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/teams",
                    HttpMethod.GET,
                    authorizedRequest(teacherToken),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).getName()).isEqualTo("Team A");
            assertThat(response.getBody().get(0).getMembersCount()).isEqualTo(1);
        }

        @Test
        void returns403_whenCurrentUserIsNotMember() {
            String outsiderToken = registerAndGetToken("outsider", "password123");
            registerAndGetToken("teacher1", "password123");

            User teacher = userByUsername("teacher1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/teams",
                    HttpMethod.GET,
                    authorizedRequest(outsiderToken),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    class SelfEnrollment {

        @Test
        void listAvailableTeamsShowsStatus() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            String studentToken = registerAndGetToken("student1", "password123");

            User teacher = userByUsername("teacher1");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            addMember(course, student, CourseRole.STUDENT);

            Post post = createPost(course, teacher, "Task 1");

            CourseTeam team = courseTeamRepository.save(CourseTeam.builder()
                    .course(course)
                    .post(post)
                    .name("Team A")
                    .selfEnrollmentEnabled(true)
                    .maxSize(2)
                    .build());

            ResponseEntity<List<CourseTeamAvailabilityDto>> response = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId() + "/teams",
                    HttpMethod.GET,
                    authorizedRequest(studentToken),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).getName()).isEqualTo("Team A");
            assertThat(response.getBody().get(0).isFull()).isFalse();
            assertThat(response.getBody().get(0).isStudentMember()).isFalse();
        }

        @Test
        void enrollAndLeaveTeamFlow() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            String studentToken = registerAndGetToken("student1", "password123");

            User teacher = userByUsername("teacher1");
            User student = userByUsername("student1");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            addMember(course, student, CourseRole.STUDENT);

            Post post = createPost(course, teacher, "Task 1");

            CourseTeam team = courseTeamRepository.save(CourseTeam.builder()
                    .course(course)
                    .post(post)
                    .name("Team A")
                    .selfEnrollmentEnabled(true)
                    .maxSize(2)
                    .build());

            ResponseEntity<EnrollmentResponseDto> enrollResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId() + "/teams/" + team.getId() + "/enroll",
                    HttpMethod.POST,
                    authorizedRequest(studentToken),
                    EnrollmentResponseDto.class
            );

            assertThat(enrollResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(enrollResponse.getBody()).isNotNull();
            assertThat(enrollResponse.getBody().isSuccess()).isTrue();
            assertThat(enrollResponse.getBody().getTeam().getCurrentMembers()).isEqualTo(1);

            CourseMember studentMember = courseMemberRepository.findByCourseIdAndUserId(course.getId(), student.getId()).orElseThrow();
            assertThat(studentMember.getTeam()).isNotNull().extracting("id").isEqualTo(team.getId());

            ResponseEntity<EnrollmentResponseDto> leaveResponse = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId() + "/teams/" + team.getId() + "/leave",
                    HttpMethod.DELETE,
                    authorizedRequest(studentToken),
                    EnrollmentResponseDto.class
            );

            assertThat(leaveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(leaveResponse.getBody()).isNotNull();
            assertThat(leaveResponse.getBody().isSuccess()).isTrue();

            studentMember = courseMemberRepository.findByCourseIdAndUserId(course.getId(), student.getId()).orElseThrow();
            assertThat(studentMember.getTeam()).isNull();
        }

        @Test
        void prohibitsMaxSizeOverflowOrDoubleTeamOrCategoryMismatch() {
            String teacherToken = registerAndGetToken("teacher1", "password123");
            String studentToken1 = registerAndGetToken("student1", "password123");
            String studentToken2 = registerAndGetToken("student2", "password123");
            String studentToken3 = registerAndGetToken("student3", "password123");

            User teacher = userByUsername("teacher1");
            User student1 = userByUsername("student1");
            User student2 = userByUsername("student2");
            User student3 = userByUsername("student3");

            Course course = createCourseEntity("Java", "Course");
            addMember(course, teacher, CourseRole.TEACHER);
            CourseMember member1 = addMember(course, student1, CourseRole.STUDENT);
            CourseMember member2 = addMember(course, student2, CourseRole.STUDENT);
            CourseMember member3 = addMember(course, student3, CourseRole.STUDENT);

            CourseCategory categoryA = createCategory(course, "A");
            CourseCategory categoryB = createCategory(course, "B");
            member1.setCategory(categoryA);
            member2.setCategory(categoryA);
            member3.setCategory(categoryB);
            courseMemberRepository.saveAll(List.of(member1, member2, member3));

            Post post = createPost(course, teacher, "Task 1");

            CourseTeam teamA = courseTeamRepository.save(CourseTeam.builder()
                    .course(course)
                    .post(post)
                    .name("Team A")
                    .selfEnrollmentEnabled(true)
                    .maxSize(2)
                    .categories(Set.of(categoryA))
                    .build());

            CourseTeam teamB = courseTeamRepository.save(CourseTeam.builder()
                    .course(course)
                    .post(post)
                    .name("Team B")
                    .selfEnrollmentEnabled(true)
                    .maxSize(2)
                    .categories(Set.of(categoryA))
                    .build());

            ResponseEntity<EnrollmentResponseDto> join1 = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId() + "/teams/" + teamA.getId() + "/enroll",
                    HttpMethod.POST,
                    authorizedRequest(studentToken1),
                    EnrollmentResponseDto.class
            );
            assertThat(join1.getStatusCode()).isEqualTo(HttpStatus.OK);

            ResponseEntity<EnrollmentResponseDto> join2 = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId() + "/teams/" + teamA.getId() + "/enroll",
                    HttpMethod.POST,
                    authorizedRequest(studentToken2),
                    EnrollmentResponseDto.class
            );
            assertThat(join2.getStatusCode()).isEqualTo(HttpStatus.OK);

            ResponseEntity<String> join3 = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId() + "/teams/" + teamA.getId() + "/enroll",
                    HttpMethod.POST,
                    authorizedRequest(studentToken3),
                    String.class
            );
            assertThat(join3.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            ResponseEntity<String> join1b = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId() + "/teams/" + teamB.getId() + "/enroll",
                    HttpMethod.POST,
                    authorizedRequest(studentToken3),
                    String.class
            );
            assertThat(join1b.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            ResponseEntity<String> join2b = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId() + "/teams/" + teamB.getId() + "/enroll",
                    HttpMethod.POST,
                    authorizedRequest(studentToken2),
                    String.class
            );
            assertThat(join2b.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            ResponseEntity<EnrollmentResponseDto> leave = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId() + "/teams/" + teamA.getId() + "/leave",
                    HttpMethod.DELETE,
                    authorizedRequest(studentToken1),
                    EnrollmentResponseDto.class
            );
            assertThat(leave.getStatusCode()).isEqualTo(HttpStatus.OK);

            assertThat(courseMemberRepository.findByCourseIdAndUserId(course.getId(), student2.getId()).get().getTeam()).isNotNull();
            ResponseEntity<String> join2c = restTemplate.exchange(
                    "/api/v1/courses/" + course.getId() + "/posts/" + post.getId() + "/teams/" + teamB.getId() + "/enroll",
                    HttpMethod.POST,
                    authorizedRequest(studentToken2),
                    String.class
            );
            assertThat(join2c.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}

