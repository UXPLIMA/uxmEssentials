package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
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

import com.uxplima.uxmessentials.npc.adapter.NpcServices;
import com.uxplima.uxmessentials.npc.adapter.inbound.command.NpcSkinByName;
import com.uxplima.uxmessentials.npc.adapter.inbound.gui.NpcEditorSubLayouts;
import com.uxplima.uxmessentials.npc.adapter.inbound.gui.NpcEditorView;
import com.uxplima.uxmessentials.npc.adapter.inbound.gui.NpcListMenu;
import com.uxplima.uxmessentials.npc.application.AddNpcAction;
import com.uxplima.uxmessentials.npc.application.CenterNpc;
import com.uxplima.uxmessentials.npc.application.ClearNpcActions;
import com.uxplima.uxmessentials.npc.application.CopyNpc;
import com.uxplima.uxmessentials.npc.application.CreateNpc;
import com.uxplima.uxmessentials.npc.application.DeleteNpc;
import com.uxplima.uxmessentials.npc.application.DescribeNpc;
import com.uxplima.uxmessentials.npc.application.FixNpc;
import com.uxplima.uxmessentials.npc.application.InsertNpcAction;
import com.uxplima.uxmessentials.npc.application.ListNpcActions;
import com.uxplima.uxmessentials.npc.application.ListNpcEquipment;
import com.uxplima.uxmessentials.npc.application.ListNpcTypeData;
import com.uxplima.uxmessentials.npc.application.ListNpcs;
import com.uxplima.uxmessentials.npc.application.MoveNpc;
import com.uxplima.uxmessentials.npc.application.MoveNpcAction;
import com.uxplima.uxmessentials.npc.application.MoveNpcTo;
import com.uxplima.uxmessentials.npc.application.NearbyNpcs;
import com.uxplima.uxmessentials.npc.application.NpcMessageKey;
import com.uxplima.uxmessentials.npc.application.NpcQuota;
import com.uxplima.uxmessentials.npc.application.RemoveNpcAction;
import com.uxplima.uxmessentials.npc.application.SetNpcAction;
import com.uxplima.uxmessentials.npc.application.SetNpcClickCommand;
import com.uxplima.uxmessentials.npc.application.SetNpcCollidable;
import com.uxplima.uxmessentials.npc.application.SetNpcDisplayName;
import com.uxplima.uxmessentials.npc.application.SetNpcEntityType;
import com.uxplima.uxmessentials.npc.application.SetNpcEquipment;
import com.uxplima.uxmessentials.npc.application.SetNpcGlowing;
import com.uxplima.uxmessentials.npc.application.SetNpcInteractionCooldown;
import com.uxplima.uxmessentials.npc.application.SetNpcLookAtPlayer;
import com.uxplima.uxmessentials.npc.application.SetNpcMirrorSkin;
import com.uxplima.uxmessentials.npc.application.SetNpcPose;
import com.uxplima.uxmessentials.npc.application.SetNpcRange;
import com.uxplima.uxmessentials.npc.application.SetNpcScale;
import com.uxplima.uxmessentials.npc.application.SetNpcShowInTab;
import com.uxplima.uxmessentials.npc.application.SetNpcSkin;
import com.uxplima.uxmessentials.npc.application.SetNpcSkinSlim;
import com.uxplima.uxmessentials.npc.application.SetNpcState;
import com.uxplima.uxmessentials.npc.application.SetNpcTypeData;
import com.uxplima.uxmessentials.npc.application.TeleportToNpc;
import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.NpcView;
import com.uxplima.uxmessentials.npc.application.port.SkinService;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
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
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
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
 * The npc golden test: the engine-rendered {@code /npc} list must draw the exact grid the original {@code
 * NpcListView} drew. The store holds two fake-player NPCs ("alpha", "beta"), so the list draws two PLAYER_HEAD
 * icons (content slots 0 and 1. The type icon a default fake-player NPC resolves to), the LIME_DYE create button
 * (slot 49), and the two ARROW nav buttons (slots 48 and 50). The engine's window is snapshotted as {@code (slot ->
 * material, plain name)} and asserted equal, slot for slot, to the baseline the old view produced, captured once
 * while both rendered the same fixture, then frozen here as the contract so the old class could be deleted. Then a
 * left click on the first NPC icon through the engine's own {@link MenuListener} proves the migrated path opens that
 * NPC's bespoke {@link NpcEditorView}, so the move is faithful in both appearance and behaviour.
 *
 * <p>The {@code KeyMessages} catalog surfaces the entry name's {@code npc_name} token, so an NPC's name appears in
 * the rendered label; every other key renders verbatim. A real rendering difference (a wrong key, a wrong material,
 * a misplaced cell, a lost name token) therefore still shows up as a snapshot mismatch.
 */
class NpcListGoldenTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);

    /** Editor property slots, in the order NpcEditorView builds them: slot 10 is the NAME_TAG name property. */
    private static final List<Integer> EDITOR_SLOTS =
            List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32);

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private FakeRepository repository;
    private NpcServices services;
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
    void clickingAnNpcThroughTheEngineOpensThatNpcsEditor() {
        create("alpha");
        openEngine();
        // Content slot 0 is the first NPC icon, "alpha"; clicking it must open that NPC's editor.
        fireClick(0);

        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
        // The editor draws the name-tag property button at its first property slot (code-default slot 10).
        assertThat(player.getOpenInventory().getTopInventory().getItem(10)).isNotNull();
        assertThat(player.getOpenInventory().getTopInventory().getItem(10).getType())
                .isEqualTo(Material.NAME_TAG);
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code NpcListView} produced for this fixture (two
     * fake-player NPCs "alpha" and "beta"), captured once while both paths rendered it identically and frozen here
     * as the contract: two PLAYER_HEAD icons (content slots 0 and 1. The names surface through the {@code npc_name}
     * token), the LIME_DYE create button (slot 49), and the two nav ARROWs (slots 48 and 50).
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.PLAYER_HEAD, "alpha"));
        baseline.put(1, new Snapshot(Material.PLAYER_HEAD, "beta"));
        baseline.put(48, new Snapshot(Material.ARROW, "npc.gui.list.prev"));
        baseline.put(49, new Snapshot(Material.LIME_DYE, "npc.gui.list.create"));
        baseline.put(50, new Snapshot(Material.ARROW, "npc.gui.list.next"));
        return baseline;
    }

    /** Render the engine menu for the player and snapshot its populated, non-filler slots. */
    private Map<Integer, Snapshot> snapshotEngine() {
        openEngine();
        Inventory inv = player.getOpenInventory().getTopInventory();
        return snapshot(inv);
    }

    /** Build the engine, register the npc bindings + spec, and open the list for the player. */
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
        menus.open(viewer, NpcListMenu.SPEC_ID, null);
    }

    /** A {@link NpcListMenu} wired off the same collaborators as the old view, over the engine façade. */
    private NpcListMenu listMenu(Menus menus) {
        EntityEditorLayout editorLayout = new EntityEditorLayout(
                6,
                EDITOR_SLOTS,
                49,
                java.util.OptionalInt.of(53),
                Material.ARROW,
                Material.BARRIER,
                Material.BLACK_STAINED_GLASS_PANE);
        NpcSkinByName skinByName =
                new NpcSkinByName(new NoSkinService(), services.skin(), repository, notifier(), scheduler);
        NpcEditorView editor = new NpcEditorView(
                menus,
                guiText,
                scheduler,
                repository,
                services,
                skinByName,
                textInput,
                new KeyMessages(),
                editorLayout,
                NpcEditorSubLayouts.codeDefault(),
                ColourPickerLayout.codeDefault(),
                (p, v) -> {});
        return new NpcListMenu(menus, scheduler, repository, services, textInput, editor);
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
        services.create().create(viewer, NpcName.of(name), AT, null);
    }

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    private record Snapshot(Material material, String name) {}

    private Notifier notifier() {
        return new Notifier(new KeyMessages(), new SilentSink());
    }

    private NpcServices assembleServices() {
        Notifier notifier = notifier();
        NpcView view = new SilentView();
        DomainEventPublisher events = new SilentEvents();
        NpcQuota quota = new NpcQuota(new UnlimitedPermissions(), -1);
        Clock clock = Clock.systemUTC();
        return new NpcServices(
                new CreateNpc(repository, view, notifier, events, clock, quota),
                new DeleteNpc(repository, view, notifier, events),
                new ListNpcs(repository, notifier),
                new NearbyNpcs(repository, notifier),
                new DescribeNpc(repository, notifier),
                new TeleportToNpc(repository, (who, destination) -> {}, notifier),
                new MoveNpc(repository, view, notifier, events),
                new CopyNpc(repository, view, notifier, events, clock),
                new CenterNpc(repository, view, notifier, events),
                new FixNpc(repository, view, notifier),
                new SetNpcSkin(repository, view, notifier),
                new SetNpcEntityType(repository, view, notifier),
                new SetNpcClickCommand(repository, notifier),
                new SetNpcLookAtPlayer(repository, view, notifier),
                new SetNpcEquipment(repository, view, notifier),
                new ListNpcEquipment(repository, notifier),
                new SetNpcGlowing(repository, view, notifier),
                new SetNpcPose(repository, view, notifier),
                new SetNpcScale(repository, view, notifier),
                new AddNpcAction(repository, notifier),
                new ListNpcActions(repository, notifier),
                new RemoveNpcAction(repository, notifier),
                new ClearNpcActions(repository, notifier),
                new InsertNpcAction(repository, notifier),
                new SetNpcAction(repository, notifier),
                new MoveNpcAction(repository, notifier),
                new SetNpcTypeData(repository, view, notifier),
                new ListNpcTypeData(repository, notifier),
                new MoveNpcTo(repository, view, notifier, events),
                new SetNpcDisplayName(repository, view, notifier),
                new SetNpcInteractionCooldown(repository, notifier),
                new SetNpcMirrorSkin(repository, view, notifier),
                new SetNpcCollidable(repository, view, notifier),
                new SetNpcShowInTab(repository, view, notifier),
                new SetNpcRange(repository, view, notifier),
                new SetNpcState(repository, view, notifier),
                new SetNpcSkinSlim(repository, view, notifier));
    }

    // --- fakes ---

    private static final class FakeRepository implements NpcRepository {
        private final Map<String, Npc> byName = new LinkedHashMap<>();

        @Override
        public Optional<Npc> find(NpcName name) {
            return Optional.ofNullable(byName.get(name.value()));
        }

        @Override
        public List<Npc> all() {
            return List.copyOf(byName.values());
        }

        @Override
        public boolean exists(NpcName name) {
            return byName.containsKey(name.value());
        }

        @Override
        public void save(Npc npc) {
            byName.put(npc.name().value(), npc);
        }

        @Override
        public void delete(NpcName name) {
            byName.remove(name.value());
        }
    }

    private static final class SilentView implements NpcView {
        @Override
        public void render(Npc npc) {}

        @Override
        public void despawn(NpcName name) {}
    }

    private static final class SilentEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final class SilentSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class NoSkinService implements SkinService {
        @Override
        public java.util.concurrent.CompletableFuture<Optional<NpcSkin>> fetchByName(String username) {
            return java.util.concurrent.CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public java.util.concurrent.CompletableFuture<Optional<NpcSkin>> fetchFromUrl(String imageUrl) {
            return java.util.concurrent.CompletableFuture.completedFuture(Optional.empty());
        }
    }

    private static final class UnlimitedPermissions implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return true;
        }

        @Override
        public Permissions.QuotaResult resolveQuota(
                PlayerRef who,
                Permissions.QuotaFamily family,
                @org.jspecify.annotations.Nullable WorldRef world,
                long configDefault) {
            return Permissions.QuotaResult.unlimited();
        }
    }

    /**
     * Surfaces the entry name's {@code npc_name} token so an NPC's name appears; else the bare key. The engine
     * resolves a {@code @key} line through a lambda {@link MessageKey} carrying only the key string, so the match is
     * by {@link MessageKey#key()} rather than enum identity.
     */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals(NpcMessageKey.NPC_GUI_LIST_ENTRY_NAME.key())) {
                return placeholders.getOrDefault("npc_name", "");
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
