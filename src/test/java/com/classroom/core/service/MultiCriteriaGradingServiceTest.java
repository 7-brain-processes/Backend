package com.classroom.core.service;

import com.classroom.core.dto.grading.*;
import com.classroom.core.exception.BadRequestException;
import com.classroom.core.exception.ForbiddenException;
import com.classroom.core.exception.ResourceNotFoundException;
import com.classroom.core.model.*;
import com.classroom.core.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiCriteriaGradingServiceTest {

    @Mock
    private GradingConfigRepository gradingConfigRepository;
    @Mock
    private CriterionRepository criterionRepository;
    @Mock
    private GradingConfigVersionRepository gradingConfigVersionRepository;
    @Mock
    private VersionedCriterionRepository versionedCriterionRepository;
    @Mock
    private AssessmentResultRepository assessmentResultRepository;
    @Mock
    private AssessmentCriterionGradeRepository assessmentCriterionGradeRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private SolutionRepository solutionRepository;
    @Mock
    private CourseMemberRepository courseMemberRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private GradingGuard guard;
    private GradingDtoMapper mapper;
    private MultiCriteriaGradingService gradingService;

    private final UUID courseId = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID solutionId = UUID.randomUUID();

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        guard = new GradingGuard(courseRepository, courseMemberRepository, postRepository, solutionRepository, gradingConfigRepository);
        mapper = new GradingDtoMapper(objectMapper);
        gradingService = new MultiCriteriaGradingService(
                gradingConfigRepository,
                criterionRepository,
                gradingConfigVersionRepository,
                versionedCriterionRepository,
                assessmentResultRepository,
                assessmentCriterionGradeRepository,
                objectMapper,
                guard,
                mapper,
                eventPublisher
        );
    }

    @Test
    void getGradingConfig_shouldReturnDto_whenConfigExists() {
        Course course = Course.builder().id(courseId).build();
        Post post = Post.builder().id(postId).course(course).type(PostType.TASK).build();
        GradingConfig config = GradingConfig.builder()
                .id(UUID.randomUUID())
                .post(post)
                .maxGrade(new BigDecimal("100"))
                .resultsVisible(true)
                .build();

        when(courseRepository.existsById(courseId)).thenReturn(true);
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(CourseMember.builder().role(CourseRole.STUDENT).build()));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(gradingConfigRepository.findByPostId(postId)).thenReturn(Optional.of(config));

        GradingConfigDto result = gradingService.getGradingConfig(courseId, postId, userId);

        assertThat(result).isNotNull();
        assertThat(result.getPostId()).isEqualTo(postId);
        assertThat(result.getMaxGrade()).isEqualByComparingTo("100");
        assertThat(result.getResultsVisible()).isTrue();
    }

    @Test
    void upsertGradingConfig_shouldCreateNewConfig() {
        Course course = Course.builder().id(courseId).build();
        Post post = Post.builder().id(postId).course(course).type(PostType.TASK).build();

        when(courseRepository.existsById(courseId)).thenReturn(true);
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(CourseMember.builder().role(CourseRole.TEACHER).build()));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(gradingConfigRepository.findByPostId(postId)).thenReturn(Optional.empty());
        when(gradingConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpsertGradingConfigRequest request = UpsertGradingConfigRequest.builder()
                .maxGrade(new BigDecimal("100"))
                .criteria(List.of(
                        CriterionConfigDto.builder()
                                .type(CriterionType.POINTS)
                                .title("Code quality")
                                .maxPoints(new BigDecimal("20"))
                                .build()
                ))
                .resultsVisible(true)
                .build();

        GradingConfigDto result = gradingService.upsertGradingConfig(courseId, postId, request, userId);

        assertThat(result.getMaxGrade()).isEqualByComparingTo("100");
        assertThat(result.getCriteria()).hasSize(1);
        assertThat(result.getCriteria().get(0).getTitle()).isEqualTo("Code quality");
    }

    @Test
    void upsertCriteriaGrades_shouldComputeBasicScoreForPoints() {
        Course course = Course.builder().id(courseId).build();
        Post post = Post.builder().id(postId).course(course).type(PostType.TASK).build();
        User student = User.builder().id(UUID.randomUUID()).build();
        Solution solution = Solution.builder().id(solutionId).post(post).student(student).build();
        UUID criterionId = UUID.randomUUID();
        Criterion criterion = Criterion.builder()
                .id(criterionId)
                .type(CriterionType.POINTS)
                .maxPoints(new BigDecimal("20"))
                .weight(BigDecimal.ONE)
                .title("Quality")
                .sortOrder(0)
                .build();
        GradingConfig config = GradingConfig.builder()
                .id(UUID.randomUUID())
                .post(post)
                .maxGrade(new BigDecimal("100"))
                .criteria(List.of(criterion))
                .build();

        when(courseRepository.existsById(courseId)).thenReturn(true);
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(CourseMember.builder().role(CourseRole.TEACHER).build()));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));
        when(gradingConfigRepository.findByPostId(postId)).thenReturn(Optional.of(config));
        when(criterionRepository.findByGradingConfigIdOrderBySortOrderAsc(config.getId()))
                .thenReturn(List.of(criterion));
        when(assessmentResultRepository.findBySolutionId(solutionId)).thenReturn(Optional.empty());
        when(gradingConfigVersionRepository.findTopByPostIdOrderByVersionNumberDesc(postId))
                .thenReturn(Optional.empty());
        when(gradingConfigVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(assessmentResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CriteriaGradeSubmissionDto request = CriteriaGradeSubmissionDto.builder()
                .grades(List.of(
                        CriterionGradeEntryDto.builder()
                                .criterionId(criterionId)
                                .value(new BigDecimal("15"))
                                .build()
                ))
                .build();

        CriteriaGradeResultDto result = gradingService.upsertCriteriaGrades(courseId, postId, solutionId, request, userId);

        assertThat(result.getBasicScore()).isEqualByComparingTo("15");
        assertThat(result.getCriteriaGrades()).hasSize(1);
        assertThat(result.getCriteriaGrades().get(0).getComputedPoints()).isEqualByComparingTo("15");
    }

    @Test
    void upsertCriteriaGrades_shouldComputeBasicScoreForYesNo() {
        Course course = Course.builder().id(courseId).build();
        Post post = Post.builder().id(postId).course(course).type(PostType.TASK).build();
        User student = User.builder().id(UUID.randomUUID()).build();
        Solution solution = Solution.builder().id(solutionId).post(post).student(student).build();
        UUID criterionId = UUID.randomUUID();
        Criterion criterion = Criterion.builder()
                .id(criterionId)
                .type(CriterionType.YES_NO)
                .maxPoints(new BigDecimal("10"))
                .weight(BigDecimal.ONE)
                .title("Passed")
                .sortOrder(0)
                .build();
        GradingConfig config = GradingConfig.builder()
                .id(UUID.randomUUID())
                .post(post)
                .maxGrade(new BigDecimal("100"))
                .criteria(List.of(criterion))
                .build();

        when(courseRepository.existsById(courseId)).thenReturn(true);
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(CourseMember.builder().role(CourseRole.TEACHER).build()));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));
        when(gradingConfigRepository.findByPostId(postId)).thenReturn(Optional.of(config));
        when(criterionRepository.findByGradingConfigIdOrderBySortOrderAsc(config.getId()))
                .thenReturn(List.of(criterion));
        when(assessmentResultRepository.findBySolutionId(solutionId)).thenReturn(Optional.empty());
        when(gradingConfigVersionRepository.findTopByPostIdOrderByVersionNumberDesc(postId))
                .thenReturn(Optional.empty());
        when(gradingConfigVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(assessmentResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CriteriaGradeSubmissionDto request = CriteriaGradeSubmissionDto.builder()
                .grades(List.of(
                        CriterionGradeEntryDto.builder()
                                .criterionId(criterionId)
                                .value(BigDecimal.ONE)
                                .build()
                ))
                .build();

        CriteriaGradeResultDto result = gradingService.upsertCriteriaGrades(courseId, postId, solutionId, request, userId);

        assertThat(result.getBasicScore()).isEqualByComparingTo("10");
    }

    @Test
    void upsertCriteriaGrades_shouldComputeBasicScoreForPercentage() {
        Course course = Course.builder().id(courseId).build();
        Post post = Post.builder().id(postId).course(course).type(PostType.TASK).build();
        User student = User.builder().id(UUID.randomUUID()).build();
        Solution solution = Solution.builder().id(solutionId).post(post).student(student).build();
        UUID criterionId = UUID.randomUUID();
        Criterion criterion = Criterion.builder()
                .id(criterionId)
                .type(CriterionType.PERCENTAGE)
                .maxPoints(new BigDecimal("20"))
                .weight(BigDecimal.ONE)
                .title("Coverage")
                .sortOrder(0)
                .build();
        GradingConfig config = GradingConfig.builder()
                .id(UUID.randomUUID())
                .post(post)
                .maxGrade(new BigDecimal("100"))
                .criteria(List.of(criterion))
                .build();

        when(courseRepository.existsById(courseId)).thenReturn(true);
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(CourseMember.builder().role(CourseRole.TEACHER).build()));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));
        when(gradingConfigRepository.findByPostId(postId)).thenReturn(Optional.of(config));
        when(criterionRepository.findByGradingConfigIdOrderBySortOrderAsc(config.getId()))
                .thenReturn(List.of(criterion));
        when(assessmentResultRepository.findBySolutionId(solutionId)).thenReturn(Optional.empty());
        when(gradingConfigVersionRepository.findTopByPostIdOrderByVersionNumberDesc(postId))
                .thenReturn(Optional.empty());
        when(gradingConfigVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(assessmentResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CriteriaGradeSubmissionDto request = CriteriaGradeSubmissionDto.builder()
                .grades(List.of(
                        CriterionGradeEntryDto.builder()
                                .criterionId(criterionId)
                                .value(new BigDecimal("75"))
                                .build()
                ))
                .build();

        CriteriaGradeResultDto result = gradingService.upsertCriteriaGrades(courseId, postId, solutionId, request, userId);

        assertThat(result.getBasicScore()).isEqualByComparingTo("15");
    }

    @Test
    void upsertCriteriaGrades_shouldRejectInvalidYesNoValue() {
        Course course = Course.builder().id(courseId).build();
        Post post = Post.builder().id(postId).course(course).type(PostType.TASK).build();
        User student = User.builder().id(UUID.randomUUID()).build();
        Solution solution = Solution.builder().id(solutionId).post(post).student(student).build();
        UUID criterionId = UUID.randomUUID();
        Criterion criterion = Criterion.builder()
                .id(criterionId)
                .type(CriterionType.YES_NO)
                .maxPoints(new BigDecimal("10"))
                .title("Passed")
                .sortOrder(0)
                .build();
        GradingConfig config = GradingConfig.builder()
                .id(UUID.randomUUID())
                .post(post)
                .maxGrade(new BigDecimal("100"))
                .criteria(List.of(criterion))
                .build();

        when(courseRepository.existsById(courseId)).thenReturn(true);
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(CourseMember.builder().role(CourseRole.TEACHER).build()));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));
        when(gradingConfigRepository.findByPostId(postId)).thenReturn(Optional.of(config));
        when(criterionRepository.findByGradingConfigIdOrderBySortOrderAsc(config.getId()))
                .thenReturn(List.of(criterion));
        when(assessmentResultRepository.findBySolutionId(solutionId)).thenReturn(Optional.empty());
        when(gradingConfigVersionRepository.findTopByPostIdOrderByVersionNumberDesc(postId))
                .thenReturn(Optional.empty());
        when(gradingConfigVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CriteriaGradeSubmissionDto request = CriteriaGradeSubmissionDto.builder()
                .grades(List.of(
                        CriterionGradeEntryDto.builder()
                                .criterionId(criterionId)
                                .value(new BigDecimal("2"))
                                .build()
                ))
                .build();

        assertThatThrownBy(() -> gradingService.upsertCriteriaGrades(courseId, postId, solutionId, request, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("YES_NO criterion value must be 0 or 1");
    }

    @Test
    void upsertCriteriaGrades_shouldRejectMissingCriterionGrade() {
        Course course = Course.builder().id(courseId).build();
        Post post = Post.builder().id(postId).course(course).type(PostType.TASK).build();
        User student = User.builder().id(UUID.randomUUID()).build();
        Solution solution = Solution.builder().id(solutionId).post(post).student(student).build();
        UUID criterionId = UUID.randomUUID();
        Criterion criterion = Criterion.builder()
                .id(criterionId)
                .type(CriterionType.POINTS)
                .maxPoints(new BigDecimal("20"))
                .title("Quality")
                .sortOrder(0)
                .build();
        GradingConfig config = GradingConfig.builder()
                .id(UUID.randomUUID())
                .post(post)
                .maxGrade(new BigDecimal("100"))
                .criteria(List.of(criterion))
                .build();

        when(courseRepository.existsById(courseId)).thenReturn(true);
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(CourseMember.builder().role(CourseRole.TEACHER).build()));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));
        when(gradingConfigRepository.findByPostId(postId)).thenReturn(Optional.of(config));
        when(criterionRepository.findByGradingConfigIdOrderBySortOrderAsc(config.getId()))
                .thenReturn(List.of(criterion));
        when(assessmentResultRepository.findBySolutionId(solutionId)).thenReturn(Optional.empty());

        CriteriaGradeSubmissionDto request = CriteriaGradeSubmissionDto.builder()
                .grades(List.of())
                .build();

        assertThatThrownBy(() -> gradingService.upsertCriteriaGrades(courseId, postId, solutionId, request, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Grades must be submitted for exactly the configured criteria");
    }

    @Test
    void upsertCriteriaGrades_shouldReject_whenAlreadyPublished() {
        Course course = Course.builder().id(courseId).build();
        Post post = Post.builder().id(postId).course(course).type(PostType.TASK).build();
        User student = User.builder().id(UUID.randomUUID()).build();
        Solution solution = Solution.builder().id(solutionId).post(post).student(student).build();
        UUID criterionId = UUID.randomUUID();
        Criterion criterion = Criterion.builder()
                .id(criterionId)
                .type(CriterionType.POINTS)
                .maxPoints(new BigDecimal("20"))
                .title("Quality")
                .sortOrder(0)
                .build();
        GradingConfig config = GradingConfig.builder()
                .id(UUID.randomUUID())
                .post(post)
                .maxGrade(new BigDecimal("100"))
                .criteria(List.of(criterion))
                .build();
        AssessmentResult published = AssessmentResult.builder()
                .solution(solution)
                .isPublished(true)
                .build();

        when(courseRepository.existsById(courseId)).thenReturn(true);
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(CourseMember.builder().role(CourseRole.TEACHER).build()));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));
        when(gradingConfigRepository.findByPostId(postId)).thenReturn(Optional.of(config));
        when(assessmentResultRepository.findBySolutionId(solutionId)).thenReturn(Optional.of(published));

        CriteriaGradeSubmissionDto request = CriteriaGradeSubmissionDto.builder()
                .grades(List.of(
                        CriterionGradeEntryDto.builder()
                                .criterionId(criterionId)
                                .value(new BigDecimal("15"))
                                .build()
                ))
                .build();

        assertThatThrownBy(() -> gradingService.upsertCriteriaGrades(courseId, postId, solutionId, request, userId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Published assessment cannot be modified directly");
    }

    @Test
    void recalculateAssessment_shouldCreateNewVersionAndRecompute() {
        Course course = Course.builder().id(courseId).build();
        Post post = Post.builder().id(postId).course(course).type(PostType.TASK).build();
        User student = User.builder().id(UUID.randomUUID()).build();
        Solution solution = Solution.builder().id(solutionId).post(post).student(student).build();
        Criterion criterion = Criterion.builder()
                .id(UUID.randomUUID())
                .type(CriterionType.POINTS)
                .maxPoints(new BigDecimal("20"))
                .weight(BigDecimal.ONE)
                .title("Quality")
                .sortOrder(0)
                .build();
        GradingConfig config = GradingConfig.builder()
                .id(UUID.randomUUID())
                .post(post)
                .maxGrade(new BigDecimal("100"))
                .criteria(List.of(criterion))
                .build();

        GradingConfigVersion oldVersion = GradingConfigVersion.builder()
                .id(UUID.randomUUID())
                .post(post)
                .versionNumber(1)
                .maxGrade(new BigDecimal("100"))
                .build();
        VersionedCriterion oldVc = VersionedCriterion.builder()
                .id(UUID.randomUUID())
                .configVersion(oldVersion)
                .type(CriterionType.POINTS)
                .title("Quality")
                .maxPoints(new BigDecimal("20"))
                .weight(BigDecimal.ONE)
                .sortOrder(0)
                .build();
        oldVersion.setCriteria(List.of(oldVc));

        AssessmentResult result = AssessmentResult.builder()
                .id(UUID.randomUUID())
                .solution(solution)
                .configVersion(oldVersion)
                .isPublished(true)
                .criterionGrades(new java.util.ArrayList<>(List.of(
                        AssessmentCriterionGrade.builder()
                                .assessmentResult(null)
                                .versionedCriterion(oldVc)
                                .value(new BigDecimal("15"))
                                .build()
                )))
                .build();

        when(courseRepository.existsById(courseId)).thenReturn(true);
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(CourseMember.builder().role(CourseRole.TEACHER).build()));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));
        when(gradingConfigRepository.findByPostId(postId)).thenReturn(Optional.of(config));
        when(assessmentResultRepository.findBySolutionId(solutionId)).thenReturn(Optional.of(result));
        when(criterionRepository.findByGradingConfigIdOrderBySortOrderAsc(config.getId()))
                .thenReturn(List.of(criterion));
        when(gradingConfigVersionRepository.findTopByPostIdOrderByVersionNumberDesc(postId))
                .thenReturn(Optional.of(oldVersion));
        when(assessmentResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CriteriaGradeResultDto dto = gradingService.recalculateAssessment(courseId, postId, solutionId, userId);

        assertThat(dto.getBasicScore()).isEqualByComparingTo("15");
        assertThat(dto.getIsPublished()).isFalse();
    }

    @Test
    void deleteGradingConfig_shouldDelete_whenTeacher() {
        Course course = Course.builder().id(courseId).build();
        Post post = Post.builder().id(postId).course(course).type(PostType.TASK).build();
        GradingConfig config = GradingConfig.builder().id(UUID.randomUUID()).post(post).build();

        when(courseRepository.existsById(courseId)).thenReturn(true);
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(CourseMember.builder().role(CourseRole.TEACHER).build()));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(gradingConfigRepository.findByPostId(postId)).thenReturn(Optional.of(config));

        gradingService.deleteGradingConfig(courseId, postId, userId);

        verify(gradingConfigRepository).delete(config);
    }

    @Test
    void upsertGradingConfig_shouldReject_whenStudent() {
        when(courseRepository.existsById(courseId)).thenReturn(true);
        when(courseMemberRepository.findByCourseIdAndUserId(courseId, userId))
                .thenReturn(Optional.of(CourseMember.builder().role(CourseRole.STUDENT).build()));

        UpsertGradingConfigRequest request = UpsertGradingConfigRequest.builder()
                .maxGrade(new BigDecimal("100"))
                .criteria(List.of())
                .build();

        assertThatThrownBy(() -> gradingService.upsertGradingConfig(courseId, postId, request, userId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only teachers can manage grading configuration");
    }
}
