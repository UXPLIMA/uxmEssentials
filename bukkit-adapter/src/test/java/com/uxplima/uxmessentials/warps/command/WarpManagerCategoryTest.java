package com.uxplima.uxmessentials.warps.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputInstaller;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpCategoryManagerMenu;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpCategoryParentSelectorMenu;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpCategorySettingsView;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage that the engine-rendered warp-category settings panel drives the real category use cases. The
 * panel renders through the menu engine and routes its clicks through the shared {@code MenuListener}, so this test
 * installs that listener and proves:
 *
 * <ul>
 *   <li>a display-name click opens the shared chat prompt and the typed line saves the renamed category;
 *   <li>a delete click removes the category.
 * </ul>
 *
 * The warp manager itself renders through the menu engine; its create-button and slot-for-slot appearance are covered
 * by {@code WarpManagerGoldenTest}, and the panel's full property grid by {@code WarpCategorySettingsGoldenTest}. The
 * {@code warp.category.display-name} input point is configured as chat here so the typed line round-trips through the
 * shared {@link TextInput} chat backend rather than the apply seam.
 */
class WarpManagerCategoryTest {

    private static final int NAME_SLOT = 10;
    private static final int DELETE_SLOT = 22;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private StubWarpCategoryRepository categories;
    private WarpCategorySettingsView categorySettingsView;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        player.setOp(true);

        Path inputDir = plugin.getDataFolder().toPath();
        Files.createDirectories(inputDir);
        Files.writeString(inputDir.resolve("text-input.conf"), """
                default-mode = anvil
                modes {
                  "warp.category.display-name" = chat
                }
                """);

        Messages messages = new KeyMessages();
        categories = new StubWarpCategoryRepository();
        Scheduler scheduler = new SyncScheduler();

        var anvil = new com.uxplima.uxmlib.gui.anvil.AnvilInput(plugin);
        anvil.install();
        TextInput textInput = TextInputInstaller.install(
                        plugin,
                        plugin.getDataFolder().toPath(),
                        anvil,
                        new GuiText(messages),
                        scheduler,
                        new SilentLogger())
                .textInput();

        TestMenuEngine engine = TestMenuEngine.create(messages, scheduler);
        engine.installListener(plugin);
        // The back button reopens the warp manager; these tests do not exercise it, so the seam is a no-op here.
        categorySettingsView =
                new WarpCategorySettingsView(engine.menus(), messages, textInput, categories, (p, v) -> {});
        WarpCategoryManagerMenu categoryManagerView =
                new WarpCategoryManagerMenu(engine.menus(), messages, scheduler, categories, textInput);
        WarpCategoryParentSelectorMenu parentSelectorView = new WarpCategoryParentSelectorMenu(
                engine.menus(), messages, scheduler, categories, categorySettingsView);
        parentSelectorView.register(engine.bindings(), plugin.getDataFolder().toPath(), new SilentLogger());
        categorySettingsView.bind(parentSelectorView);
        categoryManagerView.bind(categorySettingsView, (p, v) -> {});
        categorySettingsView.register(engine.bindings(), plugin.getDataFolder().toPath(), new SilentLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void categorySettingsEditsTheDisplayName() {
        categories.save(new WarpCategory("pvp", "pvp", Optional.empty(), List.of(), 0, Optional.empty()));
        categorySettingsView.open(player, ref(), categories.find("pvp").orElseThrow());

        fireClick(NAME_SLOT); // the display-name button
        fireChat("PvP Arenas");

        assertThat(categories.find("pvp").orElseThrow().displayName()).isEqualTo("PvP Arenas");
    }

    @Test
    void categorySettingsDeleteRemovesTheCategory() {
        categories.save(new WarpCategory("pvp", "pvp", Optional.empty(), List.of(), 0, Optional.empty()));
        categorySettingsView.open(player, ref(), categories.find("pvp").orElseThrow());

        fireClick(DELETE_SLOT); // the delete button

        assertThat(categories.find("pvp")).isEmpty();
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private void fireChat(String line) {
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.message()).thenReturn(Component.text(line));
        when(event.getHandlers()).thenReturn(AsyncChatEvent.getHandlerList());
        server.getPluginManager().callEvent(event);
    }

    private PlayerRef ref() {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    /** A category repository over a fixed, mutable map: the panel's saves and deletes land here. */
    private static final class StubWarpCategoryRepository
            implements com.uxplima.uxmessentials.warps.application.port.WarpCategoryRepository {
        private final Map<String, WarpCategory> byId = new LinkedHashMap<>();

        @Override
        public Optional<WarpCategory> find(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<WarpCategory> all() {
            return new ArrayList<>(byId.values());
        }

        @Override
        public void save(WarpCategory category) {
            byId.put(category.id(), category);
        }

        @Override
        public void delete(String id) {
            byId.remove(id);
        }
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
    }

    private static final class SilentLogger implements Logger {
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
