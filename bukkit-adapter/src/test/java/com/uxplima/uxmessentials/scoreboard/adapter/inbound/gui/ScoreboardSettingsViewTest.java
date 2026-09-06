package com.uxplima.uxmessentials.scoreboard.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.scoreboard.application.ToggleScoreboard;
import com.uxplima.uxmessentials.scoreboard.application.port.ScoreboardVisibilityStore;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ActionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the scoreboard per-player settings panel. The single show/hide toggle reads the live
 * {@link ScoreboardVisibilityStore} FRESH each open and writes through the real {@link ToggleScoreboard} use case
 * the {@code /scoreboard} command uses: it renders at its conf slot, a click flips the stored bit, and a re-render
 * shows the new state. The board a viewer sees is resolved automatically by condition and priority, so the panel
 * exposes no board-picker: there is no use case for one. The scheduler is synchronous so the off-thread setter
 * runs inline.
 */
class ScoreboardSettingsViewTest {

    private static final int TOGGLE_SLOT = 13;

    @TempDir
    Path dir;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private InMemoryVisibility visibility;
    private ToggleScoreboard toggle;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        visibility = new InMemoryVisibility();
        Notifier notifier = new Notifier(new KeyMessages(), new NoopSink());
        toggle = new ToggleScoreboard(visibility, notifier, new NoopEvents());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rendersTheToggleAtItsConfSlot() throws Exception {
        view().open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(inv.getItem(TOGGLE_SLOT).getType()).isEqualTo(Material.PAINTING); // visibility toggle
        assertThat(inv.getItem(22).getType()).isEqualTo(Material.ARROW); // back / close
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.BLACK_STAINED_GLASS_PANE); // filler
    }

    @Test
    void clickingTheToggleHidesTheBoardThroughTheUseCase() throws Exception {
        assertThat(visibility.hidden(viewer)).isFalse(); // shown by default
        view().open(player, viewer);

        fireClick(TOGGLE_SLOT, ClickType.LEFT); // shown -> hidden through ToggleScoreboard

        assertThat(visibility.hidden(viewer)).isTrue();
    }

    @Test
    void clickingTheToggleAgainShowsTheBoardAgain() throws Exception {
        visibility.toggle(viewer); // start hidden
        assertThat(visibility.hidden(viewer)).isTrue();
        view().open(player, viewer);

        fireClick(TOGGLE_SLOT, ClickType.LEFT); // hidden -> shown through ToggleScoreboard

        assertThat(visibility.hidden(viewer)).isFalse();
    }

    @Test
    void openingReflectsTheStoredStateAfterAFlip() throws Exception {
        view().open(player, viewer);
        assertThat(loreOf(TOGGLE_SLOT)).contains("scoreboard.gui.value-shown"); // shown initially

        fireClick(TOGGLE_SLOT, ClickType.LEFT); // hide; the panel re-renders to the new state

        assertThat(loreOf(TOGGLE_SLOT)).contains("scoreboard.gui.value-hidden");
    }

    private ScoreboardSettingsView view() throws Exception {
        writeLayout();
        GuiLayouts layouts = new GuiLayouts(dir, NOOP);
        return new ScoreboardSettingsView(guiText, scheduler, layouts, new KeyMessages(), visibility, toggle, engine());
    }

    /** A minimal editor-capable engine + listener so the migrated panel can open through the runtime. */
    private Menus engine() {
        EditorRenderer editorRenderer = new EditorRenderer(guiText);
        ItemRenderer itemRenderer = new ItemRenderer(guiText, new PlaceholderRegistry());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        Menus menus = new Menus(renderer, scheduler, new ListSourceRegistry(), editorRenderer);
        MenuListener listener = new MenuListener(
                renderer,
                new ActionRegistry(),
                new ConditionRegistry(),
                scheduler,
                plugin,
                editorRenderer,
                menus.selectorOpener(),
                menus.confirmOpener());
        server.getPluginManager().registerEvents(listener, plugin);
        return menus;
    }

    private void writeLayout() throws Exception {
        Path file = dir.resolve("modules").resolve("scoreboard").resolve("gui").resolve("scoreboard-settings.conf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                rows = 3
                property-slots = [13]
                back-slot = 22
                delete-slot = -1
                back-icon = "ARROW"
                delete-icon = "BARRIER"
                filler = "BLACK_STAINED_GLASS_PANE"
                """);
    }

    private String loreOf(int slot) {
        var lore = player.getOpenInventory()
                .getTopInventory()
                .getItem(slot)
                .getItemMeta()
                .lore();
        var serializer = PlainTextComponentSerializer.plainText();
        StringBuilder out = new StringBuilder();
        if (lore != null) {
            lore.forEach(line -> out.append(serializer.serialize(line)).append('\n'));
        }
        return out.toString();
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** A simple in-memory "hidden" preference standing in for the PDC-backed store. */
    private static final class InMemoryVisibility implements ScoreboardVisibilityStore {
        private final Set<java.util.UUID> hidden = new HashSet<>();

        @Override
        public boolean hidden(PlayerRef who) {
            return hidden.contains(who.uuid());
        }

        @Override
        public boolean toggle(PlayerRef who) {
            if (hidden.add(who.uuid())) {
                return true;
            }
            hidden.remove(who.uuid());
            return false;
        }

        @Override
        public void forget(PlayerRef who) {
            hidden.remove(who.uuid());
        }
    }

    /** Echoes the key and any placeholder values so a rendered value-lore reveals the substituted state. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (placeholders.isEmpty()) {
                return key.key();
            }
            return key.key() + " " + String.join(" ", placeholders.values());
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class NoopEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final Logger NOOP = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };

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
