package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
 * The proof that a menu item's click action resolves {@code %argument_<name>%} from the arguments the menu was
 * opened with: the action-side twin of the renderer's TEXT substitution. A menu opened carrying {@code amount=5}
 * whose left-click runs {@code record:%argument_amount%} must reach the registered action with the value {@code 5};
 * an action with no argument token is unchanged; and a menu opened with no arguments passes the ref's args through
 * verbatim (the identity fast-path), so the literal token survives. These drive the whole open → click → effect path
 * against the live {@link MenuListener} rather than asserting the resolver in isolation.
 */
class MenuArgumentActionGoldenTest {

    private static final String GIVE_HOCON = """
            rows = 1
            items { b { slot = 0, material = DIAMOND, name = "x", click { left = ["record:%argument_amount%"] } } }
            """;

    private static final String PLAIN_HOCON = """
            rows = 1
            items { b { slot = 0, material = DIAMOND, name = "x", click { left = ["record:plain"] } } }
            """;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private Menus menus;
    private AtomicReference<String> captured;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        captured = new AtomicReference<>();

        GuiText guiText = new GuiText(new KeyMessages());
        MenuBindings bindings = new MenuBindings();
        bindings.action("record", ctx -> captured.set(ctx.arg()));
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Scheduler scheduler = new SyncScheduler();
        menus = new Menus(renderer, scheduler, bindings.lists());
        MenuSpecLoader loader = new MenuSpecLoader();
        menus.registerSpec("give", loader.parse(GIVE_HOCON));
        menus.registerSpec("plain", loader.parse(PLAIN_HOCON));
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void anArgumentTokenInAnActionArgIsResolvedFromTheOpenArguments() {
        openWith("give", Map.of("amount", "5"));

        leftClick(0);

        assertThat(captured.get())
                .as("a menu opened with amount=5 running record:%argument_amount% records 5")
                .isEqualTo("5");
    }

    @Test
    void anActionArgWithoutAnArgumentTokenIsUnchanged() {
        openWith("plain", Map.of("amount", "5"));

        leftClick(0);

        assertThat(captured.get()).isEqualTo("plain");
    }

    @Test
    void withNoOpenArgumentsTheRefArgsPassThroughUntouched() {
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), "give", null);

        leftClick(0);

        assertThat(captured.get())
                .as("the identity fast-path leaves the literal token when the menu carried no arguments")
                .isEqualTo("%argument_amount%");
    }

    private void openWith(String specId, Map<String, String> arguments) {
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), specId, null, 0, arguments);
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
