package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
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
 * End-to-end coverage of per-action modifiers through the real {@link MenuListener}, the only path that applies a
 * click's chance roll, delay, and deny fallback. Two recording actions, {@code hit} (the main effect) and
 * {@code miss} (the deny fallback): count their fires, so which branch ran is observed concretely. The chance
 * boundaries (0 and 100) are deterministic, so no test depends on the RNG; the delay is proven with a scheduler
 * that captures the off-tick task and lets the test fire it, so nothing waits on a wall clock.
 */
class MenuActionModifiersGoldenTest {

    private static final String ALWAYS = """
            rows = 1
            items {
              b { slot = 0, material = DIAMOND, name = "x", click { left = [ { do = "hit", chance = 100, deny = "miss" } ] } }
            }
            """;

    private static final String NEVER = """
            rows = 1
            items {
              b { slot = 0, material = DIAMOND, name = "x", click { left = [ { do = "hit", chance = 0, deny = "miss" } ] } }
            }
            """;

    private static final String NEVER_NO_DENY = """
            rows = 1
            items {
              b { slot = 0, material = DIAMOND, name = "x", click { left = [ { do = "hit", chance = 0 } ] } }
            }
            """;

    private static final String DELAYED = """
            rows = 1
            items {
              b { slot = 0, material = DIAMOND, name = "x", click { left = [ { do = "hit", delay = 20 } ] } }
            }
            """;

    private static final String PLAIN = """
            rows = 1
            items {
              b { slot = 0, material = DIAMOND, name = "x", click { left = ["hit"] } }
            }
            """;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private Menus menus;
    private CapturingScheduler scheduler;
    private AtomicInteger mainHits;
    private AtomicInteger missHits;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        mainHits = new AtomicInteger();
        missHits = new AtomicInteger();

        GuiText guiText = new GuiText(new KeyMessages());
        MenuBindings bindings = new MenuBindings();
        bindings.action("hit", ctx -> mainHits.incrementAndGet());
        bindings.action("miss", ctx -> missHits.incrementAndGet());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        scheduler = new CapturingScheduler();
        menus = new Menus(renderer, scheduler, bindings.lists());
        MenuSpecLoader loader = new MenuSpecLoader();
        menus.registerSpec("always", loader.parse(ALWAYS));
        menus.registerSpec("never", loader.parse(NEVER));
        menus.registerSpec("never-no-deny", loader.parse(NEVER_NO_DENY));
        menus.registerSpec("delayed", loader.parse(DELAYED));
        menus.registerSpec("plain", loader.parse(PLAIN));
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void fullChanceAlwaysRunsTheMainActionAndNeverTheDeny() {
        open("always");

        leftClick(0);

        assertThat(mainHits.get()).as("chance 100 always fires the main action").isEqualTo(1);
        assertThat(missHits.get()).as("the deny never runs at full chance").isZero();
    }

    @Test
    void zeroChanceNeverRunsTheMainActionAndRunsTheDenyInstead() {
        open("never");

        leftClick(0);

        assertThat(mainHits.get()).as("chance 0 never fires the main action").isZero();
        assertThat(missHits.get())
                .as("the deny fallback runs when the roll fails")
                .isEqualTo(1);
    }

    @Test
    void zeroChanceWithNoDenyRunsNothing() {
        open("never-no-deny");

        leftClick(0);

        assertThat(mainHits.get()).isZero();
        assertThat(missHits.get()).isZero();
    }

    @Test
    void aDelayedActionRunsOnlyAfterItsScheduledTaskFires() {
        open("delayed");

        leftClick(0);

        assertThat(mainHits.get())
                .as("a delayed action does not fire on the click")
                .isZero();
        assertThat(scheduler.pendingDelayed())
                .as("the click scheduled one off-tick task")
                .isEqualTo(1);
        assertThat(scheduler.lastDelay())
                .as("20 ticks map to 1000ms (one second) off-tick")
                .isEqualTo(Duration.ofSeconds(1));

        scheduler.fireDelayed();

        assertThat(mainHits.get()).as("firing the delayed task runs the action").isEqualTo(1);
    }

    @Test
    void aPlainStringActionRunsImmediatelyWithNoModifiers() {
        open("plain");

        leftClick(0);

        assertThat(mainHits.get()).as("a plain action fires on the click").isEqualTo(1);
        assertThat(scheduler.pendingDelayed())
                .as("a plain action schedules no delayed task")
                .isZero();
    }

    private void open(String specId) {
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), specId, null);
    }

    /** Fire a left click at {@code slot} of the top (menu) inventory through the live listener. */
    private void leftClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent click = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click);
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /**
     * Runs every immediate hop inline (so a click's dispatch is observed synchronously) but captures each
     * {@code asyncAfter} task instead of running it, so a delayed action is proven to wait for {@link #fireDelayed}
     * rather than a wall-clock sleep. The captured delays are kept so the test can assert the tick-to-millis mapping.
     */
    private static final class CapturingScheduler implements Scheduler {
        private final List<Duration> delays = new ArrayList<>();
        private final List<Runnable> delayed = new ArrayList<>();

        int pendingDelayed() {
            return delayed.size();
        }

        Duration lastDelay() {
            return delays.get(delays.size() - 1);
        }

        void fireDelayed() {
            List<Runnable> pending = List.copyOf(delayed);
            delayed.clear();
            pending.forEach(Runnable::run);
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
            delays.add(delay);
            delayed.add(task);
        }

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            return () -> {};
        }
    }
}
