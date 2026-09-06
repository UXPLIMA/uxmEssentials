package com.uxplima.uxmessentials.communication.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.entity.Player;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.communication.application.NextAnnouncement;
import com.uxplima.uxmessentials.communication.application.port.BroadcastOptOutStore;
import com.uxplima.uxmessentials.communication.application.port.RandomSource;
import com.uxplima.uxmessentials.communication.domain.Announcement;
import com.uxplima.uxmessentials.communication.domain.AnnouncerConfig;
import com.uxplima.uxmessentials.communication.domain.Ordering;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ChannelBroadcaster;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ChannelDisplay;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.display.ConditionContext;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@link AnnouncerTask} default-rotation loop end-to-end through the real
 * {@link NextAnnouncement} cursor and {@link BukkitAnnouncerBroadcaster}: the min-players gate skips a tick below
 * the threshold, a passing tick picks an announcement and delivers it to a matching opted-in player, and the
 * opt-out / condition gates suppress a recipient. The scheduler queues each {@code asyncAfter} so the test drains
 * exactly one tick at a time rather than letting the loop reschedule forever.
 */
class AnnouncerTaskTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ServerMock server;
    private PlayerMock alice;
    private FakeOptOut optOut;
    private QueueScheduler scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");
        optOut = new FakeOptOut();
        scheduler = new QueueScheduler();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aTickBelowTheMinPlayersGateDeliversNothing() {
        // min-players = 5, one player online → the gate fails and no announcement is picked.
        AnnouncerConfig config = config(5, chat("a", "hi"));
        AnnouncerTask task = task(config);

        task.start();
        scheduler.drainOne(); // the first default-rotation tick

        assertThat(alice.nextComponentMessage()).isNull();
    }

    @Test
    void aPassingTickDeliversToAMatchingOptedInPlayer() {
        AnnouncerConfig config = config(0, chat("a", "<gold>welcome"));
        AnnouncerTask task = task(config);

        task.start();
        scheduler.drainOne();

        assertThat(PLAIN.serialize(alice.nextComponentMessage())).isEqualTo("welcome");
    }

    @Test
    void anOptedOutPlayerGetsNothingOnAPassingTick() {
        optOut.optOut(alice.getUniqueId());
        AnnouncerConfig config = config(0, chat("a", "hi"));
        AnnouncerTask task = task(config);

        task.start();
        scheduler.drainOne();

        assertThat(alice.nextComponentMessage()).isNull();
    }

    @Test
    void aConditionThatDoesNotMatchSuppressesTheRecipient() {
        Announcement gated = new Announcement(
                "vip",
                List.of("vip"),
                new DisplayCondition.Permission("uxmessentials.vip"),
                Optional.empty(),
                Set.of(BroadcastChannel.CHAT),
                Optional.empty(),
                false);
        AnnouncerTask task = task(config(0, gated));

        task.start();
        scheduler.drainOne();

        assertThat(alice.nextComponentMessage()).isNull();
    }

    @Test
    void anOverrideLoopIsCancelledOnStopAndTicksNoMore() {
        AnnouncerConfig config = config(0, override("o", "tick", 30));
        AnnouncerTask task = task(config);

        task.start();
        scheduler.drainRound(); // the default-rotation tick (nothing to pick) + the override's first tick
        assertThat(PLAIN.serialize(alice.nextComponentMessage())).isEqualTo("tick");

        task.stop();
        // The override tick rescheduled itself before stop; draining it must observe the cancellation and not fire.
        scheduler.drainRound();
        assertThat(alice.nextComponentMessage()).isNull();
    }

    @Test
    void rearmAfterAddingAnOverrideAnnouncementStartsItBroadcasting() {
        // Start with no override announcements at all: nothing is armed on its own loop.
        AtomicReference<AnnouncerConfig> live = new AtomicReference<>(config(0));
        AnnouncerTask task = task(live::get);

        task.start();
        scheduler.drainRound(); // only the default-rotation tick, which has nothing to pick
        assertThat(alice.nextComponentMessage()).isNull();

        // A reload adds a brand-new override announcement: the bug was that it never broadcast.
        live.set(config(0, override("fresh", "hello", 30)));
        task.rearmOverrides();
        scheduler.drainRound();

        assertThat(PLAIN.serialize(alice.nextComponentMessage())).isEqualTo("hello");
    }

    @Test
    void rearmAfterRemovingAnOverrideAnnouncementStopsItsLoop() {
        AtomicReference<AnnouncerConfig> live = new AtomicReference<>(config(0, override("o", "tick", 30)));
        AnnouncerTask task = task(live::get);

        task.start();
        scheduler.drainRound();
        assertThat(PLAIN.serialize(alice.nextComponentMessage())).isEqualTo("tick");

        // Drop the override on reload and re-arm: the old loop is cancelled, no new loop is armed.
        live.set(config(0));
        task.rearmOverrides();
        scheduler.drainRound();
        scheduler.drainRound();

        assertThat(alice.nextComponentMessage()).isNull();
    }

    @Test
    void rearmAfterChangingAnOverrideIntervalKeepsItBroadcasting() {
        AtomicReference<AnnouncerConfig> live = new AtomicReference<>(config(0, override("o", "tick", 30)));
        AnnouncerTask task = task(live::get);

        task.start();
        scheduler.drainRound();
        assertThat(PLAIN.serialize(alice.nextComponentMessage())).isEqualTo("tick");

        // The same announcement with a new cadence: the re-armed loop honours it and keeps firing.
        live.set(config(0, override("o", "tick", 5)));
        task.rearmOverrides();
        scheduler.drainRound();

        assertThat(PLAIN.serialize(alice.nextComponentMessage())).isEqualTo("tick");
    }

    @Test
    void theDefaultRotationDoesNotBroadcastOverrideAnnouncements() {
        // One default-rotation announcement and one override announcement together.
        AnnouncerConfig config = config(0, chat("rot", "rotation"), override("o", "override", 30));
        AnnouncerTask task = task(config);

        task.start();
        scheduler.drainOne(); // exactly the default-rotation tick, must pick "rotation", never "override"

        assertThat(PLAIN.serialize(alice.nextComponentMessage())).isEqualTo("rotation");
        assertThat(alice.nextComponentMessage()).isNull();
    }

    private AnnouncerTask task(AnnouncerConfig config) {
        return task(() -> config);
    }

    private AnnouncerTask task(java.util.function.Supplier<AnnouncerConfig> live) {
        ChannelBroadcaster channels = new ChannelBroadcaster(new SyncScheduler(), display());
        BukkitAnnouncerBroadcaster broadcaster = new BukkitAnnouncerBroadcaster(
                new ThrowingSink(), optOut, channels, this::context, new SyncScheduler());
        NextAnnouncement next = new NextAnnouncement(() -> live.get().rotating(), new ZeroRandom());
        return new AnnouncerTask(scheduler, next, broadcaster, live, () -> true);
    }

    private ConditionContext context(Player player) {
        return new ConditionContext(
                player::hasPermission,
                player.getWorld().getName(),
                player.getGameMode().name(),
                java.util.function.UnaryOperator.identity());
    }

    private static AnnouncerConfig config(int minPlayers, Announcement... announcements) {
        return new AnnouncerConfig(Duration.ofSeconds(60), minPlayers, Ordering.SEQUENTIAL, List.of(announcements));
    }

    private static Announcement chat(String id, String line) {
        return new Announcement(
                id,
                List.of(line),
                DisplayCondition.always(),
                Optional.empty(),
                Set.of(BroadcastChannel.CHAT),
                Optional.empty(),
                false);
    }

    /** A CHAT announcement that runs on its own {@code intervalSeconds} loop rather than the shared rotation. */
    private static Announcement override(String id, String line, int intervalSeconds) {
        return new Announcement(
                id,
                List.of(line),
                DisplayCondition.always(),
                Optional.of(Duration.ofSeconds(intervalSeconds)),
                Set.of(BroadcastChannel.CHAT),
                Optional.empty(),
                false);
    }

    private static ChannelDisplay display() {
        return new ChannelDisplay(100, 500, 100, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS, 1);
    }

    private static final class ZeroRandom implements RandomSource {
        @Override
        public int nextBounded(int bound) {
            return 0;
        }
    }

    private static final class FakeOptOut implements BroadcastOptOutStore {
        private final Set<java.util.UUID> out = java.util.concurrent.ConcurrentHashMap.newKeySet();

        void optOut(java.util.UUID uuid) {
            out.add(uuid);
        }

        @Override
        public boolean receivesBroadcasts(PlayerRef who) {
            return !out.contains(who.uuid());
        }

        @Override
        public boolean toggle(PlayerRef who) {
            return out.add(who.uuid());
        }

        @Override
        public void forget(PlayerRef who) {
            out.remove(who.uuid());
        }
    }

    private static final class ThrowingSink implements com.uxplima.uxmessentials.shared.application.port.MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            throw new AssertionError("the rich announcement path must not use the MessageSink");
        }
    }

    /**
     * Queues {@code asyncAfter} tasks instead of running them, so {@link #drainOne()} executes exactly one tick;
     * the loop's reschedule lands back in the queue and is left undrained. Synchronous hops run inline.
     */
    private static final class QueueScheduler implements Scheduler {
        private final Deque<Runnable> queue = new ArrayDeque<>();

        void drainOne() {
            Runnable next = queue.poll();
            if (next != null) {
                next.run();
            }
        }

        /**
         * Run exactly the tasks queued at the moment of the call (one "round" of ticks), leaving any reschedules
         * they enqueue for the next round. Snapshotting the size first keeps a self-rescheduling loop from running
         * forever within a single round.
         */
        void drainRound() {
            int pending = queue.size();
            for (int i = 0; i < pending; i++) {
                Runnable next = queue.poll();
                if (next != null) {
                    next.run();
                }
            }
        }

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
            queue.add(task);
        }
    }

    /** Synchronous scheduler for the inner ChannelBroadcaster fan-out (its hops must run inline within a tick). */
    private static final class SyncScheduler implements Scheduler {
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
}
