package com.uxplima.uxmessentials.warps.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpCategoryParentSelectorMenu;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpCategorySettingsView;
import com.uxplima.uxmessentials.warps.application.port.WarpCategoryRepository;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The warp-category settings GUI's "display material" button must wear the category's configured material, not a
 * fixed stand-in: an operator who set the category to {@code SAND} should see a sand button, and a category with no
 * material set falls back to the same {@code BOOK} the lore line names. Now rendered through the menu engine, the
 * button material is the {@code warp_cat_set_material} placeholder the renderer substitutes into the spec's material
 * slot.
 */
class WarpCategorySettingsMaterialTest {

    private static final int MATERIAL_SLOT = 12;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private TestMenuEngine engine;

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        engine = TestMenuEngine.create(new KeyMessages(), new SyncScheduler());
        engine.installListener(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void displayMaterialButtonWearsTheConfiguredMaterial() {
        ItemStack button = materialButtonFor(category(Optional.of("SAND")));

        assertThat(button.getType()).isEqualTo(Material.SAND);
    }

    @Test
    void unsetDisplayMaterialFallsBackToBook() {
        ItemStack button = materialButtonFor(category(Optional.empty()));

        assertThat(button.getType()).isEqualTo(Material.BOOK);
    }

    private ItemStack materialButtonFor(WarpCategory category) {
        WarpCategorySettingsView view = new WarpCategorySettingsView(
                engine.menus(),
                new KeyMessages(),
                org.mockito.Mockito.mock(TextInput.class),
                new FixedCategories(category),
                (p, v) -> {});
        view.bind(new WarpCategoryParentSelectorMenu(
                engine.menus(), new KeyMessages(), new SyncScheduler(), new FixedCategories(category), view));
        view.register(engine.bindings(), dataFolder, NOOP);
        view.open(player, viewer, category);
        ItemStack button = player.getOpenInventory().getTopInventory().getItem(MATERIAL_SLOT);
        assertThat(button).as("material button at slot %s", MATERIAL_SLOT).isNotNull();
        return button;
    }

    private static WarpCategory category(Optional<String> material) {
        return new WarpCategory("pvp", "PvP", material, List.of(), 0, Optional.empty());
    }

    /** A category repository over a single fixed category: the panel's subject, no Bukkit read. */
    private record FixedCategories(WarpCategory only) implements WarpCategoryRepository {
        @Override
        public Optional<WarpCategory> find(String id) {
            return only.id().equals(id) ? Optional.of(only) : Optional.empty();
        }

        @Override
        public List<WarpCategory> all() {
            return List.of(only);
        }

        @Override
        public void save(WarpCategory category) {}

        @Override
        public void delete(String id) {}
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
}
