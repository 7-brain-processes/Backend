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
}
