package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.api.event.MenuClickEvent;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.api.event.MenuOpenEvent;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.bedrock.BedrockButton;
import com.uxplima.uxmlib.bedrock.BedrockDetector;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.bedrock.BedrockWidget;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The end-to-end proof for the two public dev-API events. A real Bukkit {@link Listener} is registered through
 * {@link org.bukkit.plugin.PluginManager#registerEvents}, which itself proves each event carries the required static
 * {@code getHandlerList()}: registration reflects on it and throws when it is missing. The live {@link Menus} facade
 * and {@link MenuListener} drive open → event → effect, so these are integration goldens, not unit checks of the event
 * records.
 *
 * <p>{@code MenuOpenEvent}: an uncancelled open fires the event and shows the menu; a cancelled one shows neither a
 * chest nor a native Bedrock form. {@code MenuClickEvent}: an uncancelled click fires the event and runs the button's
 * actions; a cancelled one fires the event but runs none of them, while the vanilla click stays cancelled either way.
 */
class MenuEventGoldenTest {

    private static final String SPEC = """
            rows = 1
            items {
              button { slot = 0, material = DIAMOND, name = "x", click {
                left { click = ["record:clicked"] }
              } }
            }
            """;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private Menus menus;
    private MenuRenderer renderer;
    private Scheduler scheduler;
    private MenuBindings bindings;
    private MenuSpecLoader loader;
    private List<String> notes;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        notes = new ArrayList<>();

        bindings = new MenuBindings();
        bindings.action("record", ctx -> notes.add(ctx.arg()));

        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        scheduler = new SyncScheduler();
        loader = new MenuSpecLoader();

        menus = new Menus(renderer, scheduler, bindings.lists());
        menus.registerSpec("menu", loader.parse(SPEC));
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void anUncancelledOpenFiresTheEventAndOpensTheMenu() {
        OpenRecorder recorder = new OpenRecorder(false);
        server.getPluginManager().registerEvents(recorder, plugin);

        open(menus, "menu");

        assertThat(recorder.events).hasSize(1);
        MenuOpenEvent fired = recorder.events.get(0);
        assertThat(fired.getMenuId()).isEqualTo("menu");
        assertThat(fired.getViewer()).isEqualTo(player);
        assertThat(fired.getPage()).isZero();
        assertThat(menuOpen()).as("an uncancelled open shows the menu window").isTrue();
    }

    @Test
    void aCancelledOpenShowsNoChestMenu() {
        server.getPluginManager().registerEvents(new OpenRecorder(true), plugin);

        open(menus, "menu");

        assertThat(menuOpen())
                .as("a cancelled open must not put a menu window on the viewer's screen")
                .isFalse();
    }

    @Test
    void aBedrockViewerOpenSendsAFormWhenNotCancelled() {
        RecordingScreen screen = new RecordingScreen();
        Menus bedrockMenus = bedrockMenus(screen);
        server.getPluginManager().registerEvents(new OpenRecorder(false), plugin);

        open(bedrockMenus, "menu");

        assertThat(screen.forms)
                .as("an uncancelled open for a Bedrock viewer sends the native form")
                .hasSize(1);
    }

    @Test
    void aCancelledOpenSendsNoBedrockForm() {
        RecordingScreen screen = new RecordingScreen();
        Menus bedrockMenus = bedrockMenus(screen);
        server.getPluginManager().registerEvents(new OpenRecorder(true), plugin);

        open(bedrockMenus, "menu");

        assertThat(screen.forms)
                .as("a cancelled open fires before the Bedrock branch, so no form is sent")
                .isEmpty();
        assertThat(menuOpen()).as("a cancelled open shows no chest either").isFalse();
    }

    @Test
    void anUncancelledClickFiresTheEventAndRunsTheActions() {
        ClickRecorder recorder = new ClickRecorder(false);
        server.getPluginManager().registerEvents(recorder, plugin);
        open(menus, "menu");

        leftClick(0);

        assertThat(recorder.events).hasSize(1);
        MenuClickEvent fired = recorder.events.get(0);
        assertThat(fired.getMenuId()).isEqualTo("menu");
        assertThat(fired.getSlot()).isZero();
        assertThat(fired.getItemId()).isEqualTo("button");
        assertThat(notes).as("an uncancelled click runs the button's actions").containsExactly("clicked");
    }

    @Test
    void aCancelledClickFiresTheEventButRunsNoActions() {
        ClickRecorder recorder = new ClickRecorder(true);
        server.getPluginManager().registerEvents(recorder, plugin);
        open(menus, "menu");

        leftClick(0);

        assertThat(recorder.events)
                .as("the event still fires so a listener sees the vetoed click")
                .hasSize(1);
        assertThat(notes)
                .as("a cancelled click skips the whole click-handling, so no action runs")
                .isEmpty();
    }

    private Menus bedrockMenus(BedrockScreen screen) {
        BedrockDetector everyoneBedrock = uuid -> true;
        Menus bedrock =
                new Menus(renderer, scheduler, bindings.lists(), null, null, null, null, everyoneBedrock, screen);
        bedrock.registerSpec("menu", loader.parse(SPEC));
        return bedrock;
    }

    /** Whether the viewer's open top inventory is an engine menu window: false when nothing (or no menu) is open. */
    private boolean menuOpen() {
        InventoryView view = player.getOpenInventory();
        if (view == null) {
            return false;
        }
        Inventory top = view.getTopInventory();
        return top != null && top.getHolder() instanceof MenuHolder;
    }

    private void open(Menus target, String specId) {
        target.open(new PlayerRef(player.getUniqueId(), player.getName()), specId, null);
    }

    private void leftClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private static final class OpenRecorder implements Listener {
        final List<MenuOpenEvent> events = new ArrayList<>();
        private final boolean cancel;

        OpenRecorder(boolean cancel) {
            this.cancel = cancel;
        }

        // The event bus invokes this reflectively, so Error Prone cannot see the call site.
        @SuppressWarnings("UnusedMethod")
        @EventHandler
        public void onMenuOpen(MenuOpenEvent event) {
            events.add(event);
            if (cancel) {
                event.setCancelled(true);
            }
        }
    }

    private static final class ClickRecorder implements Listener {
        final List<MenuClickEvent> events = new ArrayList<>();
        private final boolean cancel;

        ClickRecorder(boolean cancel) {
            this.cancel = cancel;
        }

        // The event bus invokes this reflectively, so Error Prone cannot see the call site.
        @SuppressWarnings("UnusedMethod")
        @EventHandler
        public void onMenuClick(MenuClickEvent event) {
            events.add(event);
            if (cancel) {
                event.setCancelled(true);
            }
        }
    }

    /** A fake Bedrock screen that records the SimpleForm sends, so a cancelled open can be proved to send none. */
    private static final class RecordingScreen implements BedrockScreen {
        final List<String> forms = new ArrayList<>();

        @Override
        public void sendSimpleForm(
                Player player,
                String title,
                @Nullable String content,
                List<BedrockButton> buttons,
                IntConsumer onSelect) {
            forms.add(title);
        }

        @Override
        public void sendModalForm(
                Player player,
                String title,
                @Nullable String content,
                String button1,
                String button2,
                Runnable onButton1,
                Runnable onButton2) {}

        @Override
        public void sendInputForm(
                Player player,
                String title,
                String inputLabel,
                @Nullable String initial,
                Consumer<String> onSubmit,
                Runnable onClose) {}

        @Override
        public void sendCustomForm(
                Player player,
                String title,
                @Nullable String content,
                List<BedrockWidget> widgets,
                Consumer<Map<String, String>> onSubmit,
                Runnable onClose) {}
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
