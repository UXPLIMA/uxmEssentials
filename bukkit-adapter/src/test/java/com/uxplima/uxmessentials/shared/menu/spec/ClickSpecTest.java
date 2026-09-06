package com.uxplima.uxmessentials.shared.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import org.junit.jupiter.api.Test;

/**
 * Pure checks on {@link ClickSpec#hasAnyAction()}. The flag the Bedrock form renderer reads to tell a tappable
 * button from a decorative filler: plus a guard that adding it leaves {@link ClickSpec#actionsFor} untouched.
 */
class ClickSpecTest {

    @Test
    void aGestureWithActionsReportsHavingAnAction() {
        ClickSpec click = new ClickSpec(Map.of(ClickKind.LEFT, List.of(Ref.parse("record:x"))), Map.of());

        assertThat(click.hasAnyAction())
                .as("a click that binds a left-gesture action carries an action")
                .isTrue();
    }

    @Test
    void aClickBoundOnlyToAnyStillReportsHavingAnAction() {
        ClickSpec click = new ClickSpec(Map.of(ClickKind.ANY, List.of(Ref.parse("close"))), Map.of());

        assertThat(click.hasAnyAction())
                .as("a shared ANY action is still an action")
                .isTrue();
    }

    @Test
    void anEmptyClickReportsNoAction() {
        ClickSpec filler = new ClickSpec(Map.of(), Map.of());

        assertThat(filler.hasAnyAction())
                .as("a filler with no bound gesture carries no action")
                .isFalse();
    }

    @Test
    void aClickWithOnlyEmptyActionListsReportsNoAction() {
        ClickSpec filler = new ClickSpec(Map.of(ClickKind.LEFT, List.of(), ClickKind.RIGHT, List.of()), Map.of());

        assertThat(filler.hasAnyAction())
                .as("gestures present but every action list empty is still no action")
                .isFalse();
    }

    @Test
    void actionsForStillMergesTheSharedAnyList() {
        ClickSpec click = new ClickSpec(
                Map.of(
                        ClickKind.LEFT, List.of(Ref.parse("close")),
                        ClickKind.ANY, List.of(Ref.parse("refresh"))),
                Map.of());

        assertThat(click.actionsFor(ClickKind.LEFT).stream().map(Ref::id))
                .as("hasAnyAction must not disturb the own-then-ANY merge actionsFor performs")
                .containsExactly("close", "refresh");
    }
}
