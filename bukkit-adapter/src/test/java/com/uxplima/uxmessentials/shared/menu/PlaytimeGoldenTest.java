package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.PlaytimeView;
import com.uxplima.uxmessentials.playerstate.application.PlayerstateMessageKey;
import com.uxplima.uxmessentials.playerstate.application.ShowPlaytime;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerInfo;
import com.uxplima.uxmessentials.playerstate.application.port.PlaytimeRepository;
import com.uxplima.uxmessentials.playerstate.domain.PlaytimeSummary;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.SettingsPanelView;
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
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
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
 * The playtime golden test, covering a read-only display panel. The migrated {@link PlaytimeView} now opens through
 * {@link Menus#openEditor} (its {@link SettingsPanelView} is a thin shim over the engine), so this asserts the
 * engine-rendered editor draws the exact read-only breakdown panel the bespoke view drew: same material and plain
 * name at every row slot and the back slot, and the same value-lore per row. Every row is a {@code PlaytimeRowProperty}
 * the viewer inspects but cannot change, so the test also fires a click on a row through the engine's own
 * {@link MenuListener} and asserts it is inert, no crash, no navigation. The baseline is frozen from the panel's
 * geometry + catalog keys, the way the kit/warp golden tests freeze a baseline.
 */
class PlaytimeGoldenTest {

    private static final Material FILLER = Material.BLACK_STAINED_GLASS_PANE;
    private static final List<Integer> ROW_SLOTS = List.of(10, 12, 14, 16, 22);
    private static final int BACK_SLOT = 26;

    @TempDir
    Path dir;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Messages messages;
    private SyncScheduler scheduler;
    private ShowPlaytime show;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        messages = new KeyMessages();
        guiText = new GuiText(messages);
        scheduler = new SyncScheduler();
        Notifier notifier = new Notifier(new KeyMessages(), new NoopSink());
        show = new ShowPlaytime(new EmptyRepo(), new EmptyInfo(), notifier, Clock.systemUTC());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameReadOnlyPanelAsTheOldView() throws Exception {
        Map<Integer, Snapshot> baseline = baseline();
        Map<Integer, Snapshot> engine = snapshotEngine();

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
        ROW_SLOTS.forEach(slot -> assertThat(engine).containsKey(slot));
        assertThat(engine).containsKey(BACK_SLOT);
    }

    @Test
    void clickingAReadOnlyRowThroughTheEngineIsInert() throws Exception {
        view().open(player, viewer, viewer);
        Inventory before = player.getOpenInventory().getTopInventory();
        assertThat(before.getHolder()).isInstanceOf(MenuHolder.class);

        // A breakdown row neither mutates nor navigates; the click is cancelled and the window stays put.
        assertThatCode(() -> fireClick(ROW_SLOTS.get(0), ClickType.LEFT)).doesNotThrowAnyException();

        Inventory after = player.getOpenInventory().getTopInventory();
        assertThat(after).isSameAs(before);
    }

    // --- snapshots ---

    private Map<Integer, Snapshot> snapshotEngine() throws Exception {
        view().open(player, viewer, viewer);
        return snapshot(player.getOpenInventory().getTopInventory());
    }

    /**
     * The frozen parity baseline: the {@code (slot → material, name, value-lore)} the read-only breakdown panel drew.
     * Names resolve to each row's label key; the four breakdown rows render the {@code row-value} catalog line and the
     * lifetime row the {@code lifetime-value} line, each wrapped by the panel's value-lore key.
     */
    private Map<Integer, Snapshot> baseline() {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        out.put(ROW_SLOTS.get(0), row(Material.CLOCK, PlayerstateMessageKey.PLAYTIME_GUI_TODAY, false));
        out.put(ROW_SLOTS.get(1), row(Material.COMPASS, PlayerstateMessageKey.PLAYTIME_GUI_WEEK, false));
        out.put(ROW_SLOTS.get(2), row(Material.MAP, PlayerstateMessageKey.PLAYTIME_GUI_MONTH, false));
        out.put(ROW_SLOTS.get(3), row(Material.BOOK, PlayerstateMessageKey.PLAYTIME_GUI_TOTAL, false));
        out.put(ROW_SLOTS.get(4), row(Material.NETHER_STAR, PlayerstateMessageKey.PLAYTIME_GUI_LIFETIME, true));
        out.put(BACK_SLOT, new Snapshot(Material.ARROW, PlayerstateMessageKey.PLAYTIME_GUI_BACK.key(), ""));
        return out;
    }

    private Snapshot row(Material icon, PlayerstateMessageKey label, boolean lifetime) {
        String value = lifetime
                ? PlayerstateMessageKey.PLAYTIME_GUI_LIFETIME_VALUE.key()
                : PlayerstateMessageKey.PLAYTIME_GUI_ROW_VALUE.key();
        return new Snapshot(icon, label.key(), "value=" + value);
    }

    // --- harness ---

    private PlaytimeView view() throws Exception {
        writeLayout();
        GuiLayouts layouts = new GuiLayouts(dir, NOOP);
        return new PlaytimeView(guiText, scheduler, layouts, messages, show, engine());
    }

    /** A minimal editor-capable engine + listener so the migrated panel opens through the runtime. */
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
        Path file = dir.resolve("modules").resolve("playerstate").resolve("gui").resolve("playtime-panel.conf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                rows = 3
                property-slots = [10, 12, 14, 16, 22]
                back-slot = 26
                delete-slot = -1
                back-icon = "ARROW"
                delete-icon = "BARRIER"
                filler = "BLACK_STAINED_GLASS_PANE"
                """);
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == FILLER) {
                continue;
            }
            out.put(slot, new Snapshot(item.getType(), plainName(item), valueLoreOrEmpty(item)));
        }
        return out;
    }

    private static String plainName(ItemStack item) {
        // The title reads off the tile wherever the canon puts it: the display name of a bare button, or the
        // first lore line of a titled tile, whose display name is deliberately blank.
        return TileText.title(item);
    }

    private static String valueLoreOrEmpty(ItemStack item) {
        // Under the title line the canon puts at the top of a titled tile: the body is what the value-lore is.
        List<Component> lore = TileText.body(item);
        if (lore.isEmpty()) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(lore.get(0));
    }

    private record Snapshot(Material material, String name, String valueLore) {}

    // --- fakes ---

    /** A repository with no tracked rows, so every breakdown window reads back the clean all-zero summary. */
    private static final class EmptyRepo implements PlaytimeRepository {
        @Override
        public void addSeconds(UUID uuid, LocalDate day, long activeDelta, long afkDelta) {}

        @Override
        public PlaytimeSummary summaryOf(UUID uuid, LocalDate today) {
            return PlaytimeSummary.empty();
        }

        @Override
        public void reset(UUID uuid) {}

        @Override
        public void resetAll() {}
    }

    /** No vanilla play-one-minute statistic, so the lifetime row falls back to the tracked all-time total (zero). */
    private static final class EmptyInfo implements PlayerInfo {
        @Override
        public Optional<Position> positionOf(PlayerRef who) {
            return Optional.empty();
        }

        @Override
        public Optional<Integer> pingOf(PlayerRef who) {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> playtimeOf(PlayerRef who) {
            return Optional.empty();
        }
    }

    /**
     * Special-cases the panel's value-lore key to wrap the substituted value; every other key (row labels, the
     * row-value / lifetime-value lines, back) echoes itself, so the frozen baseline reads deterministically.
     */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals(PlayerstateMessageKey.PLAYTIME_GUI_VALUE_LORE.key())) {
                return "value=" + placeholders.getOrDefault("value", "");
            }
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final com.uxplima.uxmessentials.shared.application.port.Logger NOOP =
            new com.uxplima.uxmessentials.shared.application.port.Logger() {
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
