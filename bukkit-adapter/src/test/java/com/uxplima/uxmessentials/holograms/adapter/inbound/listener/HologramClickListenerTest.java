package com.uxplima.uxmessentials.holograms.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.holograms.adapter.inbound.listener.HologramClickListener.Primary;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link HologramClickListener#primaryBehaviour}, the legacy single-click decision that runs before the
 * action chain. The action chain itself always runs after (and is engine-tested in the shared module), so this
 * only covers the command-vs-page-vs-none precedence and that the click command is right-click-only.
 */
class HologramClickListenerTest {

    @Test
    void aRightClickCommandTakesPrecedence() {
        assertThat(HologramClickListener.primaryBehaviour("warp spawn", true, false))
                .isEqualTo(Primary.RUN_COMMAND);
        assertThat(HologramClickListener.primaryBehaviour("warp spawn", false, false))
                .isEqualTo(Primary.RUN_COMMAND);
    }

    @Test
    void aLeftClickNeverRunsTheLegacyCommand() {
        // The single click command is the legacy right-click binding (as it is for an NPC); a left-click falls
        // through to the page cycle when paged, otherwise to nothing: the chain's left actions run regardless.
        assertThat(HologramClickListener.primaryBehaviour("warp spawn", true, true))
                .isEqualTo(Primary.CYCLE_PAGE);
        assertThat(HologramClickListener.primaryBehaviour("warp spawn", false, true))
                .isEqualTo(Primary.NONE);
    }

    @Test
    void aPagedHologramWithNoCommandCyclesOnEitherClick() {
        assertThat(HologramClickListener.primaryBehaviour(null, true, false)).isEqualTo(Primary.CYCLE_PAGE);
        assertThat(HologramClickListener.primaryBehaviour("   ", true, true)).isEqualTo(Primary.CYCLE_PAGE);
    }

    @Test
    void aBlankCommandAndNoPagesIsNone() {
        assertThat(HologramClickListener.primaryBehaviour(null, false, false)).isEqualTo(Primary.NONE);
        assertThat(HologramClickListener.primaryBehaviour("", false, true)).isEqualTo(Primary.NONE);
    }
}
