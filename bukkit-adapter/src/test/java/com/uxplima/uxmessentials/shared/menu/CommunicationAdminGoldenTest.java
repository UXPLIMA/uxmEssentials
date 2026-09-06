package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.bossbar.BossBar;

import com.uxplima.uxmessentials.communication.adapter.ChatLock;
import com.uxplima.uxmessentials.communication.adapter.inbound.gui.CommunicationAdminMenu;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitAnnouncerBroadcaster;
import com.uxplima.uxmessentials.communication.application.port.BroadcastOptOutStore;
import com.uxplima.uxmessentials.communication.domain.Announcement;
import com.uxplima.uxmessentials.communication.domain.AnnouncerConfig;
import com.uxplima.uxmessentials.communication.domain.Ordering;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ChannelBroadcaster;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ChannelDisplay;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.Guis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The communication admin golden test: the engine-rendered panel and its read-only announcer list must draw the
 * exact windows the original {@code CommunicationAdminView} drew. Both paths are opened for the same player against
 * the same fixture, and each populated slot. The four panel buttons and the back button, then the two
 * announcement icons and the two nav arrows. Is snapshotted as {@code (slot -> material, plain name)} and asserted
 * equal slot for slot (the glass filler is skipped, as it is in the pilot and vault golden tests). Then clicks
 * through the engine's own {@link MenuListener} prove the migrated path keeps the behaviour: the chat-lock button
 * flips the live {@link ChatLock} the {@code /togglechat} command flips, the clearchat button is confirm-gated and
 * only fans out on confirm, and the announcer button opens the engine's read-only announcer list.
 */
class CommunicationAdminGoldenTest {

    private static final List<Integer> BUTTON_SLOTS = List.of(10, 12, 14, 16);
    private static final int LOCK_SLOT = BUTTON_SLOTS.get(0);
    private static final int CLEARCHAT_SLOT = BUTTON_SLOTS.get(1);
    private static final int ANNOUNCER_SLOT = BUTTON_SLOTS.get(3);
    private static final int CONFIRM_SLOT = 11; // uxmLib ConfirmMenu confirm button
    private static final Material FILLER = Material.BLACK_STAINED_GLASS_PANE;

