package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.itemworld.adapter.inbound.gui.EntityCountMenu;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.gui.EntityCountMenu.Tally;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.Guis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The itemworld {@code /entitycount} golden test: the engine-rendered tally grid must draw the exact window the
 * original {@code EntityCountListView} drew. The fixture is a two-type tally taken at radius 64: seven zombies
 * and three cows, already sorted, so the window draws a zombie spawn egg at content slot 0, a cow spawn egg at
 * slot 1, the rest of the content grid empty, the glass backdrop on the bottom row, and the two nav arrows at
 * slots 48 and 50. The engine's window is snapshotted as {@code (slot -> material, plain name)} and asserted
 * equal, slot for slot, to the baseline the old view produced. Captured once while both rendered the same
 * fixture, then frozen here as the contract so the old class could be deleted. A second case proves the empty
 * tally opens the one-row empty-state title. The grid is read-only, so there is no click effect to assert.
 */
class EntityCountGoldenTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private Messages messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        player = server.addPlayer("Counter");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        messages = new KeyMessages();
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameTallyGridAndNavAsTheOldView() {
        EntityCountMenu menu = engine();
        menu.open(
                viewer, List.of(new Tally("zombie", 7, "ZOMBIE_SPAWN_EGG"), new Tally("cow", 3, "COW_SPAWN_EGG")), 64);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getSize()).isEqualTo(54);
        assertThat(snapshot(inv)).isEqualTo(baseline());
    }

    @Test
    void anEmptyTallyOpensTheOneRowEmptyStateTitle() {
        EntityCountMenu menu = engine();
        menu.open(viewer, List.of(), 64);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getSize()).isEqualTo(9);
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code EntityCountListView} produced for this fixture
     * (zombie x7, cow x3, radius 64), captured once while both paths rendered it identically and frozen here as the
     * contract: the zombie egg at content slot 0 and the cow egg at slot 1 (both carrying the entry-name key), the
     * glass backdrop on the bottom row (empty name) save for the two ARROW nav buttons at slots 48 and 50 (their
     * prev/next keys). The plain names are the bare catalog keys because the test's {@code KeyMessages} returns each
     * key verbatim, so a real rendering difference still shows up as a mismatch.
     */
    private static Map<Integer, Snapshot> baseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        String entryName = "itemworld.entitycount.gui.entry-name";
        baseline.put(0, new Snapshot(Material.ZOMBIE_SPAWN_EGG, entryName));
        baseline.put(1, new Snapshot(Material.COW_SPAWN_EGG, entryName));
        for (int slot = 45; slot < 54; slot++) {
            baseline.put(slot, new Snapshot(Material.BLACK_STAINED_GLASS_PANE, ""));
        }
        baseline.put(48, new Snapshot(Material.ARROW, "itemworld.entitycount.gui.prev"));
        baseline.put(50, new Snapshot(Material.ARROW, "itemworld.entitycount.gui.next"));
        return baseline;
    }

    /** Wire the engine over the same collaborators the old view used and register the tally menu's bindings + specs. */
    private EntityCountMenu engine() {
        GuiText guiText = new GuiText(messages);
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Scheduler scheduler = new SyncScheduler();
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        EntityCountMenu menu = new EntityCountMenu(menus);
        menu.register(bindings, specDir(), new NoopLogger());
        return menu;
    }

    /** The bundled spec directory under the source tree, so the test loads the shipped tally + empty specs. */
    private static Path specDir() {
        Path repoRoot = Path.of("").toAbsolutePath();
        while (repoRoot != null && !java.nio.file.Files.exists(repoRoot.resolve("settings.gradle.kts"))) {
            repoRoot = repoRoot.getParent();
        }
        Objects.requireNonNull(repoRoot, "repo root");
        return repoRoot.resolve("bukkit-adapter/src/main/resources");
    }

    /** The slot -> (material, plain name) map for every non-empty slot of {@code inv}. */
    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null) {
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

    /** Resolves a key to its bare key string, ignoring placeholders, so a wrong key still mismatches. */
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
}
