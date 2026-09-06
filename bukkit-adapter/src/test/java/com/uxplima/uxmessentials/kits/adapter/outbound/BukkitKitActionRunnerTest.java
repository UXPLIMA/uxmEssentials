package com.uxplima.uxmessentials.kits.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.kits.application.port.KitActionRunner;
import com.uxplima.uxmessentials.kits.domain.KitAction;
import com.uxplima.uxmessentials.kits.domain.KitActionType;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@link BukkitKitActionRunner}: a {@code MESSAGE} action reaches the player, a {@code
 * CONSOLE_COMMAND} marshals onto the global thread, and the {@code WAIT_TICKS} sequencer runs the actions on
 * both sides of the wait. Advancing through the recording {@link Scheduler} (whose {@code asyncAfter} fires
 * inline) rather than any {@code BukkitRunnable}. The scheduler also records which thread method each action was
 * dispatched on, so the test proves player effects hop to the entity thread and broadcasts/console commands hop
 * to the global thread.
 */
class BukkitKitActionRunnerTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ServerMock server;
    private PlayerMock player;
    private RecordingScheduler scheduler;
    private KitActionRunner runner;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        scheduler = new RecordingScheduler();
        runner = new BukkitKitActionRunner(scheduler, new NoopLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aMessageActionReachesThePlayerOnTheEntityThread() {
        runner.run(ref(), kit(), List.of(KitAction.of(KitActionType.MESSAGE, "<green>hi {player}")));

        assertThat(scheduler.dispatchedOn).containsExactly("entity");
        assertThat(nextLine()).isEqualTo("hi Alice");
    }

    @Test
    void aConsoleCommandRunsOnTheGlobalThread() {
        runner.run(ref(), kit(), List.of(KitAction.of(KitActionType.CONSOLE_COMMAND, "say claimed by {player}")));

        assertThat(scheduler.dispatchedOn).containsExactly("global");
    }

    @Test
    void aBroadcastRunsOnTheGlobalThreadAndPlayerEffectsOnTheEntityThread() {
        runner.run(
                ref(),
                kit(),
                List.of(
                        KitAction.of(KitActionType.BROADCAST, "<gold>{player} claimed!"),
                        KitAction.of(KitActionType.MESSAGE, "thanks")));

        assertThat(scheduler.dispatchedOn).containsExactly("global", "entity");
    }

    @Test
    void theWaitTicksSequencerRunsBothSidesOfTheWaitWithoutABukkitRunnable() {
        runner.run(
                ref(),
                kit(),
                List.of(
                        KitAction.of(KitActionType.MESSAGE, "before the wait"),
                        new KitAction(KitActionType.WAIT_TICKS, "20", false, false),
                        KitAction.of(KitActionType.MESSAGE, "after the wait")));

        // The asyncAfter delay fired inline (recorded once), 20 ticks at 50 ms each is exactly one second, and
        // both messages reached the player in order, proving the sequencer resumed without a BukkitRunnable.
        assertThat(scheduler.delays).containsExactly(Duration.ofSeconds(1));
        assertThat(nextLine()).isEqualTo("before the wait");
        assertThat(nextLine()).isEqualTo("after the wait");
    }

    @Test
    void aClearInventoryActionEmptiesTheInventoryOnTheEntityThread() {
        player.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND, 3));
        assertThat(player.getInventory().isEmpty()).isFalse();

        runner.run(ref(), kit(), List.of(new KitAction(KitActionType.CLEAR_INVENTORY, "", true, false)));

        assertThat(scheduler.dispatchedOn).containsExactly("entity");
        assertThat(player.getInventory().isEmpty()).isTrue();
    }

    @Test
    void aMalformedSoundIsSkippedAndDoesNotAbortTheSequence() {
        runner.run(
                ref(),
                kit(),
                List.of(
                        KitAction.of(KitActionType.SOUND, "not_a_real_sound;1;1"),
                        KitAction.of(KitActionType.MESSAGE, "still here")));

        // The bad sound logged-and-skipped; the following message still reached the player.
        assertThat(nextLine()).isEqualTo("still here");
    }

    private String nextLine() {
        return PLAIN.serialize(player.nextComponentMessage());
    }

    private PlayerRef ref() {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    private static KitDefinition kit() {
        return KitDefinition.repeatable(KitId.of("vote"), List.of(), Duration.ZERO);
    }

    /** A scheduler that runs every task inline and records the thread method and any delay used. */
    private static final class RecordingScheduler implements Scheduler {
        private final List<String> dispatchedOn = new ArrayList<>();
        private final List<Duration> delays = new ArrayList<>();

        @Override
        public void onGlobal(Runnable task) {
            dispatchedOn.add("global");
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            dispatchedOn.add("region");
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            dispatchedOn.add("entity");
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            delays.add(delay);
            task.run();
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    }
}
