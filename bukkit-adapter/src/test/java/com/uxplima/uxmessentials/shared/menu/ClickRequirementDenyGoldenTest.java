package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.RequirementConditions;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.Currencies;
import com.uxplima.uxmessentials.shared.adapter.outbound.meta.PlayerMeta;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
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
 * The end-to-end proof that a click requirement block gates the click's actions and routes to its deny actions when it
 * fails. The real {@link MenuSpecLoader} reads the map form, the real {@link RequirementConditions} back the
 * {@code has-empty-slots} gate, and the live {@link MenuListener} evaluates the block, so these drive config → open →
 * click → effect, not any part in isolation. A recording {@code record-note} action captures which branch ran, and the
 * viewer's real inventory drives whether the gate holds. Inversion and minimum (AND / OR / N-of-M) are exercised the
 * same way.
 */
class ClickRequirementDenyGoldenTest {

    private static final String BASIC = """
            rows = 1
            items {
              a { slot = 0, material = DIAMOND, name = "x", click {
                left { click = ["record-note:ran"], requirements = ["has-empty-slots:1"], deny = ["record-note:denied"] }
                right = ["record-note:bare"]
              } }
            }
            """;

    private static final String INVERTED = """
            rows = 1
            items {
              a { slot = 0, material = DIAMOND, name = "x", click {
                left { click = ["record-note:ran"], requirements = ["!has-empty-slots:1"], deny = ["record-note:denied"] }
              } }
            }
            """;

    private static final String OR = """
            rows = 1
            items {
              a { slot = 0, material = DIAMOND, name = "x", click {
                left { click = ["record-note:ran"], requirements = ["has-empty-slots:1", "has-empty-slots:99"],
                       minimum = 1, deny = ["record-note:denied"] }
              } }
            }
            """;

    private static final String AND = """
            rows = 1
            items {
              a { slot = 0, material = DIAMOND, name = "x", click {
                left { click = ["record-note:ran"], requirements = ["has-empty-slots:1", "has-empty-slots:99"],
                       deny = ["record-note:denied"] }
              } }
            }
            """;

    private static final String MIN_TWO = """
            rows = 1
            items {
              a { slot = 0, material = DIAMOND, name = "x", click {
                left { click = ["record-note:ran"], requirements = ["has-empty-slots:1", "has-empty-slots:9"],
                       minimum = 2, deny = ["record-note:denied"] }
              } }
            }
            """;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private Menus menus;
    private List<String> notes;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        notes = new ArrayList<>();

        Logger log = new NoopLogger();
        MenuBindings bindings = new MenuBindings();
        bindings.action("record-note", ctx -> notes.add(ctx.arg()));
        Currencies currencies = new Currencies(() -> null, log, "vault");
        RequirementConditions.register(bindings, currencies, new PlayerMeta(plugin), log);

        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Scheduler scheduler = new SyncScheduler();
        menus = new Menus(renderer, scheduler, bindings.lists());
        MenuSpecLoader loader = new MenuSpecLoader();
        menus.registerSpec("basic", loader.parse(BASIC));
        menus.registerSpec("inverted", loader.parse(INVERTED));
        menus.registerSpec("or", loader.parse(OR));
        menus.registerSpec("and", loader.parse(AND));
        menus.registerSpec("min2", loader.parse(MIN_TWO));
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void thePassingBlockRunsTheMainActionAndNoDeny() {
        open("basic");

        leftClick(0);

        assertThat(notes)
                .as("an empty slot satisfies the gate, so the main action runs alone")
                .containsExactly("ran");
    }

    @Test
    void theFailingBlockRunsTheDenyActionAndNotTheMain() {
        fillInventory();
        open("basic");

        leftClick(0);

        assertThat(notes)
                .as("a full inventory fails the gate, so the deny action runs and the main does not")
                .containsExactly("denied");
    }

    @Test
    void aBareListGestureStillRunsWithNoRequirement() {
        fillInventory();
        open("basic");

        rightClick(0);

        assertThat(notes)
                .as("the right gesture has no requirement block, so it always runs")
                .containsExactly("bare");
    }

    @Test
    void anInvertedRequirementPassesWhenTheInventoryIsFull() {
        fillInventory();
        open("inverted");

        leftClick(0);

        assertThat(notes)
                .as("!has-empty-slots:1 passes exactly when there is no empty slot")
                .containsExactly("ran");
    }

    @Test
    void anInvertedRequirementFailsWhenTheInventoryHasRoom() {
        open("inverted");

        leftClick(0);

        assertThat(notes)
                .as("with room, the negated gate fails and routes to deny")
                .containsExactly("denied");
    }

    @Test
    void minimumOnePassesWhenEitherRequirementHolds() {
        open("or");

        leftClick(0);

        assertThat(notes)
                .as("has-empty-slots:1 holds while has-empty-slots:99 does not, and minimum 1 needs only one")
                .containsExactly("ran");
    }

    @Test
    void theDefaultMinimumRequiresEveryRequirement() {
        open("and");

        leftClick(0);

        assertThat(notes)
                .as("the default minimum is AND, so one failing requirement denies the whole block")
                .containsExactly("denied");
    }

    @Test
    void minimumTwoRequiresBothRequirements() {
        open("min2");

        leftClick(0);

        assertThat(notes)
                .as("both has-empty-slots:1 and has-empty-slots:9 hold for a fresh viewer, meeting minimum 2")
                .containsExactly("ran");
    }

    private void fillInventory() {
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.STONE));
        }
    }

    private void open(String specId) {
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), specId, null);
    }

    private void leftClick(int slot) {
        click(slot, ClickType.LEFT);
    }

    private void rightClick(int slot) {
        click(slot, ClickType.RIGHT);
    }

    private void click(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
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

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            return () -> {};
        }
    }
}