    @TempDir
    Path dir;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private ChatLock chatLock;
    private RecordingSink sink;
    private BukkitAnnouncerBroadcaster broadcaster;
    private Notifier notifier;
    private TextInput textInput;
    private final List<Announcement> announcements = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        chatLock = new ChatLock();
        sink = new RecordingSink();
        notifier = new Notifier(new KeyMessages(), sink);
        broadcaster = new BukkitAnnouncerBroadcaster(
                sink, new AlwaysReceives(), new ChannelBroadcaster(scheduler, display()), p -> null, scheduler);
        textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP);
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSamePanelButtonsAsTheOldView() {
        Map<Integer, Snapshot> baseline = panelBaseline();
        Map<Integer, Snapshot> engine = snapshotEnginePanel();

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
    }

    @Test
    void engineRendersTheSameAnnouncerListAsTheOldView() {
        announcements.add(announcement("welcome"));
        announcements.add(announcement("rules"));

        Map<Integer, Snapshot> baseline = announcerBaseline();
        Map<Integer, Snapshot> engine = snapshotEngineAnnouncer();

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
    }

    @Test
    void clickingChatLockThroughTheEngineFlipsTheLiveLock() throws Exception {
        openEnginePanel();
        assertThat(chatLock.isLocked()).isFalse();

        fireClick(LOCK_SLOT, ClickType.LEFT);

        assertThat(chatLock.isLocked()).isTrue(); // flipped through the live ChatLock the command uses
    }

    @Test
    void clearchatThroughTheEngineIsConfirmGatedAndOnlyFlushesOnConfirm() throws Exception {
        openEnginePanel();

        fireClick(CLEARCHAT_SLOT, ClickType.LEFT); // opens the confirm menu, flushes nothing
        assertThat(sink.deliveries).isEmpty();

        fireClick(CONFIRM_SLOT, ClickType.LEFT); // confirm runs the /clearchat fan-out

        assertThat(sink.blankLines()).isGreaterThanOrEqualTo(100); // a screenful of blank lines was pushed
    }

    @Test
    void announcerButtonThroughTheEngineOpensTheReadOnlyList() throws Exception {
        announcements.add(announcement("welcome"));
        openEnginePanel();

        fireClick(ANNOUNCER_SLOT, ClickType.LEFT); // opens the engine's read-only announcer list

        Inventory list = player.getOpenInventory().getTopInventory();
        assertThat(list.getItem(0)).isNotNull();
        assertThat(list.getItem(0).getType()).isEqualTo(Material.PAPER);
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code CommunicationAdminView} panel produced, captured
     * once while both paths rendered it identically and frozen here as the contract so the old class could be
     * deleted: the four buttons (chat-lock IRON_DOOR, clearchat LAVA_BUCKET, broadcast BELL, announcer
     * WRITABLE_BOOK) at slots 10/12/14/16 and the back ARROW at slot 22. The plain names are the bare catalog keys
     * because the test's {@code KeyMessages} returns each key verbatim, so a wrong key or material still mismatches.
     */
    private static Map<Integer, Snapshot> panelBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(10, new Snapshot(Material.IRON_DOOR, "communication.gui.chat-lock"));
        baseline.put(12, new Snapshot(Material.LAVA_BUCKET, "communication.gui.clearchat"));
        baseline.put(14, new Snapshot(Material.BELL, "communication.gui.broadcast"));
        baseline.put(16, new Snapshot(Material.WRITABLE_BOOK, "communication.gui.announcer"));
        baseline.put(22, new Snapshot(Material.ARROW, "communication.gui.panel.back"));
        return baseline;
    }

    /**
     * The slot -> (material, plain name) map the deleted view's read-only announcer list produced for this fixture
     * (two announcements), frozen here as the contract: two PAPER entry icons at content slots 0 and 1 and the two
     * nav ARROWs at slots 48 and 50.
     */
    private static Map<Integer, Snapshot> announcerBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.PAPER, "communication.gui.announcer.entry-name"));
        baseline.put(1, new Snapshot(Material.PAPER, "communication.gui.announcer.entry-name"));
        baseline.put(48, new Snapshot(Material.ARROW, "communication.gui.announcer.prev"));
        baseline.put(50, new Snapshot(Material.ARROW, "communication.gui.announcer.next"));
        return baseline;
    }

    // --- engine rendering ---

    private Map<Integer, Snapshot> snapshotEnginePanel() {
        openEnginePanel();
        return snapshot(player.getOpenInventory().getTopInventory());
    }

    private Map<Integer, Snapshot> snapshotEngineAnnouncer() {
        openEnginePanel();
        fireClick(ANNOUNCER_SLOT, ClickType.LEFT);
        return snapshot(player.getOpenInventory().getTopInventory());
    }

    /** Build the engine, register the communication bindings + specs, and open the panel for the player. */
    private void openEnginePanel() {
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());

        CommunicationAdminMenu menu = new CommunicationAdminMenu(
                menus,
                guiText,
                scheduler,
                new KeyMessages(),
                chatLock,
                broadcaster,
                "<prefix> ",
                () -> new AnnouncerConfig(Duration.ofMinutes(5), 0, Ordering.SEQUENTIAL, List.copyOf(announcements)),
                notifier,
                sink,
                textInput);
        menu.register(bindings, dir, NOOP);
        menu.open(viewer);
    }

    // --- helpers ---

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

    private static Announcement announcement(String id) {
        return new Announcement(
                id,
                List.of("<body>line</body>"),
                DisplayCondition.always(),
                Optional.empty(),
                Set.of(BroadcastChannel.CHAT),
                Optional.empty(),
                false);
    }

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    private record Snapshot(Material material, String name) {}

    private static ChannelDisplay display() {
        return new ChannelDisplay(100, 500, 100, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS, 1);
    }

    private static final class RecordingSink implements MessageSink {
        private final List<String> deliveries = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            deliveries.add(renderedText);
        }

        long blankLines() {
            return deliveries.stream().filter(String::isEmpty).count();
        }
    }

    private static final class AlwaysReceives implements BroadcastOptOutStore {
        @Override
        public boolean receivesBroadcasts(PlayerRef who) {
            return true;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            return true;
        }

        @Override
        public void forget(PlayerRef who) {}
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
