package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.OwnedWarp;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpEditorSubLayouts;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpEditorView;
import com.uxplima.uxmessentials.playerwarps.application.ArchivePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarpVisibility;
import com.uxplima.uxmessentials.playerwarps.application.WarpAuthorization;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.playerwarps.support.InMemoryPlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.support.NoWarpMembers;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ConfirmRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The player-warp editor golden test, the pilot of the entity-editor engine shim (increment 9). The migrated
 * {@link PlayerWarpEditorView} now opens through {@link Menus#openEditor}, so this asserts the engine-rendered editor
 * draws the exact property grid the bespoke {@code EntityEditorView}/uxmLib path drew: the same icon and the same
 * plain name at every property slot, the back button, and the confirm-gated delete button, slot for slot against a
 * baseline frozen from the retired view's layout (the property order and per-property icons/label keys
 * {@code PlayerWarpEditorView} builds, plus the layout's back/delete placement). Each property button also carries a
 * value-lore line wrapping its current value through the {@code value-lore} catalog key. The lock and password
 * controls were dropped with the surrogate-id rebuild (they return as the P4 access gate), so the grid is ten
 * properties, not the retired twelve.
 *
 * <p>Two real clicks through the engine's own {@link MenuListener} then prove the child pickers and the delete gate
 * are engine-native on this path: clicking the visibility (enum) slot opens an engine selector child (a selector
 * {@link MenuHolder}, not a uxmLib {@code SimpleGui}), and clicking the delete button opens an engine confirm child (a
 * confirm {@link MenuHolder}) whose yes button runs the same {@link ArchivePlayerWarp} use case the {@code /pwarp del}
 * command drives, recorded here through the repository.
 */
class PlayerWarpEditorGoldenTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);
    private static final Material FILLER = Material.BLACK_STAINED_GLASS_PANE;

    // The editor's property slots, in the order PlayerWarpEditorView builds its properties.
    private static final List<Integer> EDITOR_SLOTS = List.of(10, 11, 12, 13, 14, 15, 19, 20, 21, 22);
    private static final int VISIBILITY_SLOT = EDITOR_SLOTS.get(3);
    private static final int BACK_SLOT = 49;
    private static final int DELETE_SLOT = 53;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Messages messages;
    private Scheduler scheduler;
    private InMemoryPlayerWarpRepository repository;
    private PlayerWarpEditorView editorView;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        messages = new KeyMessages();
        guiText = new GuiText(messages);
        scheduler = new SyncScheduler();
        repository = new InMemoryPlayerWarpRepository();

        Notifier notifier = new Notifier(messages, new SilentSink());
        SetPlayerWarpVisibility visibility =
                new SetPlayerWarpVisibility(repository, notifier, java.time.Clock.systemUTC());
        ArchivePlayerWarp archivePlayerWarp = new ArchivePlayerWarp(
                repository,
                new WarpAuthorization(new NoWarpMembers()),
                notifier,
                event -> {},
                java.time.Clock.systemUTC());
        TextInput textInput =
                TextInputTestKit.create(plugin, guiText, scheduler, java.nio.file.Path.of("nonexistent"), NOOP);
        EntityEditorLayout layout = new EntityEditorLayout(
                6,
                EDITOR_SLOTS,
                BACK_SLOT,
                java.util.OptionalInt.of(DELETE_SLOT),
                Material.ARROW,
                Material.BARRIER,
                FILLER);
        editorView = new PlayerWarpEditorView(
                engine(),
                guiText,
                scheduler,
                repository,
                visibility,
                archivePlayerWarp,
                textInput,
                messages,
                layout,
                PlayerWarpEditorSubLayouts.codeDefault(),
                (p, v) -> {});
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSamePropertyGridAsTheRetiredView() {
        store("alpha");
        editorView.open(player, viewer, owned("alpha"));

        Map<Integer, Snapshot> baseline = retiredViewBaseline();
        Map<Integer, Snapshot> engine = snapshot(player.getOpenInventory().getTopInventory());

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
    }

    @Test
    void everyPropertyButtonCarriesAValueLoreLine() {
        store("alpha");
        editorView.open(player, viewer, owned("alpha"));

        Inventory inv = player.getOpenInventory().getTopInventory();
        for (int slot : EDITOR_SLOTS) {
            ItemStack item = inv.getItem(slot);
            assertThat(item).as("property at slot %s", slot).isNotNull();
            assertThat(valueLoreOf(item)).as("value-lore at slot %s", slot).startsWith("value=");
        }
    }

    @Test
    void clickingTheVisibilitySlotOpensAnEngineSelectorChild() {
        store("alpha");
        editorView.open(player, viewer, owned("alpha"));

        fireClick(VISIBILITY_SLOT, ClickType.LEFT);

        Inventory child = player.getOpenInventory().getTopInventory();
        assertThat(child.getHolder()).isInstanceOf(MenuHolder.class);
        // The child is an engine selector, not a uxmLib SimpleGui: its holder carries a selector state, and it draws
        // the pwarp selector's PAPER option buttons rather than the editor's property buttons.
        assertThat(((MenuHolder) child.getHolder()).selector()).isPresent();
        assertThat(hasAnyOption(child, Material.PAPER)).isTrue();
    }

    @Test
    void deleteButtonOpensTheEngineConfirmAndConfirmYesArchives() {
        store("alpha");
        editorView.open(player, viewer, owned("alpha"));

        fireClick(DELETE_SLOT, ClickType.LEFT); // opens the engine confirm window, changes nothing yet
        Inventory confirm = player.getOpenInventory().getTopInventory();
        assertThat(confirm.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) confirm.getHolder()).confirm()).isPresent(); // a confirm holder, not the editor
        assertThat(repository
                        .findByName(PlayerWarpName.of("alpha"))
                        .orElseThrow()
                        .status())
                .isEqualTo(WarpStatus.ACTIVE); // still active until confirmed

        fireClick(ConfirmRenderer.YES_SLOT, ClickType.LEFT); // confirm runs ArchivePlayerWarp (recoverable archive)

        // Delete archives by default: the row is retired to ARCHIVED, not dropped, so it can be restored.
        assertThat(repository
                        .findByName(PlayerWarpName.of("alpha"))
                        .orElseThrow()
                        .status())
                .isEqualTo(WarpStatus.ARCHIVED);
    }

    /**
     * The {@code (slot -> material, plain name)} grid the retired {@code EntityEditorView}/uxmLib path drew for this
     * fixture, frozen from {@code PlayerWarpEditorView}'s property order: the per-property icon (a warp with no custom
     * icon falls back to {@code ITEM_FRAME}) and the property's label catalog key (which {@code KeyMessages} echoes),
     * plus the back ({@code ARROW}) and delete ({@code BARRIER}) buttons at the layout's slots.
     */
    private Map<Integer, Snapshot> retiredViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(EDITOR_SLOTS.get(0), prop(Material.NAME_TAG, PlayerwarpsMessageKey.PWARP_GUI_PROP_NAME));
        baseline.put(EDITOR_SLOTS.get(1), prop(Material.COMPASS, PlayerwarpsMessageKey.PWARP_GUI_PROP_MOVE));
        baseline.put(EDITOR_SLOTS.get(2), prop(Material.ITEM_FRAME, PlayerwarpsMessageKey.PWARP_GUI_PROP_ICON));
        baseline.put(EDITOR_SLOTS.get(3), prop(Material.ENDER_EYE, PlayerwarpsMessageKey.PWARP_GUI_PROP_VISIBILITY));
        baseline.put(
                EDITOR_SLOTS.get(4), prop(Material.NOTE_BLOCK, PlayerwarpsMessageKey.PWARP_GUI_PROP_DEPARTURE_SOUND));
        baseline.put(EDITOR_SLOTS.get(5), prop(Material.JUKEBOX, PlayerwarpsMessageKey.PWARP_GUI_PROP_ARRIVAL_SOUND));
        baseline.put(
                EDITOR_SLOTS.get(6),
                prop(Material.BLAZE_POWDER, PlayerwarpsMessageKey.PWARP_GUI_PROP_DEPARTURE_PARTICLE));
        baseline.put(
                EDITOR_SLOTS.get(7),
                prop(Material.GLOWSTONE_DUST, PlayerwarpsMessageKey.PWARP_GUI_PROP_ARRIVAL_PARTICLE));
        baseline.put(EDITOR_SLOTS.get(8), prop(Material.CLOCK, PlayerwarpsMessageKey.PWARP_GUI_PROP_WARMUP));
        baseline.put(EDITOR_SLOTS.get(9), prop(Material.CLOCK, PlayerwarpsMessageKey.PWARP_GUI_PROP_COOLDOWN));
        baseline.put(BACK_SLOT, new Snapshot(Material.ARROW, PlayerwarpsMessageKey.PWARP_GUI_EDITOR_BACK.key()));
        baseline.put(DELETE_SLOT, new Snapshot(Material.BARRIER, PlayerwarpsMessageKey.PWARP_GUI_EDITOR_DELETE.key()));
        return baseline;
    }

    private static Snapshot prop(Material material, PlayerwarpsMessageKey label) {
        return new Snapshot(material, label.key());
    }

    // --- harness ---

    /**
     * One editor-capable engine plus its single listener, the engine path the production wiring uses: the façade is
     * built first so the listener can borrow its selector and confirm openers. What a property's click hook hands a
     * picker or a remove-confirm to open as an engine child window.
     */
    private Menus engine() {
        EditorRenderer editorRenderer = new EditorRenderer(guiText);
        MenuBindings bindings = new MenuBindings();
        MenuRenderer renderer =
                new MenuRenderer(new ItemRenderer(guiText, bindings.placeholders()), bindings.conditions());
        Menus menus = new Menus(renderer, scheduler, bindings.lists(), editorRenderer);
        MenuListener listener = new MenuListener(
                renderer,
                bindings.actions(),
                bindings.conditions(),
                scheduler,
                plugin,
                editorRenderer,
                menus.selectorOpener(),
                menus.confirmOpener());
        server.getPluginManager().registerEvents(listener, plugin);
        return menus;
    }

    private void store(String name) {
        repository.save(
                PlayerWarp.create(viewer, viewer.name(), PlayerWarpName.of(name), AT, Instant.ofEpochMilli(1_000)));
    }

    private OwnedWarp owned(String name) {
        return new OwnedWarp(
                viewer, repository.findByName(PlayerWarpName.of(name)).orElseThrow());
    }

    private static boolean hasAnyOption(Inventory inv, Material material) {
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() == material) {
                return true;
            }
        }
        return false;
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The slot -> (material, plain name) map for every populated, non-filler slot of {@code inv}. */
    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == FILLER) {
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

    private static String valueLoreOf(ItemStack item) {
        // Under the title line the canon puts at the top of a titled tile: the body is what the value-lore is.
        List<Component> lore = TileText.body(item);
        if (lore.isEmpty()) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(lore.get(0));
    }

    private record Snapshot(Material material, String name) {}

    // --- fakes ---

    private static final class SilentSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    /** Echoes the catalog key, except the value-lore line, which surfaces its {@code value} placeholder. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals("pwarp.gui.editor.value-lore")) {
                return "value=" + placeholders.getOrDefault("value", "");
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
