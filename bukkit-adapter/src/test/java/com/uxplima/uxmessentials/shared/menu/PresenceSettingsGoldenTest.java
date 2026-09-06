package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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

import com.uxplima.uxmessentials.presence.adapter.PresenceServices;
import com.uxplima.uxmessentials.presence.adapter.inbound.gui.PresenceSettingsView;
import com.uxplima.uxmessentials.presence.adapter.outbound.InMemoryPresenceStore;
import com.uxplima.uxmessentials.presence.application.ClearAfkOnActivity;
import com.uxplima.uxmessentials.presence.application.ClearNick;
import com.uxplima.uxmessentials.presence.application.MarkAfk;
import com.uxplima.uxmessentials.presence.application.PresenceMessageKey;
import com.uxplima.uxmessentials.presence.application.SetNick;
import com.uxplima.uxmessentials.presence.application.port.NickStore;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.SettingsPanelView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ActionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The presence settings golden test. The migrated {@link PresenceSettingsView} now opens through
 * {@link Menus#openEditor} (its {@link SettingsPanelView} is a thin shim over the engine), so this asserts the
 * engine-rendered editor draws the exact panel the bespoke view drew: same material and same plain name at every
 * slot, and the same value-lore for each toggle, for both toggle states. The baseline is frozen from the panel's
 * geometry + catalog keys. The shim replaces the live "before" render, so the parity contract is the frozen
 * {@code (slot → material, name, value-lore)}, the way the kit/warp golden tests freeze a baseline. A real click on
 * the AFK slot through the engine's own {@link MenuListener} then proves the migrated path flips the state through
 * the same {@code MarkAfk} use case the {@code /afk} command drives and re-renders the slot to the new value.
 */
class PresenceSettingsGoldenTest {

    private static final Material FILLER = Material.BLACK_STAINED_GLASS_PANE;
    private static final List<Integer> SLOTS = List.of(11, 15);
    private static final int AFK_SLOT = SLOTS.get(0);
    private static final int VANISH_SLOT = SLOTS.get(1);
    private static final int BACK_SLOT = 22;

    @TempDir
    Path dir;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Messages messages;
    private SyncScheduler scheduler;
    private InMemoryPresenceStore store;
    private PresenceServices services;
    private Consumer<PlayerRef> vanishToggle;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        messages = new KeyMessages();
        guiText = new GuiText(messages);
        scheduler = new SyncScheduler();
        Clock clock = Clock.systemUTC();
        // The fixture keeps vanish off, so the overlay set stays empty and the vanish toggle handle is inert here.
        Set<UUID> vanished = ConcurrentHashMap.newKeySet();
        vanishToggle = who -> {
            if (!vanished.add(who.uuid())) {
                vanished.remove(who.uuid());
            }
        };
        store = new InMemoryPresenceStore(clock, vanished::contains);
        Notifier notifier = new Notifier(new KeyMessages(), new NoopSink());
        DomainEventPublisher events = new NoopEvents();
        MarkAfk markAfk = new MarkAfk(store, List::of, notifier, events, clock);
        services = new PresenceServices(
                markAfk,
                new ClearAfkOnActivity(store, List::of, notifier, events, clock),
                new SetNick(new NoopNicks(), notifier),
                new ClearNick(new NoopNicks(), notifier));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSamePanelAsTheOldViewWhenNotAfk() throws Exception {
        assertParity(false);
    }

    @Test
    void engineRendersTheSamePanelAsTheOldViewWhenAfk() throws Exception {
        // Drive the same MarkAfk use case the panel toggles, so the fixture's AFK state is set the production way.
        services.markAfk().toggle(viewer, java.util.Optional.empty());
        assertParity(true);
    }

    /** Snapshot the engine editor for the fixture and assert it matches the frozen panel baseline slot-for-slot. */
    private void assertParity(boolean afk) throws Exception {
        Map<Integer, Snapshot> baseline = baseline(afk, false);
        Map<Integer, Snapshot> engine = snapshotEngine();

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
        assertThat(engine).containsKey(AFK_SLOT);
        assertThat(engine).containsKey(VANISH_SLOT);
        assertThat(engine).containsKey(BACK_SLOT);
    }

    @Test
    void clickingTheAfkToggleThroughTheEngineFlipsTheStoreAndReRendersTheSlot() throws Exception {
        view().open(player, viewer);

        Inventory before = player.getOpenInventory().getTopInventory();
        assertThat(store.current(viewer).afk()).isFalse();
        assertThat(valueLoreOf(before.getItem(AFK_SLOT))).isEqualTo("value=" + onOff(false));

        fireClick(AFK_SLOT, ClickType.LEFT);

        // Flipped through the same MarkAfk use case the /afk command drives.
        assertThat(store.current(viewer).afk()).isTrue();
        Inventory after = player.getOpenInventory().getTopInventory();
        assertThat(after).isSameAs(before); // in-place re-render: no second openInventory, same holder
        assertThat(valueLoreOf(after.getItem(AFK_SLOT))).isEqualTo("value=" + onOff(true));
    }

    // --- snapshots ---

    private Map<Integer, Snapshot> snapshotEngine() throws Exception {
        view().open(player, viewer);
        return snapshot(player.getOpenInventory().getTopInventory());
    }

    /**
     * The frozen parity baseline: the {@code (slot → material, name, value-lore)} the bespoke panel drew. Names
     * resolve to the catalog key itself ({@code KeyMessages} echoes it); each toggle's value-lore is
     * {@code value=<on/off key>}; vanish is always off in this fixture.
     */
    private Map<Integer, Snapshot> baseline(boolean afk, boolean vanished) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        out.put(
                AFK_SLOT,
                new Snapshot(Material.CLOCK, PresenceMessageKey.GUI_SETTINGS_AFK.key(), "value=" + onOff(afk)));
        out.put(
                VANISH_SLOT,
                new Snapshot(
                        Material.POTION, PresenceMessageKey.GUI_SETTINGS_VANISH.key(), "value=" + onOff(vanished)));
        out.put(BACK_SLOT, new Snapshot(Material.ARROW, PresenceMessageKey.GUI_SETTINGS_BACK.key(), ""));
        return out;
    }

    private String onOff(boolean on) {
        return on ? PresenceMessageKey.GUI_SETTINGS_VALUE_ON.key() : PresenceMessageKey.GUI_SETTINGS_VALUE_OFF.key();
    }

    // --- harness ---

    private PresenceSettingsView view() throws Exception {
        writeLayout();
        GuiLayouts layouts = new GuiLayouts(dir, NOOP);
        return new PresenceSettingsView(guiText, scheduler, layouts, messages, services, store, vanishToggle, engine());
    }

    /** A minimal editor-capable engine + listener so the migrated panel opens through the runtime. */
    private Menus engine() {
        EditorRenderer editorRenderer = new EditorRenderer(guiText);
        ItemRenderer itemRenderer = new ItemRenderer(guiText, new PlaceholderRegistry());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        Menus menus = new Menus(renderer, scheduler, new ListSourceRegistry(), editorRenderer);
        MenuListener listener = new MenuListener(
                renderer,
                new ActionRegistry(),
                new ConditionRegistry(),
                scheduler,
                plugin,
                editorRenderer,
                menus.selectorOpener(),
                menus.confirmOpener());
        server.getPluginManager().registerEvents(listener, plugin);
        return menus;
    }

    private void writeLayout() throws Exception {
        Path file = dir.resolve("modules").resolve("presence").resolve("gui").resolve("presence-settings.conf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                rows = 3
                property-slots = [11, 15]
                back-slot = 22
                delete-slot = -1
                back-icon = "ARROW"
                delete-icon = "BARRIER"
                filler = "BLACK_STAINED_GLASS_PANE"
                """);
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The slot -> (material, plain name, value-lore) map for every populated, non-filler slot of {@code inv}. */
    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == FILLER) {
                continue;
            }
            out.put(slot, new Snapshot(item.getType(), plainName(item), valueLoreOrEmpty(item)));
        }
        return out;
    }

    private static String plainName(ItemStack item) {
        // The title reads off the tile wherever the canon puts it: the display name of a bare button, or the
        // first lore line of a titled tile, whose display name is deliberately blank.
        return TileText.title(item);
    }

    private static String valueLoreOrEmpty(ItemStack item) {
        // Under the title line the canon puts at the top of a titled tile: the body is what the value-lore is.
        List<Component> lore = TileText.body(item);
        if (lore.isEmpty()) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(lore.get(0));
    }

    private static String valueLoreOf(ItemStack item) {
        List<Component> lore = TileText.body(item);
        assertThat(lore).isNotEmpty();
        return PlainTextComponentSerializer.plainText().serialize(lore.get(0));
    }

    private record Snapshot(Material material, String name, String valueLore) {}

    // --- fakes ---

    /** Special-cases the value-lore key to wrap the substituted value; every other key echoes itself. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals(PresenceMessageKey.GUI_SETTINGS_VALUE_LORE.key())) {
                return "value=" + placeholders.getOrDefault("value", "");
            }
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class NoopEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final class NoopNicks implements NickStore {
        @Override
        public void setNick(PlayerRef who, String nick) {}

        @Override
        public void clearNick(PlayerRef who) {}
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
