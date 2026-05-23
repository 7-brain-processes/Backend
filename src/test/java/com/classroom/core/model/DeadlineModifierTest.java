package com.classroom.core.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DeadlineModifierTest {

    @Test
    void computeDelta_shouldReturnZero_whenDisabled() {
        DeadlineModifier mod = new DeadlineModifier(false, null, null, null, null, null, null);
        assertThat(mod.computeDelta(Instant.now())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void computeDelta_shouldReturnZero_whenNoSubmittedAt() {
        DeadlineModifier mod = new DeadlineModifier(true, Instant.parse("2026-01-01T00:00:00Z"), null, null, null, null, null);
        assertThat(mod.computeDelta(null)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void computeDelta_shouldApplyLatePenalty() {
        Instant hardDeadline = Instant.parse("2026-01-01T00:00:00Z");
        Instant submitted = Instant.parse("2026-01-03T12:00:00Z"); // 2 days + 1 = 3 days late
        DeadlineModifier mod = new DeadlineModifier(true, null, hardDeadline, null, null, new BigDecimal("5"), null);

        BigDecimal delta = mod.computeDelta(submitted);
        assertThat(delta).isEqualByComparingTo("-15"); // 3 * 5
    }

    @Test
    void computeDelta_shouldCapLatePenalty() {
        Instant hardDeadline = Instant.parse("2026-01-01T00:00:00Z");
        Instant submitted = Instant.parse("2026-01-10T00:00:00Z"); // 9 days late
        DeadlineModifier mod = new DeadlineModifier(true, null, hardDeadline, null, null, new BigDecimal("5"), 3);

        BigDecimal delta = mod.computeDelta(submitted);
        assertThat(delta).isEqualByComparingTo("-15"); // capped at 3 * 5
    }

    @Test
    void computeDelta_shouldApplyEarlyBonusPerDay() {
        Instant softDeadline = Instant.parse("2026-01-05T00:00:00Z");
        Instant submitted = Instant.parse("2026-01-02T00:00:00Z"); // 3 days early
        DeadlineModifier mod = new DeadlineModifier(true, softDeadline, null, null, new BigDecimal("2"), null, null);

        BigDecimal delta = mod.computeDelta(submitted);
        assertThat(delta).isEqualByComparingTo("6"); // 3 * 2
    }

    @Test
    void computeDelta_shouldApplySoftDeadlineBonus_whenNoPerDayBonus() {
        Instant softDeadline = Instant.parse("2026-01-05T00:00:00Z");
        Instant submitted = Instant.parse("2026-01-02T00:00:00Z");
        DeadlineModifier mod = new DeadlineModifier(true, softDeadline, null, new BigDecimal("10"), null, null, null);

        BigDecimal delta = mod.computeDelta(submitted);
        assertThat(delta).isEqualByComparingTo("10");
    }

    @Test
    void computeDelta_shouldPreferPerDayBonusOverFixedBonus() {
        Instant softDeadline = Instant.parse("2026-01-05T00:00:00Z");
        Instant submitted = Instant.parse("2026-01-02T00:00:00Z"); // 3 days early
        DeadlineModifier mod = new DeadlineModifier(true, softDeadline, null, new BigDecimal("10"), new BigDecimal("2"), null, null);

        BigDecimal delta = mod.computeDelta(submitted);
        assertThat(delta).isEqualByComparingTo("6"); // per-day takes precedence
    }

    @Test
    void computeDelta_shouldReturnZero_whenOnTimeButNoBonus() {
        Instant softDeadline = Instant.parse("2026-01-05T00:00:00Z");
        Instant submitted = Instant.parse("2026-01-05T00:00:00Z"); // exactly on time
        DeadlineModifier mod = new DeadlineModifier(true, softDeadline, null, null, null, null, null);

        BigDecimal delta = mod.computeDelta(submitted);
        assertThat(delta).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
