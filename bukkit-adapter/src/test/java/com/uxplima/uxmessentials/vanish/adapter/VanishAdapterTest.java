package com.uxplima.uxmessentials.vanish.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.vanish.adapter.inbound.listener.VanishLifecycleListener;
import com.uxplima.uxmessentials.vanish.adapter.outbound.BukkitVanishLevelResolver;
import com.uxplima.uxmessentials.vanish.adapter.outbound.BukkitVanishView;
import com.uxplima.uxmessentials.vanish.adapter.outbound.InMemoryVanishStore;
import com.uxplima.uxmessentials.vanish.application.JoinVanishReconciler;
import com.uxplima.uxmessentials.vanish.application.SetVanishLevel;
import com.uxplima.uxmessentials.vanish.application.ToggleVanish;
import com.uxplima.uxmessentials.vanish.application.port.NetworkVanishStore;
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
 * MockBukkit coverage of the vanish adapters against a real (mock) Bukkit server: the {@link ToggleVanish} use case
 * over the in-memory {@link InMemoryVanishStore} authority and the level-aware {@link BukkitVanishView} backed by the
 * {@link BukkitVanishLevelResolver}, driving Bukkit's {@code hidePlayer}/{@code showPlayer} graph. A vanished caller
 * disappears from a viewer whose see level is below their use level and reappears on unvanish; a see-node holder is
 * exempt; a higher use level hides from a lower see level. The {@link VanishLifecycleListener} drops a quitting player's
 * vanish state. The {@link Scheduler} is a synchronous inline fake so the entity-thread hop runs deterministically.
 */
class VanishAdapterTest {

    private ServerMock server;
    private InMemoryVanishStore store;
    private BukkitVanishLevelResolver levels;
    private ToggleVanish toggleVanish;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        InlineScheduler scheduler = new InlineScheduler();
        store = new InMemoryVanishStore();
        levels = new BukkitVanishLevelResolver();
        BukkitVanishView view = new BukkitVanishView(MockBukkit.createMockPlugin(), scheduler, levels);
        Notifier notifier = new Notifier(new KeyMessages(), new DiscardingSink());
        toggleVanish =
                new ToggleVanish(store, view, levels, notifier, new NoopBuffs(), VanishBus.disabled(), event -> {});
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void vanishHidesTheCallerFromANonSeeViewerThenShowsAgain() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");

        toggleVanish.toggle(BukkitRefs.toRef(alice));
        assertThat(store.isVanished(alice.getUniqueId())).isTrue();
        assertThat(bob.canSee(alice)).isFalse(); // bob no longer sees the vanished alice

        toggleVanish.toggle(BukkitRefs.toRef(alice));
        assertThat(store.isVanished(alice.getUniqueId())).isFalse();
        assertThat(bob.canSee(alice)).isTrue(); // revealed again
    }

    @Test
    void aSeePermittedViewerStillSeesAVanishedPlayer() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.see", true);

        toggleVanish.toggle(BukkitRefs.toRef(alice));

        assertThat(staff.canSee(alice)).isTrue(); // a see-node holder (see level 1) clears the default use level
    }

    @Test
    void aHigherUseLevelHidesFromALowerSeeLevelButNotAnEqualOne() {
        PlayerMock alice = server.addPlayer("Alice");
        alice.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.use.level2", true);
        PlayerMock lowStaff = server.addPlayer("LowStaff");
        lowStaff.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.see.level1", true);
        PlayerMock highStaff = server.addPlayer("HighStaff");
        highStaff.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.see.level2", true);

        toggleVanish.toggle(BukkitRefs.toRef(alice)); // alice vanishes at use level 2

        assertThat(lowStaff.canSee(alice)).isFalse(); // see level 1 does not clear use level 2
        assertThat(highStaff.canSee(alice)).isTrue(); // see level 2 clears use level 2
    }

    @Test
    void theLifecycleListenerForgetsVanishOnQuit() {
        PlayerMock alice = server.addPlayer("Alice");
        toggleVanish.toggle(BukkitRefs.toRef(alice));
        assertThat(store.isVanished(alice.getUniqueId())).isTrue();
        SetVanishLevel setVanishLevel =
                new SetVanishLevel(store, new NoopView(), levels, new NoopBuffs(), VanishBus.disabled());
        VanishLifecycleListener listener = new VanishLifecycleListener(
                store,
                new NoopView(),
                setVanishLevel,
                toggleVanish,
                new JoinVanishReconciler(NetworkVanishStore.empty(), store),
                new com.uxplima.uxmessentials.vanish.application.VanishConfig(
                        true, true, false, true, true, true, true, true, true, true, false, "", "", "", "", false, true,
                        1),
                server);
        PlayerQuitEvent quit = new PlayerQuitEvent(
                alice, net.kyori.adventure.text.Component.text("Alice left"), PlayerQuitEvent.QuitReason.DISCONNECTED);

        listener.onQuit(quit);

        assertThat(quit.quitMessage()).isNull(); // a vanished player's quit line is suppressed
        assertThat(store.isVanished(alice.getUniqueId())).isFalse(); // vanish state dropped on quit
    }

    /** A view that records nothing: the lifecycle test only asserts store state and the quit message. */
    private static final class NoopView implements com.uxplima.uxmessentials.vanish.application.port.VanishView {
        @Override
        public void hide(PlayerRef who, VanishLevel level) {}

        @Override
        public void reveal(PlayerRef who) {}
    }

    /** A buffs port that does nothing: the toggle/level behaviour under test does not assert on buffs here. */
    private static final class NoopBuffs implements VanishBuffs {
        @Override
        public void apply(PlayerRef who) {}

        @Override
        public void clear(PlayerRef who) {}
    }

    /** A scheduler that runs every task inline so the entity-thread hop fires at once. */
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
}
