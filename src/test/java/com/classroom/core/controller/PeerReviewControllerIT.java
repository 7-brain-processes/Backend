package com.classroom.core.controller;

import com.classroom.core.TestcontainersConfig;
import com.classroom.core.dto.auth.AuthResponse;
import com.classroom.core.dto.auth.RegisterRequest;
import com.classroom.core.dto.grading.CriterionConfigDto;
import com.classroom.core.dto.grading.GradingConfigDto;
import com.classroom.core.dto.grading.UpsertGradingConfigRequest;
import com.classroom.core.dto.peerreview.PeerReviewAssignmentDto;
import com.classroom.core.dto.peerreview.SubmitPeerReviewRequest;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class PeerReviewControllerIT {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseMemberRepository courseMemberRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private SolutionRepository solutionRepository;
    @Autowired private GradingConfigRepository gradingConfigRepository;
    @Autowired private PeerReviewConfigRepository peerReviewConfigRepository;
    @Autowired private PeerReviewAssignmentRepository peerReviewAssignmentRepository;
    @Autowired private PeerReviewRepository peerReviewRepository;
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

    private String peerReviewBase(UUID courseId, UUID postId) {
        return "/api/v1/courses/" + courseId + "/posts/" + postId + "/peer-review";
    }

    private String peerReviewAssignmentsBase(UUID courseId, UUID postId) {
        return peerReviewBase(courseId, postId) + "/assignments";
    }

    private GradingConfigDto setupPeerReviewConfig(String teacherToken, Course course, Post task) {
        Instant now = Instant.now();
        UpsertGradingConfigRequest req = UpsertGradingConfigRequest.builder()
                .maxGrade(new BigDecimal("100"))
                .criteria(List.of(
                        CriterionConfigDto.builder()
                                .type(CriterionType.PEER_REVIEW)
                                .title("Peer Review")
                                .maxPoints(new BigDecimal("50"))
                                .peerReviewConfigRequest(com.classroom.core.dto.peerreview.PeerReviewConfigRequest.builder()
                                        .reviewersCount(1)
                                        .scoringStrategy(com.classroom.core.model.PeerReviewScoringStrategy.AVERAGE)
                                        .firstDeadline(now.plusSeconds(3600))
                                        .secondDeadline(now.plusSeconds(7200))
                                        .redistributionFactor(2)
                                        .build())
                                .build()
                ))
                .build();

        HttpEntity<UpsertGradingConfigRequest> entity = new HttpEntity<>(req, bearerHeaders(teacherToken));
        var resp = restTemplate.exchange(
                "/api/v1/courses/" + course.getId() + "/posts/" + task.getId() + "/grading-config",
                HttpMethod.PUT, entity, GradingConfigDto.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    @Nested
    class DistributionAndSubmission {

        @Test
        void teacherCanDistributeAndStudentsCanSubmitReviews() {
            String tToken = registerAndGetToken("teacher1");
            String s1Token = registerAndGetToken("student1");
            String s2Token = registerAndGetToken("student2");

            User teacher = user("teacher1");
            User student1 = user("student1");
            User student2 = user("student2");

            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student1, CourseRole.STUDENT);
            addMember(c, student2, CourseRole.STUDENT);

            Post task = createTask(c, teacher);
            Solution sol1 = createSolution(task, student1);
            Solution sol2 = createSolution(task, student2);

            setupPeerReviewConfig(tToken, c, task);

            // Teacher distributes round 1
            var distributeResp = restTemplate.exchange(
                    peerReviewBase(c.getId(), task.getId()) + "/distribute",
                    HttpMethod.POST, auth(tToken), Void.class);
            assertThat(distributeResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            // Each student sees exactly one assignment
            var s1Assignments = restTemplate.exchange(
                    peerReviewBase(c.getId(), task.getId()) + "/my-assignments",
                    HttpMethod.GET, auth(s1Token),
                    new ParameterizedTypeReference<List<PeerReviewAssignmentDto>>() {});
            assertThat(s1Assignments.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(s1Assignments.getBody()).hasSize(1);
            assertThat(s1Assignments.getBody().get(0).getRevieweeSolutionId()).isNotEqualTo(sol1.getId());

            var s2Assignments = restTemplate.exchange(
                    peerReviewBase(c.getId(), task.getId()) + "/my-assignments",
                    HttpMethod.GET, auth(s2Token),
                    new ParameterizedTypeReference<List<PeerReviewAssignmentDto>>() {});
            assertThat(s2Assignments.getBody()).hasSize(1);
            assertThat(s2Assignments.getBody().get(0).getRevieweeSolutionId()).isNotEqualTo(sol2.getId());

            // Student 1 submits review
            UUID assignmentId = s1Assignments.getBody().get(0).getAssignmentId();
            SubmitPeerReviewRequest reviewReq = SubmitPeerReviewRequest.builder()
                    .grade(new BigDecimal("40"))
                    .comment("Nice")
                    .build();
            HttpEntity<SubmitPeerReviewRequest> reviewEntity = new HttpEntity<>(reviewReq, jsonHeaders(s1Token));
            var submitResp = restTemplate.exchange(
                    peerReviewAssignmentsBase(c.getId(), task.getId()) + "/" + assignmentId,
                    HttpMethod.PUT, reviewEntity, PeerReviewAssignmentDto.class);
            assertThat(submitResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(submitResp.getBody().getStatus()).isEqualTo(PeerReviewAssignmentStatus.COMPLETED);
        }

        @Test
        void distributionRequiresAtLeastTwoSolutions() {
            String tToken = registerAndGetToken("teacher1");
            String sToken = registerAndGetToken("student1");

            User teacher = user("teacher1");
            User student = user("student1");

            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);

            Post task = createTask(c, teacher);
            createSolution(task, student);
            setupPeerReviewConfig(tToken, c, task);

            var resp = restTemplate.exchange(
                    peerReviewBase(c.getId(), task.getId()) + "/distribute",
                    HttpMethod.POST, auth(tToken), String.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void studentCannotReviewOwnWork() {
            String tToken = registerAndGetToken("teacher1");
            String s1Token = registerAndGetToken("student1");
            String s2Token = registerAndGetToken("student2");

            User teacher = user("teacher1");
            User student1 = user("student1");
            User student2 = user("student2");

            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student1, CourseRole.STUDENT);
            addMember(c, student2, CourseRole.STUDENT);

            Post task = createTask(c, teacher);
            createSolution(task, student1);
            createSolution(task, student2);

            // Force a self-review assignment directly to test the service guard
            GradingConfigDto configDto = setupPeerReviewConfig(tToken, c, task);
            UUID criterionId = configDto.getCriteria().get(0).getId();
            var config = peerReviewConfigRepository.findByCriterionId(criterionId).orElseThrow();
            PeerReviewAssignment selfAssignment = PeerReviewAssignment.builder()
                    .peerReviewConfig(config)
                    .reviewerUser(student1)
                    .revieweeSolution(solutionRepository.findAllByPostIdAndStatusIn(
                            task.getId(), List.of(SolutionStatus.SUBMITTED)).get(0))
                    .round(1)
                    .status(PeerReviewAssignmentStatus.PENDING)
                    .build();
            peerReviewAssignmentRepository.save(selfAssignment);

            SubmitPeerReviewRequest reviewReq = SubmitPeerReviewRequest.builder()
                    .grade(new BigDecimal("40"))
                    .build();
            HttpEntity<SubmitPeerReviewRequest> reviewEntity = new HttpEntity<>(reviewReq, jsonHeaders(s1Token));
            var resp = restTemplate.exchange(
                    peerReviewAssignmentsBase(c.getId(), task.getId()) + "/" + selfAssignment.getId(),
                    HttpMethod.PUT, reviewEntity, String.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    class ApplyGrades {

        @Test
        void teacherCanApplyPeerReviewGrades() {
            String tToken = registerAndGetToken("teacher1");
            String s1Token = registerAndGetToken("student1");
            String s2Token = registerAndGetToken("student2");

            User teacher = user("teacher1");
            User student1 = user("student1");
            User student2 = user("student2");

            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student1, CourseRole.STUDENT);
            addMember(c, student2, CourseRole.STUDENT);

            Post task = createTask(c, teacher);
            createSolution(task, student1);
            createSolution(task, student2);

            setupPeerReviewConfig(tToken, c, task);

            restTemplate.exchange(
                    peerReviewBase(c.getId(), task.getId()) + "/distribute",
                    HttpMethod.POST, auth(tToken), Void.class);

            var s1Assignments = restTemplate.exchange(
                    peerReviewBase(c.getId(), task.getId()) + "/my-assignments",
                    HttpMethod.GET, auth(s1Token),
                    new ParameterizedTypeReference<List<PeerReviewAssignmentDto>>() {});

            UUID assignmentId = s1Assignments.getBody().get(0).getAssignmentId();
            SubmitPeerReviewRequest reviewReq = SubmitPeerReviewRequest.builder()
                    .grade(new BigDecimal("40"))
                    .build();
            restTemplate.exchange(
                    peerReviewAssignmentsBase(c.getId(), task.getId()) + "/" + assignmentId,
                    HttpMethod.PUT, new HttpEntity<>(reviewReq, jsonHeaders(s1Token)), PeerReviewAssignmentDto.class);

            var applyResp = restTemplate.exchange(
                    peerReviewBase(c.getId(), task.getId()) + "/apply-grades",
                    HttpMethod.POST, auth(tToken), Void.class);
            assertThat(applyResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders h = bearerHeaders(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
