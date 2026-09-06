package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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

import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.ModerationHistoryMenu;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The moderation history golden test: the engine-rendered read-only history list must draw the exact grid the
 * original {@code PlayerHistoryView} drew. The store holds two history rows for the target (a BAN and a MUTE), so the
 * list draws a BARRIER and a BOOK icon (content slots 0 and 1. The per-action material the deleted view's switch
 * resolved) and the two ARROW nav buttons (slots 48 and 50). The engine window is snapshotted as
 * {@code (slot -> material, plain name)} and asserted equal, slot for slot, to the baseline the old view produced for
 * this fixture, then frozen here as the contract so the old class could be deleted. Then a click on the first icon
 * through the engine's own {@link MenuListener} proves the migrated path is inert. An audit row is immutable, so the
 * window stays open and unchanged, exactly as the old read-only view behaved.
 *
 * <p>The {@code KeyMessages} catalog surfaces the entry name's {@code mod_history_action} token, so a row's action
 * appears in the rendered label; every other key renders verbatim. A real rendering difference (a wrong key, a wrong
 * material, a misplaced cell, a lost action token) therefore still shows up as a snapshot mismatch.
 */
class ModerationHistoryListGoldenTest {

    private static final UUID TARGET = UUID.randomUUID();

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private FakeHistory history;

    private final Path dataFolder = Path.of("nonexistent");

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Staff");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        history = new FakeHistory();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSamePerActionIconGridAndNavAsTheOldView() {
        history.add(entry(SanctionAction.BAN));
        history.add(entry(SanctionAction.MUTE));

        Map<Integer, Snapshot> baseline = oldViewBaseline();
        Map<Integer, Snapshot> engine = snapshotEngine();

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
    }

    @Test
    void clickingAHistoryRowThroughTheEngineIsInert() {
        history.add(entry(SanctionAction.BAN));
        openEngine();
        Inventory before = player.getOpenInventory().getTopInventory();

        fireClick(0); // the first content cell holds the BAN row

        // An audit row is immutable: the window stays this menu's, still open over the same inventory.
        assertThat(player.getOpenInventory().getTopInventory()).isSameAs(before);
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code PlayerHistoryView} produced for this fixture (a BAN
     * row then a MUTE row), captured while both paths rendered it identically and frozen here: a BARRIER and a BOOK
     * icon (content slots 0 and 1. The action surfaces through the {@code mod_history_action} token) and the two nav
     * ARROWs (slots 48 and 50).
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.BARRIER, "BAN"));
        baseline.put(1, new Snapshot(Material.BOOK, "MUTE"));
        baseline.put(48, new Snapshot(Material.ARROW, "moderation.gui.history.prev"));
        baseline.put(50, new Snapshot(Material.ARROW, "moderation.gui.history.next"));
        return baseline;
    }

    private Map<Integer, Snapshot> snapshotEngine() {
        openEngine();
        return snapshot(player.getOpenInventory().getTopInventory());
    }

    private void openEngine() {
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());

        ModerationHistoryMenu menu = new ModerationHistoryMenu(menus, scheduler, history);
        menu.register(bindings, dataFolder, NOOP);
        menu.open(viewer, TARGET);
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private SanctionHistoryEntry entry(SanctionAction action) {
        return new SanctionHistoryEntry(
                action,
                TARGET,
                Issuer.console("Console"),
                Optional.of("reason"),
                Instant.EPOCH,
                Optional.empty(),
                Optional.empty());
    }

    /** The slot -> (material, plain name) map for every non-empty, non-filler slot of {@code inv}. */
    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.BLACK_STAINED_GLASS_PANE) {
                continue;
            }
            out.put(slot, new Snapshot(item.getType(), plainName(item)));
        }
        return out;
    }

    private static String plainName(ItemStack item) {
        // The title reads off the tile wherever the canon puts it: the display name of a bare button, or the
        // first lore line of a titled tile, whose display name is deliberately blank.
        return TileText.title(item);
    }

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    private record Snapshot(Material material, String name) {}

    /** A map-backed history store; the menu only reads {@code recentForTarget}. */
    private static final class FakeHistory implements SanctionHistory {
        private final List<SanctionHistoryEntry> rows = new ArrayList<>();

        void add(SanctionHistoryEntry entry) {
            rows.add(entry);
        }

        @Override
        public void append(SanctionHistoryEntry entry) {
            rows.add(entry);
        }

        @Override
        public List<SanctionHistoryEntry> banHistory(UUID target, int limit) {
            return List.copyOf(rows);
        }

        @Override
        public List<SanctionHistoryEntry> muteHistory(UUID target, int limit) {
            return List.copyOf(rows);
        }

        @Override
        public List<SanctionHistoryEntry> recentForTarget(UUID target, int limit) {
            return List.copyOf(rows);
        }

        @Override
        public List<SanctionHistoryEntry> recentByActor(UUID actor, int limit) {
            return List.copyOf(rows);
        }

        @Override
        public List<SanctionHistoryEntry> allSince(Instant threshold, int limit) {
            return List.copyOf(rows);
        }
    }

    /**
     * Surfaces the entry name's {@code mod_history_action} token so a row's action appears; else the bare key. The
     * engine resolves a {@code @key} line through a lambda {@link MessageKey} carrying only the key string, so the
     * match is by {@link MessageKey#key()} rather than enum identity.
     */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals(ModerationMessageKey.MOD_GUI_HISTORY_ENTRY_NAME.key())) {
                return placeholders.getOrDefault("mod_history_action", "");
            }
            return key.key();
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
