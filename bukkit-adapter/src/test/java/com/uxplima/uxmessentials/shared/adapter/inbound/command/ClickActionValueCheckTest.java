package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.shared.domain.action.ClickActionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Pins the add-time value validation shared by every click-action command surface: a numeric {@code DELAY}/
 * {@code CHANCE}/{@code COST} and a resolvable {@code GIVE} material are required, while the free-text gates and
 * operator-content effects accept anything. {@code parseType} maps every type word, including the richer ones,
 * case-insensitively.
 */
class ClickActionValueCheckTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock(); // Material.matchMaterial needs a server
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void parseTypeMapsTheNewTypesCaseInsensitively() {
        assertThat(ClickActionValueCheck.parseType("DELAY")).contains(ClickActionType.DELAY);
        assertThat(ClickActionValueCheck.parseType("Random")).contains(ClickActionType.RANDOM);
        assertThat(ClickActionValueCheck.parseType("chance")).contains(ClickActionType.CHANCE);
        assertThat(ClickActionValueCheck.parseType("Permission")).contains(ClickActionType.PERMISSION);
        assertThat(ClickActionValueCheck.parseType("condition")).contains(ClickActionType.CONDITION);
        assertThat(ClickActionValueCheck.parseType("cost")).contains(ClickActionType.COST);
        assertThat(ClickActionValueCheck.parseType("give")).contains(ClickActionType.GIVE);
        assertThat(ClickActionValueCheck.parseType("player_op")).contains(ClickActionType.RUN_PLAYER_AS_OP);
        assertThat(ClickActionValueCheck.parseType("nope")).isEmpty();
    }

    @Test
    void randomMustBeAPositiveCount() {
        assertThat(ClickActionValueCheck.check(ClickActionType.RANDOM, "3").isValid())
                .isTrue();
        assertThat(ClickActionValueCheck.check(ClickActionType.RANDOM, "0").isValid())
                .isFalse();
        assertThat(ClickActionValueCheck.check(ClickActionType.RANDOM, "-2").isValid())
                .isFalse();
        assertThat(ClickActionValueCheck.check(ClickActionType.RANDOM, "many").isValid())
                .isFalse();
    }

    @Test
    void giveAcceptsASerializedItemToken() {
        // A b64: token (what 'give hand' stores) is accepted as-is: its shape is the codec's concern, not the check.
        assertThat(ClickActionValueCheck.check(ClickActionType.GIVE, "b64:whatever")
                        .isValid())
                .isTrue();
    }

    @Test
    void delayMustBeAWholeNumber() {
        assertThat(ClickActionValueCheck.check(ClickActionType.DELAY, "40").isValid())
                .isTrue();
        assertThat(ClickActionValueCheck.check(ClickActionType.DELAY, "-1").isValid())
                .isFalse();
        assertThat(ClickActionValueCheck.check(ClickActionType.DELAY, "abc").isValid())
                .isFalse();
    }

    @Test
    void chanceMustBeAPercent() {
        assertThat(ClickActionValueCheck.check(ClickActionType.CHANCE, "25").isValid())
                .isTrue();
        assertThat(ClickActionValueCheck.check(ClickActionType.CHANCE, "25.0").isValid())
                .isTrue();
        assertThat(ClickActionValueCheck.check(ClickActionType.CHANCE, "150").isValid())
                .isFalse();
        assertThat(ClickActionValueCheck.check(ClickActionType.CHANCE, "nope").isValid())
                .isFalse();
    }

    @Test
    void costMustBeANonNegativeNumber() {
        assertThat(ClickActionValueCheck.check(ClickActionType.COST, "50").isValid())
                .isTrue();
        assertThat(ClickActionValueCheck.check(ClickActionType.COST, "-5").isValid())
                .isFalse();
        assertThat(ClickActionValueCheck.check(ClickActionType.COST, "free").isValid())
                .isFalse();
    }

    @Test
    void giveMustNameAKnownMaterial() {
        assertThat(ClickActionValueCheck.check(ClickActionType.GIVE, "DIAMOND").isValid())
                .isTrue();
        assertThat(ClickActionValueCheck.check(ClickActionType.GIVE, "diamond:3")
                        .isValid())
                .isTrue();
        assertThat(ClickActionValueCheck.check(ClickActionType.GIVE, "NOT_A_REAL_MATERIAL")
                        .isValid())
                .isFalse();
    }

    @Test
    void freeTextGatesAndEffectsAcceptAnything() {
        assertThat(ClickActionValueCheck.check(ClickActionType.PERMISSION, "anything at all")
                        .isValid())
                .isTrue();
        assertThat(ClickActionValueCheck.check(ClickActionType.CONDITION, "garbage")
                        .isValid())
                .isTrue();
        assertThat(ClickActionValueCheck.check(ClickActionType.MESSAGE, "<red>hi")
                        .isValid())
                .isTrue();
    }
}
