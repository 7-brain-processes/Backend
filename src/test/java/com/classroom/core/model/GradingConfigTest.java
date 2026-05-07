package com.classroom.core.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GradingConfigTest {

    @Test
    void replaceCriteria_shouldSetParentAndSortOrder() {
        GradingConfig config = GradingConfig.builder().build();

        Criterion c1 = Criterion.builder().title("A").build();
        Criterion c2 = Criterion.builder().title("B").build();

        config.replaceCriteria(List.of(c1, c2));

        assertThat(config.getCriteria()).hasSize(2);
        assertThat(c1.getGradingConfig()).isSameAs(config);
        assertThat(c2.getGradingConfig()).isSameAs(config);
        assertThat(c1.getSortOrder()).isEqualTo(0);
        assertThat(c2.getSortOrder()).isEqualTo(1);
    }

    @Test
    void replaceCriteria_shouldRemoveOldCriteria() {
        GradingConfig config = GradingConfig.builder().build();
        Criterion old = Criterion.builder().title("Old").build();
        config.getCriteria().add(old);

        Criterion fresh = Criterion.builder().title("Fresh").build();
        config.replaceCriteria(List.of(fresh));

        assertThat(config.getCriteria()).hasSize(1);
        assertThat(config.getCriteria().get(0).getTitle()).isEqualTo("Fresh");
    }

    @Test
    void computeBasicScore_shouldSumCriterionPoints() {
        Criterion c1 = Criterion.builder()
                .id(java.util.UUID.randomUUID())
                .type(CriterionType.POINTS)
                .maxPoints(new BigDecimal("20"))
                .weight(BigDecimal.ONE)
                .build();
        Criterion c2 = Criterion.builder()
                .id(java.util.UUID.randomUUID())
                .type(CriterionType.YES_NO)
                .maxPoints(new BigDecimal("10"))
                .weight(BigDecimal.ONE)
                .build();

        GradingConfig config = GradingConfig.builder()
                .criteria(new java.util.ArrayList<>(List.of(c1, c2)))
                .build();

        Solution solution = Solution.builder().build();
        CriterionGrade g1 = CriterionGrade.builder()
                .solution(solution)
                .criterion(c1)
                .value(new BigDecimal("15"))
                .build();
        CriterionGrade g2 = CriterionGrade.builder()
                .solution(solution)
                .criterion(c2)
                .value(BigDecimal.ONE)
                .build();

        BigDecimal result = config.computeBasicScore(List.of(g1, g2));

        assertThat(result).isEqualByComparingTo("25");
    }

    @Test
    void computeBasicScore_shouldTreatMissingGradesAsZero() {
        Criterion c1 = Criterion.builder()
                .type(CriterionType.POINTS)
                .maxPoints(new BigDecimal("20"))
                .weight(BigDecimal.ONE)
                .build();

        GradingConfig config = GradingConfig.builder()
                .criteria(new java.util.ArrayList<>(List.of(c1)))
                .build();

        BigDecimal result = config.computeBasicScore(List.of());

        assertThat(result).isEqualByComparingTo("0");
    }
}
