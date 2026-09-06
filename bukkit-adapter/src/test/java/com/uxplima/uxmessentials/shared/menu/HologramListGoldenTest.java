package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

import com.uxplima.uxmessentials.holograms.adapter.HologramServices;
import com.uxplima.uxmessentials.holograms.adapter.inbound.gui.HologramEditorSubLayouts;
import com.uxplima.uxmessentials.holograms.adapter.inbound.gui.HologramEditorView;
import com.uxplima.uxmessentials.holograms.adapter.inbound.gui.HologramListMenu;
import com.uxplima.uxmessentials.holograms.application.AddHologramAction;
import com.uxplima.uxmessentials.holograms.application.AddHologramLine;
import com.uxplima.uxmessentials.holograms.application.AddHologramPage;
import com.uxplima.uxmessentials.holograms.application.CenterHologram;
import com.uxplima.uxmessentials.holograms.application.ClearHologramActions;
import com.uxplima.uxmessentials.holograms.application.CopyHologram;
import com.uxplima.uxmessentials.holograms.application.CreateHologram;
import com.uxplima.uxmessentials.holograms.application.DeleteHologram;
import com.uxplima.uxmessentials.holograms.application.DescribeHologram;
import com.uxplima.uxmessentials.holograms.application.HologramsMessageKey;
import com.uxplima.uxmessentials.holograms.application.InsertHologramAction;
import com.uxplima.uxmessentials.holograms.application.InsertHologramLine;
import com.uxplima.uxmessentials.holograms.application.LinkHologramToNpc;
import com.uxplima.uxmessentials.holograms.application.ListHologramActions;
import com.uxplima.uxmessentials.holograms.application.ListHologramPages;
import com.uxplima.uxmessentials.holograms.application.ListHolograms;
import com.uxplima.uxmessentials.holograms.application.ManageHologramBlacklist;
import com.uxplima.uxmessentials.holograms.application.ManageHologramViewer;
import com.uxplima.uxmessentials.holograms.application.MoveHologram;
import com.uxplima.uxmessentials.holograms.application.MoveHologramAction;
import com.uxplima.uxmessentials.holograms.application.NearbyHolograms;
import com.uxplima.uxmessentials.holograms.application.RemoveHologramAction;
import com.uxplima.uxmessentials.holograms.application.RemoveHologramLine;
import com.uxplima.uxmessentials.holograms.application.RemoveHologramPage;
import com.uxplima.uxmessentials.holograms.application.RotateHologram;
import com.uxplima.uxmessentials.holograms.application.SetHologramAction;
import com.uxplima.uxmessentials.holograms.application.SetHologramAppearance;
import com.uxplima.uxmessentials.holograms.application.SetHologramClickCommand;
import com.uxplima.uxmessentials.holograms.application.SetHologramGrowUp;
import com.uxplima.uxmessentials.holograms.application.SetHologramLeaderboard;
import com.uxplima.uxmessentials.holograms.application.SetHologramLine;
import com.uxplima.uxmessentials.holograms.application.SetHologramModel;
import com.uxplima.uxmessentials.holograms.application.SetHologramRefresh;
import com.uxplima.uxmessentials.holograms.application.SetHologramVisibility;
import com.uxplima.uxmessentials.holograms.application.TeleportToHologram;
import com.uxplima.uxmessentials.holograms.application.UnlinkHologramFromNpc;
import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.application.port.LinkedNpcLocator;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
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
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour.ColourPickerLayout;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DirectTeleporter;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmlib.gui.Guis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The holograms golden test: the engine-rendered {@code /hologram} list must draw the exact grid the original
 * {@code HologramListView} drew. The store holds two holograms ("alpha", "beta"), so the list draws two ARMOR_STAND
 * icons (content slots 0 and 1), the LIME_DYE create button (slot 49), and the two ARROW nav buttons (slots 48 and
 * 50). The engine's window is snapshotted as {@code (slot -> material, plain name)} and asserted equal, slot for
 * slot, to the baseline the old view produced. Captured once while both rendered the same fixture, then frozen here
 * as the contract so the old class could be deleted. Then a left click on the first hologram icon through the
 * engine's own {@link MenuListener} proves the migrated path opens that hologram's bespoke {@link HologramEditorView}
 *, so the move is faithful in both appearance and behaviour.
 *
 * <p>The {@code KeyMessages} catalog surfaces the entry name's {@code hologram_name} token, so a hologram's name
 * appears in the rendered label; every other key renders verbatim. A real rendering difference (a wrong key, a wrong
 * material, a misplaced cell, a lost name token) therefore still shows up as a snapshot mismatch.
 */
class HologramListGoldenTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private FakeRepository repository;
    private HologramServices services;
    private TextInput textInput;

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
        repository = new FakeRepository();
        services = assembleServices();
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
        create("alpha");
        create("beta");
        Map<Integer, Snapshot> baseline = oldViewBaseline();

        Map<Integer, Snapshot> engine = snapshotEngine();

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
    }

    @Test
    void clickingAHologramThroughTheEngineOpensThatHologramsEditor() {
        create("alpha");
        openEngine();
        // Content slot 0 is the first hologram icon, "alpha"; clicking it must open that hologram's editor.
        fireClick(0);

        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
        // The editor draws the name-tag property button at its first property slot (code-default slot 10).
        assertThat(player.getOpenInventory().getTopInventory().getItem(10)).isNotNull();
        assertThat(player.getOpenInventory().getTopInventory().getItem(10).getType())
                .isEqualTo(Material.NAME_TAG);
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code HologramListView} produced for this fixture (two
     * holograms "alpha" and "beta", no create disabled), captured once while both paths rendered it identically and
     * frozen here as the contract: two ARMOR_STAND icons (content slots 0 and 1. The names surface through the
     * {@code hologram_name} token), the LIME_DYE create button (slot 49), and the two nav ARROWs (slots 48 and 50).
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.ARMOR_STAND, "alpha"));
        baseline.put(1, new Snapshot(Material.ARMOR_STAND, "beta"));
        baseline.put(48, new Snapshot(Material.ARROW, "hologram.gui.list.prev"));
        baseline.put(49, new Snapshot(Material.LIME_DYE, "hologram.gui.list.create"));
        baseline.put(50, new Snapshot(Material.ARROW, "hologram.gui.list.next"));
        return baseline;
    }

    /** Render the engine menu for the player and snapshot its populated, non-filler slots. */
    private Map<Integer, Snapshot> snapshotEngine() {
        openEngine();
        Inventory inv = player.getOpenInventory().getTopInventory();
        return snapshot(inv);
    }

    /** Build the engine, register the hologram bindings + spec, and open the list for the player. */
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

        listMenu(menus).register(bindings, dataFolder, NOOP);
        menus.open(viewer, HologramListMenu.SPEC_ID, null);
    }

    /** A {@link HologramListMenu} wired off the same collaborators as the old view, over the engine façade. */
    private HologramListMenu listMenu(Menus menus) {
        EntityEditorLayout editorLayout = new EntityEditorLayout(
                6,
                List.of(
                        10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39,
                        40, 41, 42, 43, 46, 47, 48),
                49,
                java.util.OptionalInt.of(53),
                Material.ARROW,
                Material.BARRIER,
                Material.BLACK_STAINED_GLASS_PANE);
        HologramEditorView editor = new HologramEditorView(
                menus,
                guiText,
                scheduler,
                repository,
                services,
                textInput,
                new FakeLookup(),
                new KeyMessages(),
                editorLayout,
                HologramEditorSubLayouts.codeDefault(),
                ColourPickerLayout.codeDefault(),
                (p, v) -> {});
        return new HologramListMenu(menus, scheduler, repository, services, textInput, editor);
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

    private void create(String name) {
        services.create().create(viewer, HologramName.of(name), AT, HologramLine.of(name));
    }

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    private record Snapshot(Material material, String name) {}

    private HologramServices assembleServices() {
        Notifier notifier = new Notifier(new KeyMessages(), new SilentSink());
        HologramView view = new SilentView();
        DomainEventPublisher events = new SilentEvents();
        DirectTeleporter teleporter = (who, destination) -> {};
        LinkedNpcLocator npc = name -> Optional.of(AT);
        return new HologramServices(
                new CreateHologram(repository, view, notifier, events, Clock.systemUTC()),
                new DeleteHologram(repository, view, notifier, events),
                new ListHolograms(repository, notifier),
                new AddHologramLine(repository, view, notifier),
                new SetHologramLine(repository, view, notifier),
                new InsertHologramLine(repository, view, notifier),
                new RemoveHologramLine(repository, view, notifier),
                new MoveHologram(repository, view, notifier),
                new CenterHologram(repository, view, notifier),
                new TeleportToHologram(repository, teleporter, notifier),
                new CopyHologram(repository, view, notifier),
                new RotateHologram(repository, view, notifier),
                new DescribeHologram(repository, notifier),
                new NearbyHolograms(repository, notifier),
                new SetHologramAppearance(repository, view, notifier),
                new SetHologramRefresh(repository, view, notifier),
                new SetHologramVisibility(repository, view, notifier),
                new ManageHologramViewer(repository, view, notifier),
                new SetHologramModel(repository, view, notifier),
                new SetHologramClickCommand(repository, view, notifier),
                new SetHologramLeaderboard(repository, view, notifier),
                new LinkHologramToNpc(repository, view, notifier, npc),
                new UnlinkHologramFromNpc(repository, view, notifier),
                new AddHologramPage(repository, view, notifier),
                new RemoveHologramPage(repository, view, notifier),
                new ListHologramPages(repository, notifier),
                new SetHologramGrowUp(repository, view, notifier),
                new ManageHologramBlacklist(repository, view, notifier),
                new AddHologramAction(repository, notifier),
                new InsertHologramAction(repository, notifier),
                new SetHologramAction(repository, notifier),
                new MoveHologramAction(repository, notifier),
                new RemoveHologramAction(repository, notifier),
                new ClearHologramActions(repository, notifier),
                new ListHologramActions(repository, notifier));
    }

    // --- fakes ---

    private static final class FakeRepository implements HologramRepository {
        private final Map<String, Hologram> byName = new LinkedHashMap<>();
        private final Map<String, Set<UUID>> manual = new LinkedHashMap<>();
        private final Map<String, Set<UUID>> black = new LinkedHashMap<>();

        @Override
        public Optional<Hologram> find(HologramName name) {
            return Optional.ofNullable(byName.get(name.value()));
        }

        @Override
        public List<Hologram> all() {
            return List.copyOf(byName.values());
        }

        @Override
        public boolean exists(HologramName name) {
            return byName.containsKey(name.value());
        }

        @Override
        public void save(Hologram hologram) {
            byName.put(hologram.name().value(), hologram);
        }

        @Override
        public void delete(HologramName name) {
            byName.remove(name.value());
            manual.remove(name.value());
            black.remove(name.value());
        }

        @Override
        public Set<UUID> manualViewers(HologramName name) {
            return new java.util.LinkedHashSet<>(manual.getOrDefault(name.value(), Set.of()));
        }

        @Override
        public void showTo(HologramName name, UUID viewer) {
            manual.computeIfAbsent(name.value(), k -> new java.util.LinkedHashSet<>())
                    .add(viewer);
        }

        @Override
        public void hideFrom(HologramName name, UUID viewer) {
            Set<UUID> v = manual.get(name.value());
            if (v != null) {
                v.remove(viewer);
            }
        }

        @Override
        public Set<UUID> blacklisted(HologramName name) {
            return new java.util.LinkedHashSet<>(black.getOrDefault(name.value(), Set.of()));
        }

        @Override
        public void addToBlacklist(HologramName name, UUID viewer) {
            black.computeIfAbsent(name.value(), k -> new java.util.LinkedHashSet<>())
                    .add(viewer);
        }

        @Override
        public void removeFromBlacklist(HologramName name, UUID viewer) {
            Set<UUID> v = black.get(name.value());
            if (v != null) {
                v.remove(viewer);
            }
        }
    }

    private static final class SilentView implements HologramView {
        @Override
        public void render(Hologram hologram) {}

        @Override
        public void despawn(HologramName name) {}

        @Override
        public void applyManualViewer(HologramName name, UUID viewer, boolean visible) {}
    }

    private static final class SilentEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final class SilentSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class FakeLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.of(new PlayerRef(
                    UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.of(new PlayerRef(uuid, "player"));
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return true;
        }
    }

    /**
     * Surfaces the entry name's {@code hologram_name} token so a hologram's name appears; else the bare key. The
     * engine resolves a {@code @key} line through a lambda {@link MessageKey} carrying only the key string, so the
     * match is by {@link MessageKey#key()} rather than enum identity.
     */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals(HologramsMessageKey.HOLOGRAM_GUI_LIST_ENTRY_NAME.key())) {
                return placeholders.getOrDefault("hologram_name", "");
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
