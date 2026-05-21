package com.classroom.core.controller;

import com.classroom.core.TestcontainersConfig;
import com.classroom.core.dto.auth.AuthResponse;
import com.classroom.core.dto.auth.RegisterRequest;
import com.classroom.core.dto.grading.CriteriaGradeResultDto;
import com.classroom.core.dto.grading.CriteriaGradeSubmissionDto;
import com.classroom.core.dto.grading.CriterionConfigDto;
import com.classroom.core.dto.grading.CriterionGradeEntryDto;
import com.classroom.core.dto.grading.GradingConfigDto;
import com.classroom.core.dto.grading.UpsertGradingConfigRequest;
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
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class MultiCriteriaGradingControllerIT {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseMemberRepository courseMemberRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private SolutionRepository solutionRepository;
    @Autowired private GradingConfigRepository gradingConfigRepository;
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
        h.setContentType(MediaType.APPLICATION_JSON);
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

    private String configBase(UUID courseId, UUID postId) {
        return "/api/v1/courses/" + courseId + "/posts/" + postId + "/grading-config";
    }

    private String criteriaGradesBase(UUID courseId, UUID postId, UUID solutionId) {
        return "/api/v1/courses/" + courseId + "/posts/" + postId + "/solutions/" + solutionId + "/criteria-grades";
    }

    private String publishBase(UUID courseId, UUID postId) {
        return "/api/v1/courses/" + courseId + "/posts/" + postId + "/criteria-grades/publish";
    }

    private String recalculateBase(UUID courseId, UUID postId, UUID solutionId) {
        return "/api/v1/courses/" + courseId + "/posts/" + postId + "/solutions/" + solutionId + "/recalculate";
    }

    private String decompositionBase(UUID courseId, UUID postId, UUID solutionId) {
        return "/api/v1/courses/" + courseId + "/posts/" + postId + "/solutions/" + solutionId + "/grade-decomposition";
    }

    @Nested
    class ConfigLifecycle {

        @Test
        void canCreateAndRetrieveGradingConfig() {
            String tToken = registerAndGetToken("teacher1");
            User teacher = user("teacher1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            Post task = createTask(c, teacher);

            UpsertGradingConfigRequest req = UpsertGradingConfigRequest.builder()
                    .maxGrade(new BigDecimal("100"))
                    .criteria(List.of(
                            CriterionConfigDto.builder()
                                    .type(CriterionType.POINTS)
                                    .title("Quality")
                                    .maxPoints(new BigDecimal("50"))
                                    .build()
                    ))
                    .build();

            HttpEntity<UpsertGradingConfigRequest> entity = new HttpEntity<>(req, bearerHeaders(tToken));
            var resp = restTemplate.exchange(configBase(c.getId(), task.getId()), HttpMethod.PUT, entity, GradingConfigDto.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().getMaxGrade()).isEqualByComparingTo("100");

            var getResp = restTemplate.exchange(configBase(c.getId(), task.getId()), HttpMethod.GET, auth(tToken), GradingConfigDto.class);
            assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getResp.getBody().getMaxGrade()).isEqualByComparingTo("100");
        }
    }

    @Nested
    class CriteriaGradesAndRecalculation {

        @Test
        void teacherCanGradeAndPublish_thenRecalculate() {
            String tToken = registerAndGetToken("teacher1");
            String sToken = registerAndGetToken("student1");
            User teacher = user("teacher1");
            User student = user("student1");
            Course c = createCourse("C1");
            addMember(c, teacher, CourseRole.TEACHER);
            addMember(c, student, CourseRole.STUDENT);
            Post task = createTask(c, teacher);
            Solution sol = createSolution(task, student);

            // Create config with one criterion
            UpsertGradingConfigRequest configReq = new UpsertGradingConfigRequest();
            configReq.setMaxGrade(new BigDecimal("100"));
            configReq.setCriteria(List.of(
                    CriterionConfigDto.builder()
                            .type(CriterionType.POINTS)
                            .title("Quality")
                            .maxPoints(new BigDecimal("50"))
                            .build()
            ));

            HttpEntity<UpsertGradingConfigRequest> configEntity = new HttpEntity<>(configReq, bearerHeaders(tToken));
            var configResp = restTemplate.exchange(configBase(c.getId(), task.getId()), HttpMethod.PUT, configEntity, GradingConfigDto.class);
            UUID criterionId = configResp.getBody().getCriteria().get(0).getId();

            // Submit criteria grades
            CriteriaGradeSubmissionDto gradeReq = CriteriaGradeSubmissionDto.builder()
                    .grades(List.of(
                            CriterionGradeEntryDto.builder()
                                    .criterionId(criterionId)
                                    .value(new BigDecimal("40"))
                                    .build()
                    ))
                    .build();

            HttpEntity<CriteriaGradeSubmissionDto> gradeEntity = new HttpEntity<>(gradeReq, bearerHeaders(tToken));
            var gradeResp = restTemplate.exchange(criteriaGradesBase(c.getId(), task.getId(), sol.getId()), HttpMethod.PUT, gradeEntity, CriteriaGradeResultDto.class);
            assertThat(gradeResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(gradeResp.getBody().getBasicScore()).isEqualByComparingTo("40");
            assertThat(gradeResp.getBody().getIsPublished()).isFalse();

            // Publish grades
            var publishResp = restTemplate.exchange(publishBase(c.getId(), task.getId()), HttpMethod.POST, auth(tToken), Void.class);
            assertThat(publishResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            // Student can view decomposition
            var decompResp = restTemplate.exchange(decompositionBase(c.getId(), task.getId(), sol.getId()), HttpMethod.GET, auth(sToken), CriteriaGradeResultDto.class);
            assertThat(decompResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(decompResp.getBody().getIsPublished()).isTrue();
            assertThat(decompResp.getBody().getBasicScore()).isEqualByComparingTo("40");

            // Teacher cannot modify published assessment directly
            var modifyResp = restTemplate.exchange(criteriaGradesBase(c.getId(), task.getId(), sol.getId()), HttpMethod.PUT, gradeEntity, String.class);
            assertThat(modifyResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            // Update config (simulate change)
            configReq.setCriteria(List.of(
                    CriterionConfigDto.builder()
                            .type(CriterionType.POINTS)
                            .title("Quality")
                            .maxPoints(new BigDecimal("60"))
                            .build()
            ));
            restTemplate.exchange(configBase(c.getId(), task.getId()), HttpMethod.PUT, new HttpEntity<>(configReq, bearerHeaders(tToken)), GradingConfigDto.class);

            // Recalculate
            var recalcResp = restTemplate.exchange(recalculateBase(c.getId(), task.getId(), sol.getId()), HttpMethod.POST, auth(tToken), CriteriaGradeResultDto.class);
            assertThat(recalcResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(recalcResp.getBody().getIsPublished()).isFalse();
            // 40 pts on 60 max -> still 40 basic score, but maxGrade changed to 100 (same maxGrade)
            assertThat(recalcResp.getBody().getBasicScore()).isEqualByComparingTo("40");
        }
    }
}
