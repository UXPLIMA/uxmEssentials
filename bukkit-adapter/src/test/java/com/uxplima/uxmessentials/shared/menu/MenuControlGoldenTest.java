package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuControlActions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuVocabulary;
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
 * End-to-end coverage of the menu-control action pack through the real {@link MenuListener}, which is the only path
 * that hands an action a live control bound to the clicked window. A {@code %counter%} placeholder increments once
 * per repaint, so a re-render is observed concretely; a paginated list proves a page reset; and two registered specs
 * prove {@code open:<menu> [page]} opens the second menu at the requested page.
 */
class MenuControlGoldenTest {

    private static final String REFRESH_HOCON = """
            rows = 1
            items {
              info { slot = 0, material = STONE, name = "%counter%" }
              btn { slot = 1, material = DIAMOND, name = "Refresh", click { left = ["refresh"] } }
            }
            """;

    private static final String SLOT_HOCON = """
            rows = 1
            items {
              info { slot = 0, material = STONE, name = "%counter%" }
              in { slot = 1, material = DIAMOND, name = "In", click { left = ["refresh-slot:4"] } }
              out { slot = 2, material = REDSTONE, name = "Out", click { left = ["refresh-slot:99"] } }
            }
            """;

    private static final String LIST_HOCON = """
            rows = 1
            items {
              things { slots = ["0-5"], list { source = "t:list", template { material = STONE, name = "%v%" } } }
              next { slot = 7, type = NEXT, material = ARROW, name = "Next" }
              reset { slot = 8, material = BARRIER, name = "Reset", click { left = ["reset-pagination"] } }
            }
            """;

    private static final String MENU1_HOCON = """
            rows = 1
            items {
              toOne { slot = 0, material = STONE, name = "P1", click { left = ["open:menu2 1"] } }
              toZero { slot = 1, material = STONE, name = "P0", click { left = ["open:menu2"] } }
              bad { slot = 2, material = STONE, name = "Bad", click { left = ["open:ghost"] } }
            }
            """;

    private static final String MENU2_HOCON = """
            rows = 1
            items {
              x { slot = 0, material = PAPER, name = "menu2" }
            }
            """;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private Menus menus;
    private MenuBindings bindings;
    private AtomicInteger counter;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        counter = new AtomicInteger();

        GuiText guiText = new GuiText(new KeyMessages());
        bindings = new MenuBindings();
        bindings.placeholder("counter", ctx -> String.valueOf(counter.incrementAndGet()));
        bindings.placeholder("v", ctx -> ctx.entry(String.class));
        bindings.lists().register("t:list", ctx -> List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l"));
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Scheduler scheduler = new SyncScheduler();
        menus = new Menus(renderer, scheduler, bindings.lists());
        MenuVocabulary.registerActions(bindings, menus, true, new RecordingLogger());
        MenuControlActions.register(bindings, new RecordingLogger());
        MenuSpecLoader loader = new MenuSpecLoader();
        menus.registerSpec("refresh", loader.parse(REFRESH_HOCON));
        menus.registerSpec("slot", loader.parse(SLOT_HOCON));
        menus.registerSpec("list", loader.parse(LIST_HOCON));
        menus.registerSpec("menu1", loader.parse(MENU1_HOCON));
        menus.registerSpec("menu2", loader.parse(MENU2_HOCON));
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void refreshRepaintsTheMenuAtItsCurrentPage() {
        open("refresh");
        assertThat(counter.get()).as("open renders the counter once").isEqualTo(1);
        assertThat(plainName(0)).isEqualTo("1");

        leftClick(1);

        assertThat(counter.get()).as("refresh re-renders the menu").isEqualTo(2);
        assertThat(plainName(0)).isEqualTo("2");
    }

    @Test
    void refreshSlotInRangeRepaintsAndOutOfRangeIsANoOp() {
        open("slot");
        assertThat(counter.get()).isEqualTo(1);

        leftClick(1); // refresh-slot:4, in range for a nine-slot window
        assertThat(counter.get()).as("an in-range refresh-slot repaints").isEqualTo(2);

        leftClick(2); // refresh-slot:99, out of range
        assertThat(counter.get())
                .as("an out-of-range refresh-slot does nothing")
                .isEqualTo(2);
    }

    @Test
    void resetPaginationReturnsToPageZero() {
        open("list");
        assertThat(page()).isZero();
        assertThat(plainName(0)).isEqualTo("a");

        leftClick(7); // NEXT
        assertThat(page()).isEqualTo(1);
        assertThat(plainName(0)).isEqualTo("g");

        leftClick(8); // reset-pagination
        assertThat(page()).as("reset-pagination returns to page zero").isZero();
        assertThat(plainName(0)).isEqualTo("a");
    }

    @Test
    void openOpensAnotherMenuAtTheRequestedPage() {
        open("menu1");

        leftClick(0); // open:menu2 1

        assertThat(holder().specId()).isEqualTo("menu2");
        assertThat(page()).isEqualTo(1);
    }

    @Test
    void openDefaultsToPageZeroWhenNoPageGiven() {
        open("menu1");

        leftClick(1); // open:menu2

        assertThat(holder().specId()).isEqualTo("menu2");
        assertThat(page()).isZero();
    }

    @Test
    void openAnUnknownMenuFailsLoudly() {
        // The open action is unwrapped (no fail-soft), so an unknown spec id surfaces the façade's loud failure the
        // same way it did before paging was added: asserted directly, since a listener swallows a handler throw.
        Consumer<MenuActionContext> open =
                bindings.action("open").orElseThrow(() -> new AssertionError("open not registered"));
        PlayerRef ref = new PlayerRef(player.getUniqueId(), player.getName());
        MenuActionContext ctx =
                new MenuActionContext(MenuContext.of(ref, null, 0), player, ClickKind.LEFT, Map.of("value", "ghost"));

        assertThatThrownBy(() -> open.accept(ctx)).isInstanceOf(IllegalArgumentException.class);
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

    private MenuHolder holder() {
        return (MenuHolder) player.getOpenInventory().getTopInventory().getHolder();
    }

    private int page() {
        return holder().ctx().page();
    }

    private String plainName(int slot) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        var item = inv.getItem(slot);
        if (item == null || item.getItemMeta() == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class RecordingLogger implements Logger {
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
