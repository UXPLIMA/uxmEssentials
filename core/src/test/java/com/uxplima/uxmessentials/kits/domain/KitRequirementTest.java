package com.uxplima.uxmessentials.kits.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Parsing rules of the opaque {@link KitRequirement} value object and its {@link RequirementOperator}: each
 * config entry splits on the first operator token (two-character tokens before single ones, so {@code >=}
 * never mis-splits as {@code >}), tolerates spacing, keeps both operands as raw strings, and rejects a blank
 * side or an unknown operator by returning empty. The kernel never resolves the placeholders these operands
 * carry, that is the evaluator adapter's job, so these tests cover only the shape, not any comparison.
 */
class KitRequirementTest {

    @Test
    void parsesAGreaterOrEqualConditionWithPlaceholders() {
        KitRequirement req = KitRequirement.parse("%player_level% >= 10").orElseThrow();

        assertThat(req.left()).isEqualTo("%player_level%");
        assertThat(req.operator()).isEqualTo(RequirementOperator.GTE);
        assertThat(req.right()).isEqualTo("10");
        assertThat(req.asText()).isEqualTo("%player_level% >= 10");
    }

    @Test
    void doesNotMisSplitTwoCharacterOperators() {
        assertThat(KitRequirement.parse("%a% >= 5").orElseThrow().operator()).isEqualTo(RequirementOperator.GTE);
        assertThat(KitRequirement.parse("%a% <= 5").orElseThrow().operator()).isEqualTo(RequirementOperator.LTE);
        assertThat(KitRequirement.parse("%a% == 5").orElseThrow().operator()).isEqualTo(RequirementOperator.EQ);
        assertThat(KitRequirement.parse("%a% != 5").orElseThrow().operator()).isEqualTo(RequirementOperator.NEQ);
        assertThat(KitRequirement.parse("%a% > 5").orElseThrow().operator()).isEqualTo(RequirementOperator.GT);
        assertThat(KitRequirement.parse("%a% < 5").orElseThrow().operator()).isEqualTo(RequirementOperator.LT);
    }

    @Test
    void toleratesMissingSpacesAroundTheOperator() {
        KitRequirement req = KitRequirement.parse("%rank%==vip").orElseThrow();

        assertThat(req.left()).isEqualTo("%rank%");
        assertThat(req.operator()).isEqualTo(RequirementOperator.EQ);
        assertThat(req.right()).isEqualTo("vip");
    }

    @Test
    void rejectsAnEntryWithNoOperator() {
        assertThat(KitRequirement.parse("%player_level% 10")).isEmpty();
    }

    @Test
    void rejectsAnEntryWithABlankSide() {
        assertThat(KitRequirement.parse(">= 10")).isEmpty();
        assertThat(KitRequirement.parse("%player_level% >=")).isEmpty();
    }

    @Test
    void constructorRejectsBlankOperands() {
        assertThatThrownBy(() -> new KitRequirement("  ", RequirementOperator.EQ, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KitRequirement("x", RequirementOperator.EQ, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void operatorParseMapsTokensAndRejectsUnknown() {
        assertThat(RequirementOperator.parse(">=")).contains(RequirementOperator.GTE);
        assertThat(RequirementOperator.parse("  != ")).contains(RequirementOperator.NEQ);
        assertThat(RequirementOperator.parse("=")).isEmpty();
        assertThat(RequirementOperator.GTE.isOrdered()).isTrue();
        assertThat(RequirementOperator.EQ.isOrdered()).isFalse();
    }
}
