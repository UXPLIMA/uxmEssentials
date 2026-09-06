package com.uxplima.uxmessentials.presence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.presence.adapter.inbound.listener.PresenceLifecycleListener;
import com.uxplima.uxmessentials.presence.adapter.outbound.AfkSweep;
import com.uxplima.uxmessentials.presence.adapter.outbound.InMemoryPresenceStore;
import com.uxplima.uxmessentials.presence.application.ClearAfkOnActivity;
import com.uxplima.uxmessentials.presence.application.MarkAfk;
import com.uxplima.uxmessentials.presence.application.port.PresenceAudience;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the presence outbound adapters and listeners against a real (mock) Bukkit server: the
 * in-memory presence store's {@code compute}-based mutation, the {@link AfkSweep} flipping an idle player to AFK on
 * its scan, and the {@link PresenceLifecycleListener} seeding and forgetting a player's presence. Vanish moved to its
 * own {@code vanish} context, covered by {@code VanishAdapterTest}. The {@link Scheduler} is a synchronous inline fake
 * so the entity-thread hop the adapters route through is exercised deterministically.
 */
class PresenceAdapterTest {

    private ServerMock server;
    private InlineScheduler scheduler;
    private InMemoryPresenceStore store;
    private RecordingEvents events;
    private Notifier notifier;
    private FixedClock clock;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        scheduler = new InlineScheduler();
        clock = new FixedClock(Instant.parse("2026-05-30T12:00:00Z"));
        store = new InMemoryPresenceStore(clock, uuid -> false);
        events = new RecordingEvents();
        notifier = new Notifier(new KeyMessages(), new DiscardingSink());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theStoreSeedsActiveAndForgetsAQuitPlayer() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerRef ref = BukkitRefs.toRef(alice);

        assertThat(store.current(ref).afk()).isFalse();
        store.update(ref, p -> p.markAfk(Optional.of("brb")));
        assertThat(store.current(ref).afk()).isTrue();

        store.forget(ref);
        assertThat(store.current(ref).afk()).isFalse(); // back to the neutral active state
    }

    @Test
    void theAfkSweepFlipsAnIdlePlayerToAfk() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerRef ref = BukkitRefs.toRef(alice);
        store.current(ref); // seed an active presence stamped at the fixed clock
        MarkAfk markAfk = new MarkAfk(store, new OnlineAudience(), notifier, events, clock);
        AfkSweep sweep = new AfkSweep(
                scheduler,
                store,
                markAfk,
                new NoopLogger(),
                Duration.ofSeconds(15),
                Duration.ofMinutes(5),
                oneShot(),
                clock);

        clock.advance(Duration.ofMinutes(6)); // push past the idle threshold
        sweep.start(); // the inline scheduler runs the single delayed sweep immediately

        assertThat(store.current(ref).afk()).isTrue();
        assertThat(events.published)
                .anyMatch(e -> e instanceof com.uxplima.uxmessentials.presence.domain.event.WentAfk w && w.automatic());
    }

    @Test
    void theAfkSweepLeavesAnActivePlayerAlone() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerRef ref = BukkitRefs.toRef(alice);
        store.current(ref);
        MarkAfk markAfk = new MarkAfk(store, new OnlineAudience(), notifier, events, clock);
        AfkSweep sweep = new AfkSweep(
                scheduler,
                store,
                markAfk,
                new NoopLogger(),
                Duration.ofSeconds(15),
                Duration.ofMinutes(5),
                oneShot(),
                clock);

        clock.advance(Duration.ofMinutes(2)); // still within the threshold
        sweep.start();

        assertThat(store.current(ref).afk()).isFalse();
    }

    @Test
    void activityReturnsAnAfkPlayer() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerRef ref = BukkitRefs.toRef(alice);
        MarkAfk markAfk = new MarkAfk(store, new OnlineAudience(), notifier, events, clock);
        ClearAfkOnActivity clearAfk = new ClearAfkOnActivity(store, new OnlineAudience(), notifier, events, clock);
        markAfk.markAuto(ref);

        clearAfk.recordActivity(ref);

        assertThat(store.current(ref).afk()).isFalse();
    }

    @Test
    void theLifecycleListenerForgetsAQuitPlayersPresence() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerRef ref = BukkitRefs.toRef(alice);
        store.update(ref, p -> p.markAfk(Optional.of("brb")));
        assertThat(store.current(ref).afk()).isTrue();
        PresenceLifecycleListener listener = new PresenceLifecycleListener(store);
        PlayerQuitEvent quit = new PlayerQuitEvent(
                alice, net.kyori.adventure.text.Component.text("Alice left"), PlayerQuitEvent.QuitReason.DISCONNECTED);

        listener.onQuit(quit);

        assertThat(store.current(ref).afk()).isFalse(); // presence dropped, re-reads as the neutral active state
    }

    /**
     * A {@code running} supplier that stays true long enough for exactly one sweep cycle, then false, so the
     * inline scheduler's synchronous self-reschedule terminates instead of recursing. The real
     * {@code asyncAfter} genuinely defers, so production never recurses.
     */
    private static java.util.function.BooleanSupplier oneShot() {
        java.util.concurrent.atomic.AtomicInteger ticks = new java.util.concurrent.atomic.AtomicInteger(2);
        return () -> ticks.getAndDecrement() > 0;
    }

    /** A scheduler that runs every task inline so the entity-thread hop and the delayed sweep fire at once. */
    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    /** A clock the test advances by hand so the AFK idle threshold is crossed deterministically. */
    private static final class FixedClock extends Clock {
        private Instant now;

        FixedClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** A presence audience reading the live mock server's online set. */
    private static final class OnlineAudience implements PresenceAudience {
        @Override
        public List<PlayerRef> online() {
            List<PlayerRef> all = new ArrayList<>();
            org.bukkit.Bukkit.getOnlinePlayers().forEach(p -> all.add(BukkitRefs.toRef(p)));
            return List.copyOf(all);
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

    private static final class DiscardingSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // discarded: delivery is not under test here
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
