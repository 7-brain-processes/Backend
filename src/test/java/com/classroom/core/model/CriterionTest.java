package com.classroom.core.model;

import com.classroom.core.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CriterionTest {

    @Test
    void computePoints_forYesNoPresent_shouldReturnMaxPointsTimesWeight() {
        Criterion criterion = Criterion.builder()
                .type(CriterionType.YES_NO)
                .maxPoints(new BigDecimal("10"))
                .weight(new BigDecimal("1.5"))
                .build();

        BigDecimal result = criterion.computePoints(BigDecimal.ONE);

        assertThat(result).isEqualByComparingTo("15");
    }

    @Test
    void computePoints_forYesNoAbsent_shouldReturnZero() {
        Criterion criterion = Criterion.builder()
                .type(CriterionType.YES_NO)
                .maxPoints(new BigDecimal("10"))
                .build();

        BigDecimal result = criterion.computePoints(BigDecimal.ZERO);

        assertThat(result).isEqualByComparingTo("0");
    }

    @Test
    void computePoints_forPercentage_shouldUsePercentageOfMaxPoints() {
        Criterion criterion = Criterion.builder()
                .type(CriterionType.PERCENTAGE)
                .maxPoints(new BigDecimal("20"))
                .weight(BigDecimal.ONE)
                .build();

        BigDecimal result = criterion.computePoints(new BigDecimal("75"));

        assertThat(result).isEqualByComparingTo("15");
    }

    @Test
    void computePoints_forPoints_shouldReturnValueTimesWeight() {
        Criterion criterion = Criterion.builder()
                .type(CriterionType.POINTS)
                .maxPoints(new BigDecimal("20"))
                .weight(new BigDecimal("2"))
                .build();

        BigDecimal result = criterion.computePoints(new BigDecimal("5"));

        assertThat(result).isEqualByComparingTo("10");
    }

    @Test
    void validateValue_shouldAcceptZeroAndOne_forYesNo() {
        Criterion criterion = Criterion.builder()
                .type(CriterionType.YES_NO)
                .maxPoints(new BigDecimal("10"))
                .build();

        criterion.validateValue(BigDecimal.ZERO);
        criterion.validateValue(BigDecimal.ONE);
    }

    @Test
    void validateValue_shouldRejectInvalidValue_forYesNo() {
        Criterion criterion = Criterion.builder()
                .type(CriterionType.YES_NO)
                .maxPoints(new BigDecimal("10"))
                .build();

        assertThatThrownBy(() -> criterion.validateValue(new BigDecimal("2")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("YES_NO criterion value must be 0 or 1");
    }

    @Test
    void validateValue_shouldAcceptZeroTo100_forPercentage() {
        Criterion criterion = Criterion.builder()
                .type(CriterionType.PERCENTAGE)
                .maxPoints(new BigDecimal("20"))
                .build();

        criterion.validateValue(BigDecimal.ZERO);
        criterion.validateValue(new BigDecimal("50"));
        criterion.validateValue(new BigDecimal("100"));
    }

    @Test
    void validateValue_shouldRejectOutOfRange_forPercentage() {
        Criterion criterion = Criterion.builder()
                .type(CriterionType.PERCENTAGE)
                .maxPoints(new BigDecimal("20"))
                .build();

        assertThatThrownBy(() -> criterion.validateValue(new BigDecimal("101")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PERCENTAGE criterion value must be between 0 and 100");
    }

    @Test
    void validateValue_shouldAcceptWithinBounds_forPositivePoints() {
        Criterion criterion = Criterion.builder()
                .type(CriterionType.POINTS)
                .maxPoints(new BigDecimal("20"))
                .build();

        criterion.validateValue(BigDecimal.ZERO);
        criterion.validateValue(new BigDecimal("15"));
        criterion.validateValue(new BigDecimal("20"));
    }

    @Test
    void validateValue_shouldRejectNegative_forPositivePoints() {
        Criterion criterion = Criterion.builder()
                .type(CriterionType.POINTS)
                .maxPoints(new BigDecimal("20"))
                .build();

        assertThatThrownBy(() -> criterion.validateValue(new BigDecimal("-1")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("POINTS criterion value must be between 0 and 20");
    }

    @Test
    void validateValue_shouldAcceptWithinBounds_forNegativePoints() {
        Criterion criterion = Criterion.builder()
                .type(CriterionType.POINTS)
                .maxPoints(new BigDecimal("-5"))
                .build();

        criterion.validateValue(new BigDecimal("-5"));
        criterion.validateValue(new BigDecimal("-2"));
        criterion.validateValue(BigDecimal.ZERO);
    }

    @Test
    void validateValue_shouldRejectPositive_forNegativePoints() {
        Criterion criterion = Criterion.builder()
                .type(CriterionType.POINTS)
                .maxPoints(new BigDecimal("-5"))
                .build();

        assertThatThrownBy(() -> criterion.validateValue(new BigDecimal("1")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("POINTS criterion value must be between -5 and 0");
    }

    @Test
    void computePoints_forNegativeMaxPointsYesNo_shouldReturnNegative() {
        Criterion criterion = Criterion.builder()
                .type(CriterionType.YES_NO)
                .maxPoints(new BigDecimal("-5"))
                .build();

        BigDecimal result = criterion.computePoints(BigDecimal.ONE);

        assertThat(result).isEqualByComparingTo("-5");
    }
}
