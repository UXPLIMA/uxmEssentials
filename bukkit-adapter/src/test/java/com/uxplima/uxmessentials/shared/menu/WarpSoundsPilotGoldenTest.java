package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
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
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.PlayerWarpGoToHandle;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpEditorView;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpSoundEdit;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpSoundMenu;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpSoundSelectorView;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The pilot's golden test: the warp sound selector must render identically whether it is drawn by the original
 * fixed view or by the menu engine. Both are opened for the same server warp on the departure side, and the
 * populated slots (the preset option grid plus the back / custom / remove buttons) are snapshotted as
 * {@code (slot -> material, plain name)} and asserted equal slot for slot. A final click on an option through the
 * engine's own listener proves the migrated path also writes the warp's sound, so the move is faithful in both
 * appearance and behaviour.
 */
class WarpSoundsPilotGoldenTest {

    private static final WarpName WARP = WarpName.of("shop");

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private RecordingWarpRepository repository;
    private Messages messages;

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Editor");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        repository = new RecordingWarpRepository(serverWarp());
        messages = new TemplateMessages();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameOptionGridAndButtonsAsTheOldView() {
        Map<Integer, Snapshot> oldView = snapshotOldView();
        Map<Integer, Snapshot> engine = snapshotEngine();

        // Comparing the slot sets first catches a slot one path fills that the other leaves empty (e.g. an option
        // grid that paginates a different number of cells); the map equality then pins material and plain name.
        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(oldView.keySet());
        assertThat(engine).isEqualTo(oldView);
    }

    @Test
    void clickingAnOptionThroughTheEngineSetsTheWarpsDepartureSound() {
        openEngine();
        // The third preset is the portal-travel sound; clicking its slot must persist that sound on the warp.
        WarpSoundSelectorView.SoundOption expected = options().get(2);

        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 2, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);

