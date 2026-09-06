package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
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

import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpEditorSubLayouts;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpEditorView;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpListMenu;
import com.uxplima.uxmessentials.playerwarps.application.ArchivePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.PlayerWarpQuota;
import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarpVisibility;
import com.uxplima.uxmessentials.playerwarps.application.WarpAuthorization;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.support.InMemoryPlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.support.NoWarpMembers;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
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
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmlib.gui.Guis;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The player-warps golden test: the engine-rendered {@code /pwarp} list must draw the exact grid the original
 * {@code PlayerWarpListView} drew. The store holds two warps the viewer owns ("alpha", "beta", neither with a
 * custom icon), so the list draws two ENDER_PEARL icons (content slots 0 and 1. The warp's fallback material),
 * the LIME_DYE create button (slot 49), and the two ARROW nav buttons (slots 48 and 50). The engine's window is
 * snapshotted as {@code (slot -> material, plain name)} and asserted equal, slot for slot, to the baseline the old
 * view produced. Captured once while both rendered the same fixture, then frozen here as the contract so the old
 * class could be deleted. Then a left click on the first warp icon through the engine's own {@link MenuListener}
 * proves the migrated path opens that warp's bespoke {@link PlayerWarpEditorView}, so the move is faithful in both
 * appearance and behaviour.
 *
 * <p>The {@code KeyMessages} catalog surfaces the entry name's {@code pwarp_name} token, so a warp's name appears in
 * the rendered label; every other key renders verbatim. A real rendering difference (a wrong key, a wrong material,
 * a misplaced cell, a lost name token) therefore still shows up as a snapshot mismatch. A separate test pins that an
 * operator holding the manage node sees every owner's warps while a plain player sees only their own, the scope the
 * subject carries from the open site, where the permission read legally runs.
 */
class PlayerWarpListGoldenTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private SyncScheduler scheduler;
    private InMemoryPlayerWarpRepository repository;
    private TextInput textInput;
    private boolean managePerm;

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        player = server.addPlayer("Owner");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        repository = new InMemoryPlayerWarpRepository();
        managePerm = false;
        textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP);
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameIconGridCreateAndNavAsTheOldView() {
        store(viewer, "alpha");
        store(viewer, "beta");
        Map<Integer, Snapshot> baseline = oldViewBaseline();

        Map<Integer, Snapshot> engine = snapshotEngine();

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
    }

    @Test
    void clickingAWarpThroughTheEngineOpensThatWarpsEditor() {
        store(viewer, "alpha");
        openEngine();
        // Content slot 0 is the first warp icon, "alpha"; clicking it must open that warp's editor.
        fireClick(0);

        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
        // The editor draws the name-tag property button at its first property slot (code-default slot 10).
        assertThat(player.getOpenInventory().getTopInventory().getItem(10)).isNotNull();
        assertThat(player.getOpenInventory().getTopInventory().getItem(10).getType())
                .isEqualTo(Material.NAME_TAG);
    }

    @Test
    void aPlayerWithoutTheManageNodeSeesOnlyTheirOwnWarps() {
        store(viewer, "mine");
        store(new PlayerRef(UUID.randomUUID(), "Bob"), "bobs");

        Map<Integer, Snapshot> engine = snapshotEngine();

        // Only the viewer's own warp is drawn; Bob's is unreachable from a non-operator list.
        assertThat(engine.get(0)).isEqualTo(new Snapshot(Material.ENDER_PEARL, "mine"));
        assertThat(engine).doesNotContainValue(new Snapshot(Material.ENDER_PEARL, "bobs"));
    }

    @Test
    void anOperatorWithTheManageNodeSeesEveryOwnersWarps() {
        managePerm = true;
        store(viewer, "mine");
        store(new PlayerRef(UUID.randomUUID(), "Bob"), "bobs");

        Map<Integer, Snapshot> engine = snapshotEngine();

        assertThat(engine.get(0)).isEqualTo(new Snapshot(Material.ENDER_PEARL, "mine"));
        assertThat(engine.get(1)).isEqualTo(new Snapshot(Material.ENDER_PEARL, "bobs"));
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code PlayerWarpListView} produced for this fixture (two
     * warps "alpha" and "beta", neither with a custom icon), captured once while both paths rendered it identically
     * and frozen here as the contract: two ENDER_PEARL icons (content slots 0 and 1, the fallback icon, with the
     * names surfacing through the {@code pwarp_name} token), the LIME_DYE create button (slot 49), and the two nav
     * ARROWs (slots 48 and 50).
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.ENDER_PEARL, "alpha"));
        baseline.put(1, new Snapshot(Material.ENDER_PEARL, "beta"));
        baseline.put(48, new Snapshot(Material.ARROW, "pwarp.gui.list.prev"));
        baseline.put(49, new Snapshot(Material.LIME_DYE, "pwarp.gui.list.create"));
        baseline.put(50, new Snapshot(Material.ARROW, "pwarp.gui.list.next"));
        return baseline;
    }

    /** Render the engine menu for the player and snapshot its populated, non-filler slots. */
    private Map<Integer, Snapshot> snapshotEngine() {
        openEngine();
        Inventory inv = player.getOpenInventory().getTopInventory();
        return snapshot(inv);
    }

    /** Build the engine, register the player-warp bindings + spec, and open the list for the player. */
    private void openEngine() {
        EditorRenderer editorRenderer = new EditorRenderer(guiText);
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
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

        PlayerWarpListMenu listMenu = listMenu(menus);
        listMenu.register(bindings, dataFolder, NOOP);
        listMenu.open(player, viewer);
    }

    /** A {@link PlayerWarpListMenu} wired off the same collaborators as the old view, over the engine façade. */
    private PlayerWarpListMenu listMenu(Menus menus) {
        Messages messages = new KeyMessages();
        Notifier notifier = new Notifier(messages, new SilentSink());
        Permissions permissions = new ManagePermissions(() -> managePerm);
        SetPlayerWarp setPlayerWarp = new SetPlayerWarp(
                repository,
                new PlayerWarpQuota(permissions, 3),
                notifier,
                event -> {},
                java.time.Clock.systemUTC(),
                List.of());
        SetPlayerWarpVisibility visibility =
                new SetPlayerWarpVisibility(repository, notifier, java.time.Clock.systemUTC());
        ArchivePlayerWarp archivePlayerWarp = new ArchivePlayerWarp(
                repository,
                new WarpAuthorization(new NoWarpMembers()),
                notifier,
                event -> {},
                java.time.Clock.systemUTC());
        EntityEditorLayout editorLayout = new EntityEditorLayout(
                6,
                List.of(10, 11, 12, 13, 14, 15, 19, 20, 21, 22),
                49,
                java.util.OptionalInt.of(53),
                Material.ARROW,
                Material.BARRIER,
                Material.BLACK_STAINED_GLASS_PANE);
        PlayerWarpEditorView editor = new PlayerWarpEditorView(
                menus,
                guiText,
                scheduler,
                repository,
                visibility,
                archivePlayerWarp,
                textInput,
                messages,
                editorLayout,
                PlayerWarpEditorSubLayouts.codeDefault(),
                (p, v) -> {});
        return new PlayerWarpListMenu(
                menus, scheduler, permissions, messages, repository, setPlayerWarp, textInput, editor);
    }

    /** Left-click the given content slot of the open menu through the production click path. */
    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
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

    private void store(PlayerRef owner, String name) {
        repository.save(
                PlayerWarp.create(owner, owner.name(), PlayerWarpName.of(name), AT, Instant.ofEpochMilli(1_000)));
    }

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    private record Snapshot(Material material, String name) {}

    // --- fakes ---

    private static final class ManagePermissions implements Permissions {
        private final java.util.function.BooleanSupplier grants;

        ManagePermissions(java.util.function.BooleanSupplier grants) {
            this.grants = grants;
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return grants.getAsBoolean();
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.unlimited();
        }
    }

    private static final class SilentSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    /**
     * Surfaces the entry name's {@code pwarp_name} token so a warp's name appears; else the bare key. The engine
     * resolves a {@code @key} line through a lambda {@link MessageKey} carrying only the key string, so the match is
     * by {@link MessageKey#key()} rather than enum identity.
     */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals(PlayerwarpsMessageKey.PWARP_GUI_LIST_ENTRY_NAME.key())) {
                return placeholders.getOrDefault("pwarp_name", "");
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

    private static final class SyncScheduler implements com.uxplima.uxmessentials.shared.application.port.Scheduler {
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
