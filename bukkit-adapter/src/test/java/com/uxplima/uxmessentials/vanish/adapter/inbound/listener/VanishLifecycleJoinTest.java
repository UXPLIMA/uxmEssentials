package com.uxplima.uxmessentials.vanish.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.bukkit.event.player.PlayerJoinEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.vanish.adapter.outbound.BukkitVanishLevelResolver;
import com.uxplima.uxmessentials.vanish.adapter.outbound.BukkitVanishView;
import com.uxplima.uxmessentials.vanish.adapter.outbound.InMemoryNetworkVanishStore;
import com.uxplima.uxmessentials.vanish.adapter.outbound.InMemoryVanishStore;
import com.uxplima.uxmessentials.vanish.application.JoinVanishReconciler;
import com.uxplima.uxmessentials.vanish.application.SetVanishLevel;
import com.uxplima.uxmessentials.vanish.application.ToggleVanish;
import com.uxplima.uxmessentials.vanish.application.VanishConfig;
import com.uxplima.uxmessentials.vanish.application.VanishSync;
import com.uxplima.uxmessentials.vanish.application.port.VanishBuffs;
import com.uxplima.uxmessentials.vanish.application.port.VanishBus;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@link VanishLifecycleListener} join path: a player still marked vanished is re-hidden and
 * their real join line suppressed; a player holding {@code uxmessentials.vanish.persist} auto-vanishes on join when the
 * {@code join-vanished} gate is on; the whole behaviour is a no-op when the gate is off. An inline scheduler runs the
 * view's hops deterministically.
 */
class VanishLifecycleJoinTest {

    private static VanishConfig config(boolean joinVanished) {
        return new VanishConfig(
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                joinVanished,
                "",
                "",
                "",
                "",
                false,
                true,
                1);
    }

    private ServerMock server;
    private InMemoryVanishStore store;
    private InMemoryNetworkVanishStore network;
    private ToggleVanish toggleVanish;
    private SetVanishLevel setVanishLevel;
    private BukkitVanishView view;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        InlineScheduler scheduler = new InlineScheduler();
        store = new InMemoryVanishStore();
        network = new InMemoryNetworkVanishStore();
        BukkitVanishLevelResolver levels = new BukkitVanishLevelResolver();
        view = new BukkitVanishView(MockBukkit.createMockPlugin(), scheduler, levels);
        Notifier notifier = new Notifier(new KeyMessages(), new DiscardingSink());
        toggleVanish =
                new ToggleVanish(store, view, levels, notifier, new NoopBuffs(), VanishBus.disabled(), event -> {});
        setVanishLevel = new SetVanishLevel(store, view, levels, new NoopBuffs(), VanishBus.disabled());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aStillVanishedPlayerIsReHiddenAndTheirJoinLineSuppressed() {
        PlayerMock alice = server.addPlayer("Alice");
        store.vanish(alice.getUniqueId(), VanishLevel.DEFAULT); // still marked vanished on (re)join
        PlayerJoinEvent event = join(alice);

        listener(config(true)).onJoin(event);

        assertThat(store.isVanished(alice.getUniqueId())).isTrue();
        assertThat(event.joinMessage()).isNull();
    }

    @Test
    void aPersistPlayerAutoVanishesOnJoin() {
        PlayerMock alice = server.addPlayer("Alice");
        alice.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.persist", true);
        PlayerJoinEvent event = join(alice);

        listener(config(true)).onJoin(event);

        assertThat(store.isVanished(alice.getUniqueId())).isTrue();
        assertThat(event.joinMessage()).isNull();
    }

    @Test
    void joinVanishedOffLeavesAPersistPlayerVisible() {
        PlayerMock alice = server.addPlayer("Alice");
        alice.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.persist", true);
        PlayerJoinEvent event = join(alice);

        listener(config(false)).onJoin(event);

        assertThat(store.isVanished(alice.getUniqueId())).isFalse();
        assertThat(event.joinMessage()).isNotNull(); // the real join line is left intact
    }

    @Test
    void aNetworkVanishedPlayerArrivesHiddenOnJoin() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob"); // a non-see viewer already online
        // Alice vanished on another backend; every backend's network view recorded it.
        network.apply(VanishSync.vanished(new PlayerRef(alice.getUniqueId(), "Alice"), VanishLevel.DEFAULT));
        PlayerJoinEvent event = join(alice);

        listener(config(false)).onJoin(event); // join-vanished off. The hide comes purely from the network view

        assertThat(store.isVanished(alice.getUniqueId())).isTrue(); // seeded from the synced state
        assertThat(event.joinMessage()).isNull(); // arrives silently
        assertThat(bob.canSee(alice)).isFalse(); // hidden from a non-see viewer on arrival
    }

    private VanishLifecycleListener listener(VanishConfig config) {
        return new VanishLifecycleListener(
                store, view, setVanishLevel, toggleVanish, new JoinVanishReconciler(network, store), config, server);
    }

    private static PlayerJoinEvent join(PlayerMock player) {
        return new PlayerJoinEvent(player, Component.text(player.getName() + " joined"));
    }

    /** A buffs port that does nothing: the join behaviour under test does not assert on buffs. */
    private static final class NoopBuffs implements VanishBuffs {
        @Override
        public void apply(PlayerRef who) {}

        @Override
        public void clear(PlayerRef who) {}
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
            // discarded: the joiner's own confirmation is not under test here
        }
    }

    /** A scheduler that runs every task inline so the view's entity hop fires at once. */
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
}
