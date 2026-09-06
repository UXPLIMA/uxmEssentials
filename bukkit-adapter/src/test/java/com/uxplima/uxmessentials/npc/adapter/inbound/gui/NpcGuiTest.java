package com.uxplima.uxmessentials.npc.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
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
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.npc.adapter.NpcServices;
import com.uxplima.uxmessentials.npc.adapter.inbound.command.NpcSkinByName;
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
import com.uxplima.uxmessentials.npc.domain.EquipmentSlot;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContexts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EnumProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.TextProperty;
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
 * MockBukkit coverage of the NPC property editor: a toggle property (glow) cycles and persists through the glow
 * use case; an enum property (type) cycles and persists through the type use case; a text property (name) routes a
 * validated anvil line to a rename; an equipment slot set persists through the equip use case; and the delete
 * button is gated behind a confirm menu. The editor is laid out from a temp conf (no hardcoded slots); the
 * scheduler runs every hop inline so the off-thread writes land synchronously. The list, now drawn through the
 * menu engine, is covered by {@code NpcListGoldenTest}.
 */
class NpcGuiTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);

    // Editor property slots, in the order NpcEditorView builds its properties.
    private static final List<Integer> EDITOR_SLOTS =
            List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32);
    private static final int NAME_SLOT = EDITOR_SLOTS.get(0);
    private static final int TYPE_SLOT = EDITOR_SLOTS.get(2);
    private static final int EQUIPMENT_SLOT = EDITOR_SLOTS.get(3);
    private static final int GLOW_SLOT = EDITOR_SLOTS.get(7);
    private static final int GLOW_COLOR_SLOT = EDITOR_SLOTS.get(8);
    private static final int TELEPORT_SLOT = EDITOR_SLOTS.get(18); // "Teleport here", the last property
    private static final int DELETE_SLOT = 53;
    private static final int CONFIRM_SLOT =
            11; // the engine confirm window's yes button slot (ConfirmRenderer.YES_SLOT)
    private static final int EQUIP_HEAD_SLOT = 11; // first armour slot in the equipment sub-menu (code default)

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private FakeRepository repository;
    private NpcServices services;
    private NpcEditorView editorView;
    private final List<Position> teleportDestinations = new java.util.ArrayList<>();

    @BeforeEach
    void setUp(@TempDir Path dir) throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        repository = new FakeRepository();
        services = assembleServices();
        Guis.install(plugin);

        EntityEditorLayout editorLayout = editorLayout(dir);
        TextInput textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP);
        NpcSkinByName skinByName =
                new NpcSkinByName(new NoSkinService(), services.skin(), repository, notifier(), scheduler);
        editorView = new NpcEditorView(
                editorEngine(),
                guiText,
                scheduler,
                repository,
                services,
                skinByName,
                textInput,
                new KeyMessages(),
                editorLayout,
                NpcEditorSubLayouts.codeDefault(),
                com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour.ColourPickerLayout.codeDefault(),
                (p, v) -> {});
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void togglePropertyCyclesAndPersistsThroughTheUseCase() {
        create("alpha");
        editorView.open(player, viewer, npc("alpha"));
        assertThat(npc("alpha").glowing()).isFalse();

        fireClick(GLOW_SLOT, ClickType.LEFT);

        assertThat(npc("alpha").glowing()).isTrue();
    }

    @Test
    void enumPropertyCyclesAndPersistsThroughTheUseCase() {
        create("alpha");
        Npc holo = npc("alpha");
        EditableProperty type = editorView.grid().propertyAt(TYPE_SLOT, holo).orElseThrow();
        assertThat(type).isInstanceOf(EnumProperty.class);
        assertThat(npc("alpha").entityType()).isEqualTo("PLAYER");

        // The enum selector writes through the type use case; opening it then clicking an option persists that
        // option's value. PLAYER is the selected option (drawn with a glint), so the first non-PLAYER option is a
        // real type change we can assert lands on the NPC.
        editorView.open(player, viewer, holo);
        fireClick(TYPE_SLOT, ClickType.LEFT); // opens the selector
        String chosen = clickFirstSelectorOptionOtherThan("PLAYER");

        assertThat(npc("alpha").entityType()).isEqualTo(chosen);
    }

    @Test
    void typeSelectorDrawsEachOptionAsItsSpawnEgg() {
        create("alpha");
        Npc holo = npc("alpha");
        editorView.open(player, viewer, holo);

        fireClick(TYPE_SLOT, ClickType.LEFT); // opens the type selector

        // The fake-player entry (the default, selected option) shows a player head; every other drawn option shows
        // the icon NpcTypeIcon.iconFor resolves for that type, its spawn egg, or the egg fallback. Assert against
        // every option actually drawn rather than one fixed type, so the test does not depend on the slot count.
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(selectorIcon(inv, "PLAYER")).isEqualTo(Material.PLAYER_HEAD);
        int mobOptionsChecked = 0;
        for (int slot = 0; slot < inv.getSize(); slot++) {
            org.bukkit.inventory.ItemStack item = inv.getItem(slot);
            if (item == null || item.getItemMeta() == null || item.getItemMeta().displayName() == null) {
                continue;
            }
            String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(item.getItemMeta().displayName());
            if (name.isBlank() || name.equals("PLAYER")) {
                continue;
            }
            assertThat(item.getType()).isEqualTo(NpcTypeIcon.iconFor(org.bukkit.entity.EntityType.valueOf(name)));
            mobOptionsChecked++;
        }
        assertThat(mobOptionsChecked).isGreaterThan(0);
    }

    @Test
    void glowColourUsesTheSharedGlassColourPickerNotAPaperEnum() {
        create("alpha");
        EditableProperty glowColour =
                editorView.grid().propertyAt(GLOW_COLOR_SLOT, npc("alpha")).orElseThrow();
        assertThat(glowColour)
                .isInstanceOf(
                        com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour.ColourProperty.class);
    }

    @Test
    void textPropertyRoutesAnvilInputToRename() {
        create("alpha");
        EditableProperty name =
                editorView.grid().propertyAt(NAME_SLOT, npc("alpha")).orElseThrow();
        assertThat(name).isInstanceOf(TextProperty.class);

        ((TextProperty) name).applyInput(ClickContexts.carrier(player, viewer), "renamed");

        assertThat(repository.find(NpcName.of("renamed"))).isPresent();
        assertThat(repository.find(NpcName.of("alpha"))).isEmpty();
    }

    @Test
    void equipmentSlotSetFromHeldItemPersists() {
        create("alpha");
        player.getInventory().setItemInMainHand(new org.bukkit.inventory.ItemStack(Material.DIAMOND_HELMET));
        editorView.open(player, viewer, npc("alpha"));

        fireClick(EQUIPMENT_SLOT, ClickType.LEFT); // open the equipment sub-menu
        fireClick(EQUIP_HEAD_SLOT, ClickType.LEFT); // set the head slot from the held item

        assertThat(npc("alpha").equipment()).containsKey(EquipmentSlot.HEAD);
    }

    @Test
    void deleteIsGatedByAConfirmMenu() {
        create("alpha");
        editorView.open(player, viewer, npc("alpha"));

        fireClick(DELETE_SLOT, ClickType.LEFT); // opens the confirm menu, does not delete
        assertThat(repository.find(NpcName.of("alpha"))).isPresent();

        fireClick(CONFIRM_SLOT, ClickType.LEFT); // the confirm button deletes
        assertThat(repository.find(NpcName.of("alpha"))).isEmpty();
    }

    @Test
    void teleportButtonSendsTheViewerToTheNpcLocation() {
        create("alpha");
        editorView.open(player, viewer, npc("alpha"));

        fireClick(TELEPORT_SLOT, ClickType.LEFT);

        assertThat(teleportDestinations).containsExactly(AT);
    }

    // --- helpers ---

    private void create(String name) {
        services.create().create(viewer, NpcName.of(name), AT, null);
    }

    private Npc npc(String name) {
        return repository.find(NpcName.of(name)).orElseThrow();
    }

    /**
     * Click the first option in the open enum selector whose plain name is not {@code excluded}, and return that
     * name so the caller can assert it persisted. The selector draws one named button per option into its option
     * slots; the option's name is the entity-type word wrapped in {@code <value>…</value>}.
     */
    private String clickFirstSelectorOptionOtherThan(String excluded) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            org.bukkit.inventory.ItemStack item = inv.getItem(slot);
            if (item == null || item.getItemMeta() == null || item.getItemMeta().displayName() == null) {
                continue;
            }
            String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(item.getItemMeta().displayName());
            if (!name.isBlank() && !name.equals(excluded)) {
                fireClick(slot, ClickType.LEFT);
                return name;
            }
        }
        throw new AssertionError("no selector option other than " + excluded);
    }

    /**
     * The icon material of the open selector's option whose rendered name is {@code optionName}. The selector
     * draws one named button per option; the name is the value word wrapped in {@code <value>…</value>}.
     */
    private Material selectorIcon(Inventory inv, String optionName) {
        for (int slot = 0; slot < inv.getSize(); slot++) {
            org.bukkit.inventory.ItemStack item = inv.getItem(slot);
            if (item == null || item.getItemMeta() == null || item.getItemMeta().displayName() == null) {
                continue;
            }
            String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(item.getItemMeta().displayName());
            if (name.equals(optionName)) {
                return item.getType();
            }
        }
        throw new AssertionError("no selector option named " + optionName);
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /**
     * One editor-capable engine plus its single listener: the editor opens through this {@link Menus} and its enum
     * selector and delete-confirm children are routed by the one registered {@link MenuListener}, the engine path the
     * production wiring uses.
     */
    private Menus editorEngine() {
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

    private Notifier notifier() {
        return new Notifier(new KeyMessages(), new SilentSink());
    }

    private EntityEditorLayout editorLayout(Path dir) throws Exception {
        Path file = dir.resolve("modules").resolve("npc").resolve("gui").resolve("npc-editor.conf");
        Files.createDirectories(file.getParent());
        StringBuilder slots = new StringBuilder();
        for (int i = 0; i < EDITOR_SLOTS.size(); i++) {
            slots.append(EDITOR_SLOTS.get(i));
            if (i < EDITOR_SLOTS.size() - 1) {
                slots.append(", ");
            }
        }
        Files.writeString(
                file,
                "rows = 6\nback-icon = \"ARROW\"\ndelete-icon = \"BARRIER\"\n"
                        + "filler = \"BLACK_STAINED_GLASS_PANE\"\nback-slot = 49\ndelete-slot = 53\n"
                        + "property-slots = ["
                        + slots + "]\n");
        EntityEditorLayout codeDefault = new EntityEditorLayout(
                6,
                EDITOR_SLOTS,
                49,
                java.util.OptionalInt.of(53),
                Material.ARROW,
                Material.BARRIER,
                Material.BLACK_STAINED_GLASS_PANE);
        return new GuiLayouts(dir, NOOP).loadEntityEditor("npc", "npc-editor", codeDefault);
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
                new TeleportToNpc(repository, (who, destination) -> teleportDestinations.add(destination), notifier),
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
        private final java.util.Map<String, Npc> byName = new java.util.LinkedHashMap<>();

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

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
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
