package com.uxplima.uxmessentials.presence.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import com.uxplima.uxmessentials.presence.application.port.PresenceAudience;
import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
import com.uxplima.uxmessentials.presence.domain.PlayerPresence;
import com.uxplima.uxmessentials.presence.domain.event.ReturnedFromAfk;
import com.uxplima.uxmessentials.presence.domain.event.WentAfk;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The presence use cases through their real implementations against in-memory fakes. The same wiring the
 * Brigadier handlers, the activity listeners, and the AFK sweep drive, minus Bukkit. It pins the headline rules:
 * {@code /afk} toggles AFK and publishes the matching event; the sweep's auto-mark flips an idle player exactly
 * once; and activity clears AFK, only announcing a return when the player was actually AFK. Vanish moved to its own
 * {@code vanish} context and is exercised there.
 */
class PresenceUseCasesTest {

    private FakeStore store;
    private FakeAudience audience;
    private RecordingEvents events;
    private Notifier notifier;
    private Clock clock;
    private PlayerRef alice;
    private PlayerRef bob;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        audience = new FakeAudience();
        events = new RecordingEvents();
        notifier = new Notifier(new KeyMessages(), new CapturingSink());
        clock = Clock.system(ZoneOffset.UTC);
        alice = new PlayerRef(UUID.randomUUID(), "Alice");
        bob = new PlayerRef(UUID.randomUUID(), "Bob");
        audience.players.add(alice);
        audience.players.add(bob);
    }

    @Test
    void afkToggleEntersAfkWithReasonAndPublishesWentAfk() {
        MarkAfk markAfk = new MarkAfk(store, audience, notifier, events, clock);

        boolean afk = markAfk.toggle(alice, Optional.of("lunch"));

        assertThat(afk).isTrue();
        assertThat(store.current(alice).afk()).isTrue();
        assertThat(store.current(alice).afkReason()).contains("lunch");
        WentAfk event = (WentAfk) onlyEvent();
        assertThat(event.subject()).isEqualTo(alice);
        assertThat(event.automatic()).isFalse();
        assertThat(event.reason()).contains("lunch");
    }

    @Test
    void afkToggleTwiceReturnsAndPublishesReturnedFromAfk() {
        MarkAfk markAfk = new MarkAfk(store, audience, notifier, events, clock);

        markAfk.toggle(alice, Optional.empty());
        boolean afk = markAfk.toggle(alice, Optional.empty());

        assertThat(afk).isFalse();
        assertThat(store.current(alice).afk()).isFalse();
        assertThat(events.published).last().isInstanceOf(ReturnedFromAfk.class);
    }

    @Test
    void autoMarkFlipsAnActivePlayerOnce() {
        MarkAfk markAfk = new MarkAfk(store, audience, notifier, events, clock);

        boolean firstFlip = markAfk.markAuto(alice);
        boolean secondFlip = markAfk.markAuto(alice);

        assertThat(firstFlip).isTrue();
        assertThat(secondFlip).isFalse(); // already AFK, no re-flip
        assertThat(store.current(alice).afk()).isTrue();
        WentAfk event = (WentAfk) onlyEvent();
        assertThat(event.automatic()).isTrue();
        assertThat(event.reason()).isEmpty();
    }

    @Test
    void activityOnAnAfkPlayerReturnsThemAndAnnounces() {
        MarkAfk markAfk = new MarkAfk(store, audience, notifier, events, clock);
        ClearAfkOnActivity clearAfk = new ClearAfkOnActivity(store, audience, notifier, events, clock);
        markAfk.markAuto(alice);
        events.published.clear();

        clearAfk.recordActivity(alice);

        assertThat(store.current(alice).afk()).isFalse();
        assertThat(events.published).singleElement().isInstanceOf(ReturnedFromAfk.class);
    }

    @Test
    void activityOnAnActivePlayerJustRestampsWithNoEvent() {
        ClearAfkOnActivity clearAfk = new ClearAfkOnActivity(store, audience, notifier, events, clock);

        clearAfk.recordActivity(bob);

        assertThat(store.current(bob).afk()).isFalse();
        assertThat(events.published).isEmpty(); // no return event when the player was never AFK
    }

    private DomainEvent onlyEvent() {
        assertThat(events.published).hasSize(1);
        return events.published.get(0);
    }

    /** A map-backed {@link PresenceStore} mutated via the same compute contract as the real adapter. */
    private static final class FakeStore implements PresenceStore {
        private final ConcurrentHashMap<UUID, Entry> map = new ConcurrentHashMap<>();
        private final Clock clock = Clock.system(ZoneOffset.UTC);

        @Override
        public PlayerPresence current(PlayerRef who) {
            return map.computeIfAbsent(who.uuid(), id -> new Entry(who, PlayerPresence.active(clock.instant())))
                    .presence();
        }

        @Override
        public PlayerPresence update(PlayerRef who, UnaryOperator<PlayerPresence> mutator) {
            return map.compute(who.uuid(), (id, existing) -> {
                        PlayerPresence base =
                                existing == null ? PlayerPresence.active(clock.instant()) : existing.presence();
                        return new Entry(who, mutator.apply(base));
                    })
                    .presence();
        }

        @Override
        public void forget(PlayerRef who) {
            map.remove(who.uuid());
        }

        @Override
        public Map<PlayerRef, PlayerPresence> snapshotAll() {
            Map<PlayerRef, PlayerPresence> copy = new java.util.HashMap<>();
            map.values().forEach(entry -> copy.put(entry.who(), entry.presence()));
            return Map.copyOf(copy);
        }

        private record Entry(PlayerRef who, PlayerPresence presence) {}
    }

    /** An audience returning a fixed online set. */
    private static final class FakeAudience implements PresenceAudience {
        private final List<PlayerRef> players = new ArrayList<>();

        @Override
        public List<PlayerRef> online() {
            return List.copyOf(players);
        }
    }

    private static final class RecordingEvents implements DomainEventPublisher {
        private final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class CapturingSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // discarded: feedback delivery is not under test here
        }
    }
}