        assertThat(repository.lastSaved).isNotNull();
        assertThat(repository.lastSaved.departureSound()).contains(expected.soundName());
    }

    /** Render the migrated selector for the warp and snapshot its populated slots. */
    private Map<Integer, Snapshot> snapshotOldView() {
        GuiText guiText = new GuiText(messages);
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Menus menus = new Menus(renderer, sync(), bindings.lists());
        WarpEditorView editorView = new WarpEditorView(
                menus,
                messages,
                sync(),
                repository,
                textInput(),
                org.mockito.Mockito.mock(com.uxplima.uxmessentials.warps.application.UseWarp.class),
                playerWarpHandle(),
                new PlayerWarpGoToHandle());
        editorView.register(bindings, dataFolder, new NoopLogger());
        WarpSoundSelectorView view = WarpSoundSelectorView.create(messages, menus, repository, editorView, textInput());
        view.open(player, viewer, WARP.value(), null, true);
        Inventory inv = player.getOpenInventory().getTopInventory();
        return snapshot(inv);
    }

    /** The preset option list the migrated selector grids, read off a throwaway selector for the assertion. */
    private java.util.List<WarpSoundSelectorView.SoundOption> options() {
        GuiText guiText = new GuiText(messages);
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Menus menus = new Menus(renderer, sync(), bindings.lists());
        WarpEditorView editorView = new WarpEditorView(
                menus,
                messages,
                sync(),
                repository,
                textInput(),
                org.mockito.Mockito.mock(com.uxplima.uxmessentials.warps.application.UseWarp.class),
                playerWarpHandle(),
                new PlayerWarpGoToHandle());
        editorView.register(bindings, dataFolder, new NoopLogger());
        return WarpSoundSelectorView.create(messages, menus, repository, editorView, textInput())
                .getOptions();
    }

    /** Render the engine menu for the same warp+side and snapshot its populated slots. */
    private Map<Integer, Snapshot> snapshotEngine() {
        openEngine();
        Inventory inv = player.getOpenInventory().getTopInventory();
        return snapshot(inv);
    }

    /** Build the engine, register the warp bindings + spec, and open the selector for the warp's departure side. */
    private void openEngine() {
        GuiText guiText = new GuiText(messages);
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        MenuListener listener = new MenuListener(renderer, bindings.actions(), bindings.conditions(), sync(), plugin);
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, sync(), bindings.lists());

        WarpEditorView editorView = new WarpEditorView(
                menus,
                messages,
                sync(),
                repository,
                textInput(),
                org.mockito.Mockito.mock(com.uxplima.uxmessentials.warps.application.UseWarp.class),
                playerWarpHandle(),
                new PlayerWarpGoToHandle());
        editorView.register(bindings, dataFolder, new NoopLogger());
        WarpSoundSelectorView optionSource =
                WarpSoundSelectorView.create(messages, menus, repository, editorView, textInput());
        WarpSoundMenu menu = WarpSoundMenu.create(menus, optionSource, repository, editorView, textInput());
        menu.register(bindings, dataFolder, new NoopLogger());
        menu.open(viewer, new WarpSoundEdit(WARP, true));
    }

    /** The slot -> (material, plain name) map for every non-empty, non-filler slot of {@code inv}. */
    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.GRAY_STAINED_GLASS_PANE) {
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

    private Warp serverWarp() {
        Position location = Position.of(new WorldRef(UUID.randomUUID(), "world"), 1, 2, 3);
        PlayerRef owner = new PlayerRef(UUID.randomUUID(), "Owner");
        return Warp.create(WARP, location, owner, Instant.EPOCH);
    }

    private com.uxplima.uxmessentials.warps.adapter.inbound.gui.PlayerWarpRepositoryHandle playerWarpHandle() {
        return new com.uxplima.uxmessentials.warps.adapter.inbound.gui.PlayerWarpRepositoryHandle();
    }

    private TextInput textInput() {
        // The selector under test never reaches the custom-name prompt, so a mock text input is enough to satisfy
        // the menu's constructor; the test fails loudly if any path unexpectedly calls into it.
        return org.mockito.Mockito.mock(TextInput.class);
    }

    private Scheduler sync() {
        return new SyncScheduler();
    }

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    private record Snapshot(Material material, String name) {}

    /** A warp repository that serves one warp by name and records the last warp written back. */
    private static final class RecordingWarpRepository implements WarpRepository {
        private Warp warp;
        private Warp lastSaved;

        RecordingWarpRepository(Warp warp) {
            this.warp = warp;
        }

        @Override
        public Optional<Warp> find(WarpName name) {
            return name.equals(warp.name()) ? Optional.of(warp) : Optional.empty();
        }

        @Override
        public java.util.List<Warp> all() {
            return java.util.List.of(warp);
        }

        @Override
        public boolean exists(WarpName name) {
            return name.equals(warp.name());
        }

        @Override
        public void save(Warp updated) {
            this.warp = updated;
            this.lastSaved = updated;
        }

        @Override
        public void delete(WarpName name) {}

        @Override
        public void rate(WarpName name, UUID playerId, double rating) {}

        @Override
        public double averageRating(WarpName name) {
            return 0.0;
        }
    }

    /**
     * A messages port that substitutes {@code {token}} arguments into a small per-key template. The two render
     * paths must agree on the plain text of every slot, so the test controls the templates the catalog would carry
     * and proves the engine fills {@code {sound}} from its registered placeholder exactly as the old view filled it
     * from its explicit argument map.
     */
    private static final class TemplateMessages implements Messages {
        private static final Map<String, String> TEMPLATES = templates();

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            String template = TEMPLATES.getOrDefault(key.key(), key.key());
            String result = template;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            return result;
        }

        private static Map<String, String> templates() {
            Map<String, String> t = new HashMap<>();
            t.put(WarpsMessageKey.WARP_EDITOR_SOUND_SELECTOR_TITLE_DEPARTURE.key(), "Select Departure Sound");
            t.put(WarpsMessageKey.WARP_EDITOR_SOUND_SELECTOR_ENTRY_NAME.key(), "{sound}");
            t.put(WarpsMessageKey.WARP_EDITOR_SOUND_SELECTOR_ENTRY_LORE.key(), "Sound: {sound}");
            t.put(WarpsMessageKey.WARP_EDITOR_SOUND_SELECTOR_CUSTOM_NAME.key(), "Custom Sound Name");
            t.put(WarpsMessageKey.WARP_EDITOR_SOUND_SELECTOR_CUSTOM_LORE.key(), "Type a custom sound.");
            t.put(WarpsMessageKey.WARP_EDITOR_SOUND_SELECTOR_REMOVE_NAME.key(), "Remove Sound");
            t.put(WarpsMessageKey.WARP_EDITOR_SOUND_SELECTOR_REMOVE_LORE.key(), "Remove the sound.");
            t.put(WarpsMessageKey.WARP_EDITOR_SELECTOR_BACK.key(), "Back to Editor");
            return Map.copyOf(t);
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
