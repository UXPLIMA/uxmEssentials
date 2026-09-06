package com.uxplima.uxmessentials.kits.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Unit coverage of {@link KitAction#parse} and {@link KitActionType#parse}: each action type parses from its
 * kebab-case token (and the {@code _} alias), the before-items and count-as-item flags are carried through, and
 * a token naming no known type is skipped (empty) rather than throwing, so a malformed kit action never fails
 * the whole kit on load.
 */
class KitActionTest {

    @Test
    void parsesEveryTypeFromItsToken() {
        assertThat(parse("message"))
                .hasValueSatisfying(a -> assertThat(a.type()).isEqualTo(KitActionType.MESSAGE));
        assertThat(parse("broadcast"))
                .hasValueSatisfying(a -> assertThat(a.type()).isEqualTo(KitActionType.BROADCAST));
        assertThat(parse("actionbar"))
                .hasValueSatisfying(a -> assertThat(a.type()).isEqualTo(KitActionType.ACTIONBAR));
        assertThat(parse("title")).hasValueSatisfying(a -> assertThat(a.type()).isEqualTo(KitActionType.TITLE));
        assertThat(parse("sound")).hasValueSatisfying(a -> assertThat(a.type()).isEqualTo(KitActionType.SOUND));
        assertThat(parse("particle"))
                .hasValueSatisfying(a -> assertThat(a.type()).isEqualTo(KitActionType.PARTICLE));
        assertThat(parse("firework"))
                .hasValueSatisfying(a -> assertThat(a.type()).isEqualTo(KitActionType.FIREWORK));
        assertThat(parse("console-command"))
                .hasValueSatisfying(a -> assertThat(a.type()).isEqualTo(KitActionType.CONSOLE_COMMAND));
        assertThat(parse("player-command"))
                .hasValueSatisfying(a -> assertThat(a.type()).isEqualTo(KitActionType.PLAYER_COMMAND));
        assertThat(parse("clear-inventory"))
                .hasValueSatisfying(a -> assertThat(a.type()).isEqualTo(KitActionType.CLEAR_INVENTORY));
        assertThat(parse("wait-ticks"))
                .hasValueSatisfying(a -> assertThat(a.type()).isEqualTo(KitActionType.WAIT_TICKS));
    }

    @Test
    void clearInventoryParsesWithAnEmptyValue() {
        Optional<KitAction> action = KitAction.parse("clear-inventory", "", true, false);

        assertThat(action).isPresent();
        assertThat(action.get().type()).isEqualTo(KitActionType.CLEAR_INVENTORY);
        assertThat(action.get().value()).isEmpty();
        assertThat(action.get().beforeItems()).isTrue();
    }

    @Test
    void underscoreAndCaseAreToleratedInTheTypeToken() {
        assertThat(parse("CONSOLE_COMMAND"))
                .hasValueSatisfying(a -> assertThat(a.type()).isEqualTo(KitActionType.CONSOLE_COMMAND));
        assertThat(parse("  Wait_Ticks "))
                .hasValueSatisfying(a -> assertThat(a.type()).isEqualTo(KitActionType.WAIT_TICKS));
    }

    @Test
    void anUnknownTypeIsSkippedNotFatal() {
        assertThat(KitAction.parse("teleport", "spawn", false, false)).isEmpty();
        assertThat(KitAction.parse("", "x", false, false)).isEmpty();
    }

    @Test
    void carriesTheValueAndBothFlags() {
        Optional<KitAction> action = KitAction.parse("console-command", "crate give {player} vote 1", true, true);

        assertThat(action).isPresent();
        assertThat(action.get().value()).isEqualTo("crate give {player} vote 1");
        assertThat(action.get().beforeItems()).isTrue();
        assertThat(action.get().countAsItem()).isTrue();
    }

    @Test
    void flagsDefaultToFalseThroughTheOfFactory() {
        KitAction action = KitAction.of(KitActionType.SOUND, "ENTITY_PLAYER_LEVELUP;1;1");

        assertThat(action.beforeItems()).isFalse();
        assertThat(action.countAsItem()).isFalse();
    }

    private static Optional<KitAction> parse(String type) {
        return KitAction.parse(type, "value", false, false);
    }
}
