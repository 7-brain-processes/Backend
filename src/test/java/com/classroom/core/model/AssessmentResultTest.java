package com.classroom.core.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssessmentResultTest {

    private GradingConfigVersion versionWithOneCriterion(BigDecimal maxGrade, BigDecimal maxPoints) {
        GradingConfigVersion version = GradingConfigVersion.builder()
                .id(UUID.randomUUID())
                .versionNumber(1)
                .maxGrade(maxGrade)
                .build();
        VersionedCriterion vc = VersionedCriterion.builder()
                .id(UUID.randomUUID())
                .configVersion(version)
                .type(CriterionType.POINTS)
                .title("Quality")
                .maxPoints(maxPoints)
                .weight(BigDecimal.ONE)
                .sortOrder(0)
                .build();
        version.replaceCriteria(List.of(vc));
        return version;
    }

    private AssessmentResult resultWithGrade(BigDecimal value, BigDecimal maxGrade, BigDecimal maxPoints) {
        return resultWithGrade(value, maxGrade, maxPoints, null);
    }

    private AssessmentResult resultWithGrade(BigDecimal value, BigDecimal maxGrade, BigDecimal maxPoints, Instant submittedAt) {
        GradingConfigVersion version = versionWithOneCriterion(maxGrade, maxPoints);
        Solution solution = Solution.builder().id(UUID.randomUUID()).submittedAt(submittedAt).build();
        AssessmentResult result = AssessmentResult.builder()
                .solution(solution)
                .configVersion(version)
                .build();
        AssessmentCriterionGrade grade = AssessmentCriterionGrade.builder()
                .assessmentResult(result)
                .versionedCriterion(version.getCriteria().get(0))
                .value(value)
                .build();
        result.replaceCriterionGrades(List.of(grade));
        return result;
    }

    @Test
    void assess_shouldComputeBasicScoreAndFinalScore() {
        AssessmentResult result = resultWithGrade(new BigDecimal("15"), new BigDecimal("100"), new BigDecimal("20"));

        result.assess(result.getCriterionGrades(), new ModifierConfig(null));

        assertThat(result.getBasicScore()).isEqualByComparingTo("15");
        assertThat(result.getFinalScore()).isEqualByComparingTo("15");
        assertThat(result.getModifierDelta()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getGradedAt()).isNotNull();
    }

    @Test
    void assess_shouldClampFinalScoreToMaxGrade() {
        AssessmentResult result = resultWithGrade(new BigDecimal("25"), new BigDecimal("20"), new BigDecimal("20"));

        result.assess(result.getCriterionGrades(), new ModifierConfig(null));

        assertThat(result.getFinalScore()).isEqualByComparingTo("20");
    }

    @Test
    void assess_shouldApplyModifierDelta() {
        Instant submittedAt = Instant.parse("2026-01-02T00:00:00Z");
        AssessmentResult result = resultWithGrade(new BigDecimal("15"), new BigDecimal("100"), new BigDecimal("20"), submittedAt);
        DeadlineModifier deadline = new DeadlineModifier(true, Instant.parse("2026-01-05T00:00:00Z"), null, null, new BigDecimal("2"), null, null);
        ModifierConfig config = new ModifierConfig(deadline);

        result.assess(result.getCriterionGrades(), config);

        assertThat(result.getModifierDelta()).isEqualByComparingTo("6"); // 3 days early * 2
        assertThat(result.getFinalScore()).isEqualByComparingTo("21"); // 15 + 6, clamped to 100
    }

    @Test
    void assess_shouldClampToZero_whenNegative() {
        Instant submittedAt = Instant.parse("2026-01-05T00:00:00Z");
        AssessmentResult result = resultWithGrade(new BigDecimal("5"), new BigDecimal("100"), new BigDecimal("20"), submittedAt);
        DeadlineModifier deadline = new DeadlineModifier(true, null, Instant.parse("2026-01-01T00:00:00Z"), null, null, new BigDecimal("10"), null);
        ModifierConfig config = new ModifierConfig(deadline);

        result.assess(result.getCriterionGrades(), config);

        assertThat(result.getFinalScore()).isEqualByComparingTo(BigDecimal.ZERO); // 5 - 40 = -35, clamped to 0
    }

    @Test
    void publish_shouldSetPublished() {
        AssessmentResult result = resultWithGrade(BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("20"));
        result.publish();
        assertThat(result.getIsPublished()).isTrue();
    }

    @Test
    void publish_shouldThrow_whenAlreadyPublished() {
        AssessmentResult result = resultWithGrade(BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("20"));
        result.publish();
        assertThatThrownBy(result::publish)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already published");
    }

    @Test
    void unpublish_shouldSetUnpublished() {
        AssessmentResult result = resultWithGrade(BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("20"));
        result.publish();
        result.unpublish();
        assertThat(result.getIsPublished()).isFalse();
    }

    @Test
    void replaceCriterionGrades_shouldReplaceAll() {
        AssessmentResult result = resultWithGrade(BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("20"));
        assertThat(result.getCriterionGrades()).hasSize(1);

        GradingConfigVersion version = result.getConfigVersion();
        AssessmentCriterionGrade newGrade = AssessmentCriterionGrade.builder()
                .assessmentResult(result)
                .versionedCriterion(version.getCriteria().get(0))
                .value(new BigDecimal("10"))
                .build();
        result.replaceCriterionGrades(List.of(newGrade));

        assertThat(result.getCriterionGrades()).hasSize(1);
        assertThat(result.getCriterionGrades().get(0).getValue()).isEqualByComparingTo("10");
    }

    @Test
    void computeBasicScore_shouldReturnZero_whenNoGrades() {
        GradingConfigVersion version = versionWithOneCriterion(new BigDecimal("100"), new BigDecimal("20"));
        Solution solution = Solution.builder().id(UUID.randomUUID()).build();
        AssessmentResult result = AssessmentResult.builder()
                .solution(solution)
                .configVersion(version)
                .build();

        assertThat(result.computeBasicScore()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
