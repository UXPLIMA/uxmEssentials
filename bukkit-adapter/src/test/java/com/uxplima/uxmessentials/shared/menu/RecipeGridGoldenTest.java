package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.itemworld.adapter.inbound.gui.RecipeGridMenu;
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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The itemworld {@code /recipe} golden test: the engine-rendered crafting grid must draw the exact window the
 * original {@code RecipeGridView} drew. The fixture is a boat-like recipe. Oak planks at the top-left and
 * top-right grid cells, every other cell empty, the result an oak boat, so the window draws two plank
 * ingredients (slots 0 and 2), seven empty-slot panes in the rest of the 3x3 grid, the boat in the result slot
 * (15), and a glass backdrop everywhere else. The engine's window is snapshotted as {@code (slot -> material,
 * plain name)} and asserted equal, slot for slot, to the baseline the old view produced, captured once while
 * both rendered the same fixture, then frozen here as the contract so the old class could be deleted. A second
 * case proves the no-recipe path opens the one-row empty-state title.
 */
class RecipeGridGoldenTest {

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
        player = server.addPlayer("Crafter");
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
    void engineRendersTheSameGridAndResultAsTheOldView() {
        RecipeGridMenu menu = openEngineGrid();

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getSize()).isEqualTo(27);
        assertThat(snapshot(inv)).isEqualTo(gridBaseline());
        assertThat(menu).isNotNull();
    }

    @Test
    void anItemWithNoRecipeOpensTheOneRowEmptyStateTitle() {
        RecipeGridMenu menu = engine();
        menu.openEmpty(viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getSize()).isEqualTo(9);
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code RecipeGridView} produced for this fixture (planks
     * at grid cells 0 and 2, an oak-boat result), captured once while both paths rendered it identically and frozen
     * here as the contract: planks at slots 0 and 2 with no name, seven empty-slot panes carrying the empty-name
     * key, the boat at slot 15 with the result-name key, and the glass backdrop (empty name) everywhere else. The
     * plain names are the bare catalog keys because the test's {@code KeyMessages} returns each key verbatim, so a
     * real rendering difference (a wrong key, a wrong material, a misplaced cell) still shows up as a mismatch.
     */
    private static Map<Integer, Snapshot> gridBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        for (int slot = 0; slot < 27; slot++) {
            baseline.put(slot, new Snapshot(Material.BLACK_STAINED_GLASS_PANE, ""));
        }
        String emptyName = "itemworld.recipe.gui.empty-name";
        // The 3x3 grid cells at slots {0,1,2,9,10,11,18,19,20}: planks at 0 and 2, the rest empty-slot panes.
        baseline.put(0, new Snapshot(Material.OAK_PLANKS, ""));
        baseline.put(2, new Snapshot(Material.OAK_PLANKS, ""));
        baseline.put(1, new Snapshot(Material.BLACK_STAINED_GLASS_PANE, emptyName));
        baseline.put(9, new Snapshot(Material.BLACK_STAINED_GLASS_PANE, emptyName));
        baseline.put(10, new Snapshot(Material.BLACK_STAINED_GLASS_PANE, emptyName));
        baseline.put(11, new Snapshot(Material.BLACK_STAINED_GLASS_PANE, emptyName));
        baseline.put(18, new Snapshot(Material.BLACK_STAINED_GLASS_PANE, emptyName));
        baseline.put(19, new Snapshot(Material.BLACK_STAINED_GLASS_PANE, emptyName));
        baseline.put(20, new Snapshot(Material.BLACK_STAINED_GLASS_PANE, emptyName));
        baseline.put(15, new Snapshot(Material.OAK_BOAT, "itemworld.recipe.gui.result-name"));
        return baseline;
    }

    /** Build the engine, register the recipe bindings + spec, and open the grid for the fixture recipe. */
    private RecipeGridMenu openEngineGrid() {
        RecipeGridMenu menu = engine();
        // Planks across the top corners of the grid, the rest empty; the result an oak boat.
        List<@Nullable Material> grid =
                Arrays.asList(Material.OAK_PLANKS, null, Material.OAK_PLANKS, null, null, null, null, null, null);
        menu.open(viewer, grid, Material.OAK_BOAT);
        return menu;
    }

    /** Wire the engine over the same collaborators the old view used and register the recipe menu's bindings. */
    private RecipeGridMenu engine() {
        GuiText guiText = new GuiText(messages);
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Scheduler scheduler = new SyncScheduler();
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        RecipeGridMenu menu = new RecipeGridMenu(menus, messages, Material.BLACK_STAINED_GLASS_PANE);
        menu.register(bindings, specDir(), new NoopLogger());
        return menu;
    }

    /** The bundled spec directory under the source tree, so the test loads the shipped grid + empty specs. */
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
            org.bukkit.inventory.ItemStack item = inv.getItem(slot);
            if (item == null) {
                continue;
            }
            out.put(slot, new Snapshot(item.getType(), plainName(item)));
        }
        return out;
    }

    private static String plainName(org.bukkit.inventory.ItemStack item) {
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
