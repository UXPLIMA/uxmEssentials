package com.uxplima.uxmessentials.shared.adapter.outbound.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Pins {@link BlockedCommands}: the first-word, slash-stripped, case-insensitive matching and the normalisation of
 * configured entries (blank ones dropped). An empty list blocks nothing, the default.
 */
class BlockedCommandsTest {

    @Test
    void anEmptyListBlocksNothing() {
        BlockedCommands blocked = BlockedCommands.of(List.of());

        assertThat(blocked.isEmpty()).isTrue();
        assertThat(blocked.isBlocked("op Notch")).isFalse();
    }

    @Test
    void matchesTheFirstWordCaseInsensitivelyAndIgnoresALeadingSlash() {
        BlockedCommands blocked = BlockedCommands.of(List.of("op", "Gamemode"));

        assertThat(blocked.isBlocked("op Notch")).isTrue();
        assertThat(blocked.isBlocked("/OP Notch")).isTrue();
        assertThat(blocked.isBlocked("gamemode creative")).isTrue();
        assertThat(blocked.isBlocked("opme please")).isFalse();
        assertThat(blocked.isBlocked("say hi")).isFalse();
    }

    @Test
    void normalisesConfiguredEntriesAndDropsBlanks() {
        BlockedCommands blocked = BlockedCommands.of(List.of("  /Stop  ", "", "   "));

        assertThat(blocked.isEmpty()).isFalse();
        assertThat(blocked.isBlocked("stop")).isTrue();
    }
}
