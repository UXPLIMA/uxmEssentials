package com.uxplima.uxmessentials.messaging.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.messaging.adapter.MutableAfkStatus;
import com.uxplima.uxmessentials.messaging.application.port.AfkStatus;
import com.uxplima.uxmessentials.presence.adapter.outbound.InMemoryPresenceStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The {@link PresenceAfkStatus} soft-couple over the presence in-memory store, plus the {@link MutableAfkStatus}
 * degradation. An AFK player with a reason surfaces it; an AFK player with no reason (auto-AFK) surfaces a
 * present-but-blank value so "AFK, no reason" is distinguishable from "not AFK"; a non-AFK player is empty.
 * When presence is disabled the messaging side never binds the real status, so the holder stays on
 * {@link AfkStatus#NEVER} and answers empty for everyone, the same degrade-with-the-module-off contract the
 * mute soft-couple has.
 */
class PresenceAfkStatusTest {

    private static final Instant T0 = Instant.parse("2026-06-14T12:00:00Z");
    private final InMemoryPresenceStore store =
            new InMemoryPresenceStore(Clock.fixed(T0, ZoneOffset.UTC), uuid -> false);
    private final PresenceAfkStatus status = new PresenceAfkStatus(store);

    private final PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");

    @Test
    void returnsTheReasonWhenAfkWithAReason() {
        store.update(alice, presence -> presence.markAfk(Optional.of("lunch")));

        assertThat(status.afkReasonOf(alice)).contains("lunch");
    }

    @Test
    void returnsAPresentBlankWhenAfkWithoutAReason() {
        store.update(alice, presence -> presence.markAfk(Optional.empty()));

        assertThat(status.afkReasonOf(alice)).contains("");
    }

    @Test
    void isEmptyWhenNotAfk() {
        // current() seeds the neutral active (not-AFK) state for a never-seen player.
        assertThat(status.afkReasonOf(alice)).isEmpty();
    }

    @Test
    void mutableHolderDegradesToEmptyWhenPresenceIsDisabled() {
        // Presence off → nothing ever binds the holder, so it stays on AfkStatus.NEVER.
        MutableAfkStatus unbound = new MutableAfkStatus();
        store.update(alice, presence -> presence.markAfk(Optional.of("lunch")));

        assertThat(unbound.afkReasonOf(alice)).isEmpty();
    }

    @Test
    void mutableHolderHonoursTheBoundStatusOncePresenceWires() {
        MutableAfkStatus holder = new MutableAfkStatus();
        store.update(alice, presence -> presence.markAfk(Optional.of("brb")));
        holder.bind(status);

        assertThat(holder.afkReasonOf(alice)).contains("brb");
    }
}
