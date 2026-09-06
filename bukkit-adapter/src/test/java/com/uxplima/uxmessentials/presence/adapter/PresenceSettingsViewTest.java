package com.uxplima.uxmessentials.presence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
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
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.presence.adapter.inbound.gui.PresenceSettingsView;
import com.uxplima.uxmessentials.presence.adapter.outbound.InMemoryPresenceStore;
import com.uxplima.uxmessentials.presence.application.ClearAfkOnActivity;
import com.uxplima.uxmessentials.presence.application.MarkAfk;
import com.uxplima.uxmessentials.presence.application.SetNick;
import com.uxplima.uxmessentials.presence.application.port.NickStore;
import com.uxplima.uxmessentials.presence.application.port.PresenceAudience;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ActionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
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
 * MockBukkit coverage of the presence per-player settings panel. The panel draws one toggle per conf slot: the AFK
 * toggle reads the live {@link InMemoryPresenceStore} and writes through the real {@code MarkAfk}, and the vanish
 * toggle reads the store's overlaid vanished bit and writes through the injected vanish-toggle handle onto a fake
 * vanish authority (a mutable uuid set). Opening reflects the stored state, a click flips it, and a re-render shows the
 * new value. The scheduler is synchronous so the off-thread setter runs inline.
 */
class PresenceSettingsViewTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private InMemoryPresenceStore store;
    private PresenceServices services;
    private Consumer<PlayerRef> vanishToggle;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        Clock clock = Clock.systemUTC();
        // A fake vanish authority: the store overlays its vanished bit from this set, and the toggle handle flips it
        // the same contract the real vanish store and ToggleVanish satisfy in production.
        Set<UUID> vanished = ConcurrentHashMap.newKeySet();
        vanishToggle = who -> {
            if (!vanished.add(who.uuid())) {
                vanished.remove(who.uuid());
            }
        };
        store = new InMemoryPresenceStore(clock, vanished::contains);
        Notifier notifier = new Notifier(new KeyMessages(), new NoopSink());
        PresenceAudience audience = List::<PlayerRef>of;
        DomainEventPublisher events = new NoopEvents();
        MarkAfk markAfk = new MarkAfk(store, audience, notifier, events, clock);
        services = new PresenceServices(
                markAfk,
                new ClearAfkOnActivity(store, audience, notifier, events, clock),
                new SetNick(new NoopNicks(), notifier),
                new com.uxplima.uxmessentials.presence.application.ClearNick(new NoopNicks(), notifier));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rendersBothTogglesAtTheirConfSlots(@TempDir Path dir) throws Exception {
        view(dir).open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(inv.getItem(11).getType()).isEqualTo(Material.CLOCK); // afk toggle
        assertThat(inv.getItem(15).getType()).isEqualTo(Material.POTION); // vanish toggle
        assertThat(inv.getItem(22).getType()).isEqualTo(Material.ARROW); // back / close
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.BLACK_STAINED_GLASS_PANE); // filler
    }

    @Test
    void clickingAfkToggleFlipsTheStoredState(@TempDir Path dir) throws Exception {
        assertThat(store.current(viewer).afk()).isFalse();
        view(dir).open(player, viewer);

        fireClick(11, ClickType.LEFT);

        assertThat(store.current(viewer).afk()).isTrue(); // flipped on through MarkAfk
    }

    @Test
    void clickingVanishToggleFlipsTheStoredState(@TempDir Path dir) throws Exception {
        assertThat(store.current(viewer).vanished()).isFalse();
        view(dir).open(player, viewer);

        fireClick(15, ClickType.LEFT);

        assertThat(store.current(viewer).vanished()).isTrue(); // flipped on through ToggleVanish
    }

    @Test
    void openingReflectsTheStoredStateAfterAFlip(@TempDir Path dir) throws Exception {
        view(dir).open(player, viewer);
        assertThat(loreOf(11)).contains("presence.gui.settings.value-off"); // not AFK yet

        fireClick(11, ClickType.LEFT); // AFK on; the panel re-renders to the new state

        assertThat(loreOf(11)).contains("presence.gui.settings.value-on");
    }

    private PresenceSettingsView view(Path dir) throws Exception {
        writeLayout(dir);
        GuiLayouts layouts = new GuiLayouts(dir, NOOP);
        return new PresenceSettingsView(
                guiText, scheduler, layouts, new KeyMessages(), services, store, vanishToggle, engine());
    }

    /** A minimal editor-capable engine + listener so the migrated panel can open through the runtime. */
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

    private void writeLayout(Path dir) throws Exception {
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

    private String loreOf(int slot) {
        var lore = player.getOpenInventory()
                .getTopInventory()
                .getItem(slot)
                .getItemMeta()
                .lore();
        var serializer = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        StringBuilder out = new StringBuilder();
        if (lore != null) {
            lore.forEach(line -> out.append(serializer.serialize(line)).append('\n'));
        }
        return out.toString();
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** Echoes the key and any placeholder values so a rendered value-lore reveals the substituted state. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (placeholders.isEmpty()) {
                return key.key();
            }
            return key.key() + " " + String.join(" ", placeholders.values());
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

    /** Runs every scheduler hop inline, as the production schedulers would after their marshal. */
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
