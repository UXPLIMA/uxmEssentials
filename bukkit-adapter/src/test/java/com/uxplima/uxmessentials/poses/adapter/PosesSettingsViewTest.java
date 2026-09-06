package com.uxplima.uxmessentials.poses.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.poses.adapter.inbound.gui.PosesSettingsView;
import com.uxplima.uxmessentials.poses.adapter.outbound.PdcPlayerSitPreferences;
import com.uxplima.uxmessentials.poses.application.PoseSessions;
import com.uxplima.uxmessentials.poses.application.TogglePlayerSit;
import com.uxplima.uxmessentials.poses.domain.PoseSession;
import com.uxplima.uxmessentials.poses.domain.PoseType;
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
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the poses settings/status panel: the window a bare {@code /poses} opens. It draws two
 * buttons over the shared settings-panel runtime: the current-pose status (a live read of the {@link PoseSessions}
 * registry) and the player-sit opt-out (a click flips the PDC preference through the same {@link TogglePlayerSit}
 * the {@code /poses toggle} command uses). Mirrors {@code PresenceSettingsViewTest}: opening reflects the stored
 * state, and a click on the opt-out flips it. The scheduler is synchronous so the redraw runs inline.
 */
class PosesSettingsViewTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private PoseSessions sessions;
    private PdcPlayerSitPreferences preferences;
    private TogglePlayerSit togglePlayerSit;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        sessions = new PoseSessions();
        preferences = new PdcPlayerSitPreferences();
        togglePlayerSit = new TogglePlayerSit(preferences);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void openingDrawsTheStatusAndPlayerSitButtonsAtTheirConfSlots(@TempDir Path dir) throws Exception {
        view(dir).open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(inv.getItem(11).getType()).isEqualTo(Material.ARMOR_STAND); // current-pose status
        assertThat(inv.getItem(15).getType()).isEqualTo(Material.SADDLE); // player-sit opt-out
        assertThat(inv.getItem(22).getType()).isEqualTo(Material.ARROW); // back / close
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.BLACK_STAINED_GLASS_PANE); // filler
    }

    @Test
    void clickingThePlayerSitButtonFlipsTheStoredPdcPreference(@TempDir Path dir) throws Exception {
        assertThat(preferences.allowsSitting(viewer)).isTrue(); // GSit default: others may sit on you
        view(dir).open(player, viewer);

        fireClick(15, ClickType.LEFT);

        assertThat(preferences.allowsSitting(viewer)).isFalse(); // flipped off through TogglePlayerSit
    }

    @Test
    void thePlayerSitButtonLoreReflectsTheStoredStateAfterAFlip(@TempDir Path dir) throws Exception {
        view(dir).open(player, viewer);
        assertThat(loreOf(15)).contains("poses.gui.value-on"); // allows sitting by default

        fireClick(15, ClickType.LEFT); // now refusing; the panel re-renders to the new state

        assertThat(loreOf(15)).contains("poses.gui.value-off");
    }

    @Test
    void theStatusButtonReadsTheLivePoseRegistry(@TempDir Path dir) throws Exception {
        PosesSettingsView view = view(dir);
        view.open(player, viewer);
        assertThat(loreOf(11)).contains("poses.gui.value-off"); // not posing yet

        sessions.start(new PoseSession(
                viewer, PoseType.SIT, new Position(WORLD, 0, 64, 0, 0f, 0f), "seat", null, Instant.EPOCH));
        view.open(player, viewer); // reopen reads the registry fresh

        assertThat(loreOf(11)).contains("poses.gui.value-on");
    }

    private PosesSettingsView view(Path dir) throws Exception {
        writeLayout(dir);
        GuiLayouts layouts = new GuiLayouts(dir, NOOP);
        return new PosesSettingsView(
                guiText, scheduler, layouts, new KeyMessages(), engine(), sessions, preferences, togglePlayerSit);
    }

    /** A minimal editor-capable engine + listener so the panel can open and route clicks through the runtime. */
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

    private void writeLayout(Path dir) throws Exception {
        Path file = dir.resolve("modules").resolve("poses").resolve("gui").resolve("poses-settings.conf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                rows = 3
                property-slots = [11, 15]
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
        var serializer = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
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

    /** Runs every scheduler hop inline, as the production schedulers would after their marshal. */
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
