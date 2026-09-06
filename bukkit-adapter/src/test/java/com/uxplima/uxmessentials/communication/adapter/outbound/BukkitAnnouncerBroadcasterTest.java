package com.uxplima.uxmessentials.communication.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.bukkit.entity.Player;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.communication.application.port.BroadcastOptOutStore;
import com.uxplima.uxmessentials.communication.domain.Announcement;
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
 * MockBukkit coverage of the rich announcer delivery in {@link BukkitAnnouncerBroadcaster}: a matching, opted-in
 * recipient receives the rendered chat line (MiniMessage parsed through the {@code HudText} PlaceholderAPI bridge),
 * an opted-out recipient is skipped, a recipient whose {@code DisplayCondition} does not match is skipped, and
 * {@code preview} reaches one viewer regardless of those gates. The scheduler runs every hop inline.
 */
class BukkitAnnouncerBroadcasterTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ServerMock server;
    private PlayerMock alice;
    private FakeOptOut optOut;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");
        optOut = new FakeOptOut();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void anOptedInMatchingRecipientReceivesTheRenderedLine() {
        BukkitAnnouncerBroadcaster broadcaster = broadcaster(player -> alwaysContext(player));

        broadcaster.broadcast(chat(DisplayCondition.always(), "<gold>hello %player_name%"));

        // MiniMessage parsed the colour tag; PlaceholderAPI is absent so the %papi% token is left as-is by the
        // identity bridge: what matters is the HudText render path ran end to end.
        assertThat(PLAIN.serialize(alice.nextComponentMessage())).isEqualTo("hello %player_name%");
    }

    @Test
    void anOptedOutRecipientIsSkipped() {
        optOut.optOut(alice.getUniqueId());
        BukkitAnnouncerBroadcaster broadcaster = broadcaster(player -> alwaysContext(player));

        broadcaster.broadcast(chat(DisplayCondition.always(), "hi"));

        assertThat(alice.nextComponentMessage()).isNull();
    }

    @Test
    void aRecipientWhoseConditionDoesNotMatchIsSkipped() {
        BukkitAnnouncerBroadcaster broadcaster = broadcaster(player -> alwaysContext(player));

        // permission:uxmessentials.vip: Alice is not op and holds no node, so the condition fails.
        broadcaster.broadcast(chat(new DisplayCondition.Permission("uxmessentials.vip"), "vip only"));

        assertThat(alice.nextComponentMessage()).isNull();
    }

    @Test
    void previewReachesTheViewerEvenWhenOptedOutAndConditionFails() {
        optOut.optOut(alice.getUniqueId());
        BukkitAnnouncerBroadcaster broadcaster = broadcaster(player -> alwaysContext(player));

        broadcaster.preview(chat(new DisplayCondition.Permission("uxmessentials.vip"), "<gray>peek"), alice);

        assertThat(PLAIN.serialize(alice.nextComponentMessage())).isEqualTo("peek");
    }

    @Test
    void theStringBroadcastFansOutOnTheGlobalRegionThread() {
        RecordingScheduler recording = new RecordingScheduler();
        ChannelBroadcaster channels = new ChannelBroadcaster(new SyncScheduler(), display());
        RecordingSink sink = new RecordingSink();
        BukkitAnnouncerBroadcaster broadcaster = new BukkitAnnouncerBroadcaster(
                sink, optOut, channels, BukkitAnnouncerBroadcasterTest::alwaysContext, recording);

        broadcaster.broadcast("hello");

        assertThat(recording.globalTasks).isOne();
        assertThat(sink.deliveredTo(alice.getUniqueId())).isTrue();
    }

    @Test
    void onlineCountIsResolvedOnTheGlobalRegionThread() {
        RecordingScheduler recording = new RecordingScheduler();
        ChannelBroadcaster channels = new ChannelBroadcaster(new SyncScheduler(), display());
        BukkitAnnouncerBroadcaster broadcaster = new BukkitAnnouncerBroadcaster(
                new ThrowingSink(), optOut, channels, BukkitAnnouncerBroadcasterTest::alwaysContext, recording);
        java.util.concurrent.atomic.AtomicInteger seen = new java.util.concurrent.atomic.AtomicInteger(-1);

        broadcaster.withOnlineCount(seen::set);

        assertThat(recording.globalTasks).isOne();
        assertThat(seen).hasValue(1); // exactly Alice is online
    }

    private BukkitAnnouncerBroadcaster broadcaster(Function<Player, ConditionContext> ctx) {
        ChannelBroadcaster channels = new ChannelBroadcaster(new SyncScheduler(), display());
        return new BukkitAnnouncerBroadcaster(new ThrowingSink(), optOut, channels, ctx, new SyncScheduler());
    }

    private static Announcement chat(DisplayCondition condition, String line) {
        return new Announcement(
                "ann",
                List.of(line),
                condition,
                Optional.empty(),
                Set.of(BroadcastChannel.CHAT),
                Optional.empty(),
                false);
    }

    private static ConditionContext alwaysContext(Player player) {
        return new ConditionContext(
                player::hasPermission,
                player.getWorld().getName(),
                player.getGameMode().name(),
                java.util.function.UnaryOperator.identity());
    }

    private static ChannelDisplay display() {
        return new ChannelDisplay(100, 500, 100, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS, 1);
    }

    /** Opt-out store backed by a UUID set; absent UUID means opted-in (receives broadcasts). */
    private static final class FakeOptOut implements BroadcastOptOutStore {
        private final Set<java.util.UUID> out = ConcurrentHashMap.newKeySet();

        void optOut(java.util.UUID uuid) {
            out.add(uuid);
        }

        @Override
        public boolean receivesBroadcasts(PlayerRef who) {
            return !out.contains(who.uuid());
        }

        @Override
        public boolean toggle(PlayerRef who) {
            return out.add(who.uuid()) ? true : !out.remove(who.uuid());
        }

        @Override
        public void forget(PlayerRef who) {
            out.remove(who.uuid());
        }
    }

    /**
     * The {@code MessageSink} is only used by the legacy {@code /broadcast} string path, which these tests do not
     * exercise; the rich announcement path goes through the {@link ChannelBroadcaster}. Fail loudly if it is hit.
     */
    private static final class ThrowingSink implements com.uxplima.uxmessentials.shared.application.port.MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            throw new AssertionError("the rich announcement path must not use the MessageSink");
        }
    }

    /** Like {@link SyncScheduler} but counts the {@code onGlobal} hops so the global-thread fan-out is assertable. */
    private static final class RecordingScheduler implements Scheduler {
        private int globalTasks;

        @Override
        public void onGlobal(Runnable task) {
            globalTasks++;
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

    /** Records which viewers the legacy {@code /broadcast} string path delivered to, by UUID. */
    private static final class RecordingSink implements com.uxplima.uxmessentials.shared.application.port.MessageSink {
        private final Set<java.util.UUID> viewers = ConcurrentHashMap.newKeySet();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            viewers.add(viewer.uuid());
        }

        boolean deliveredTo(java.util.UUID uuid) {
            return viewers.contains(uuid);
        }
    }

    /** Runs every hop inline so the entity-thread and async-after work executes within the test. */
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
