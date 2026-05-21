package com.classroom.core.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreTest {

    @Test
    void clamp_shouldLeaveValueUnchanged_whenWithinBounds() {
        Score score = Score.of(new BigDecimal("75"), new BigDecimal("100"));
        assertThat(score.clamp().getValue()).isEqualByComparingTo("75");
    }

    @Test
    void clamp_shouldEnforceZeroMinimum() {
        Score score = Score.of(new BigDecimal("-10"), new BigDecimal("100"));
        assertThat(score.clamp().getValue()).isEqualByComparingTo("0");
    }

    @Test
    void clamp_shouldEnforceMaxMaximum() {
        Score score = Score.of(new BigDecimal("150"), new BigDecimal("100"));
        assertThat(score.clamp().getValue()).isEqualByComparingTo("100");
    }

    @Test
    void clamp_shouldPreserveMax() {
        Score clamped = Score.of(new BigDecimal("50"), new BigDecimal("100")).clamp();
        assertThat(clamped.getMax()).isEqualByComparingTo("100");
    }
}
