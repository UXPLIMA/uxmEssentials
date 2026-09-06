package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.event.JailLocationDefined;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes.FakeJailLocationStore;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes.RecordingEvents;
import com.uxplima.uxmessentials.moderation.fakes.RecordingModerationAudit;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * {@code /setjail}: saves the staff member's position as a named jail. The store is keyed by name, so a save
 * is an upsert that always succeeds, both a new name and a redefinition publish {@link JailLocationDefined},
 * audit a {@code jail_location_set} line and confirm with {@code SETJAIL_SAVED}, and the name is normalised to
 * lowercase before it is stored.
 */
class SetJailTest {

    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "op");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = new Position(WORLD, 10, 64, 20, 0f, 0f);
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC);

    @Test
    void savesTheJailUnderItsLowercasedNameAndConfirms() {
        FakeJailLocationStore store = new FakeJailLocationStore();
        RecordingEvents events = new RecordingEvents();
        RecordingModerationAudit audit = new RecordingModerationAudit();
        SetJail setJail = new SetJail(store, ModerationFakes.notifier(), audit, events, CLOCK);

        setJail.set(ACTOR, "SpawnJail", AT);

        assertThat(store.find("spawnjail")).isPresent();
        assertThat(store.find("spawnjail").orElseThrow().location()).isEqualTo(AT);
        assertThat(events.events).singleElement().isInstanceOf(JailLocationDefined.class);
        assertThat(((JailLocationDefined) events.events.get(0)).jail()).isEqualTo("spawnjail");
        assertThat(audit.lines).singleElement().satisfies(line -> {
            assertThat(line.event()).isEqualTo("jail_location_set");
            assertThat(line.ok()).isTrue();
        });
    }

    @Test
    void aRedefinitionOverwritesTheLocationInPlace() {
        FakeJailLocationStore store = new FakeJailLocationStore();
        SetJail setJail = new SetJail(
                store, ModerationFakes.notifier(), new RecordingModerationAudit(), new RecordingEvents(), CLOCK);

        setJail.set(ACTOR, "mines", AT);
        Position moved = new Position(WORLD, 100, 70, 100, 0f, 0f);
        setJail.set(ACTOR, "mines", moved);

        assertThat(store.names()).containsExactly("mines");
        assertThat(store.find("mines").orElseThrow().location()).isEqualTo(moved);
    }
}
