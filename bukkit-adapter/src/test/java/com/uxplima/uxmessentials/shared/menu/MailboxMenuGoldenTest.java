package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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

import com.uxplima.uxmessentials.messaging.adapter.inbound.gui.MailboxMenu;
import com.uxplima.uxmessentials.messaging.application.ClearMail;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.domain.MailBox;
import com.uxplima.uxmessentials.messaging.domain.MailId;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.messaging.domain.MailSender;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
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
 * The mailbox golden test: the engine-rendered {@code /mail} mailbox must draw the exact windows the original
 * {@code MailboxView} drew. A player holds one unread mail, so the list draws a single WRITTEN_BOOK icon (content
 * slot 0), the clear LAVA_BUCKET button (slot 49), and the two nav ARROWs (slots 48 and 50). The engine's window is
 * snapshotted as {@code (slot -> material, plain name)} and asserted equal, slot for slot, to the baseline the old
 * view produced. Captured once while both rendered the same fixture, then frozen here as the contract so the old
 * class could be deleted (the glass filler is skipped, as in the pilot and vault golden tests). Then clicks through
 * the engine's own {@link MenuListener} prove the migrated path keeps the behaviour: opening the list marks the box
 * read (mirroring {@code /mail read}), a click on the mail opens its read-only detail (the WRITTEN_BOOK icon and the
 * back ARROW at their fixed slots), and the clear button is confirm-gated and only empties the box through the same
 * {@link ClearMail} use case on confirm.
 */
class MailboxMenuGoldenTest {

    private static final Material FILLER = Material.BLACK_STAINED_GLASS_PANE;

    private static final int DETAIL_ICON_SLOT = 13;
    private static final int DETAIL_BACK_SLOT = 22;
    private static final int CLEAR_SLOT = 49;
    private static final int CONFIRM_SLOT = 11; // uxmLib ConfirmMenu's confirm button slot
    private static final int CANCEL_SLOT = 15; // uxmLib ConfirmMenu's cancel button slot

    @TempDir
    Path dataFolder;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private FakeMail mail;
    private ClearMail clearMail;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        mail = new FakeMail();
        clearMail = new ClearMail(mail, new Notifier(new KeyMessages(), new NoopSink()));
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameMailGridClearAndNavAsTheOldView() {
        mail.deliver(viewer, "Bob", "hello there");

        Map<Integer, Snapshot> baseline = oldViewBaseline();
        Map<Integer, Snapshot> engine = snapshotEngine();

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
    }

    @Test
    void openingTheMailboxMarksTheBoxRead() {
        mail.deliver(viewer, "Bob", "hello there");
        assertThat(mail.load(viewer).hasUnread()).isTrue();

        openEngine();

        assertThat(mail.load(viewer).hasUnread()).isFalse(); // the GUI's read action, mirroring /mail read
    }

    @Test
    void clickingAMailThroughTheEngineOpensTheReadOnlyDetail() {
        mail.deliver(viewer, "Bob", "hello there");
        openEngine();

        fireClick(0, ClickType.LEFT); // content slot 0 is the only mail

        Inventory detail = player.getOpenInventory().getTopInventory();
        // The mail was unread on this open (the icon renders from the pre-mark snapshot), so the detail icon is a book.
        assertThat(detail.getItem(DETAIL_ICON_SLOT).getType()).isEqualTo(Material.WRITTEN_BOOK);
        assertThat(detail.getItem(DETAIL_BACK_SLOT).getType()).isEqualTo(Material.ARROW);
    }

    @Test
    void clearButtonIsConfirmGatedAndOnlyClearsOnConfirm() {
        mail.deliver(viewer, "Bob", "hello there");
        openEngine();

        fireClick(CLEAR_SLOT, ClickType.LEFT); // opens the confirm menu, clears nothing
        assertThat(mail.load(viewer).isEmpty()).isFalse();

        fireClick(CANCEL_SLOT, ClickType.LEFT); // cancel reopens the mailbox, box untouched
        assertThat(mail.load(viewer).isEmpty()).isFalse();

        fireClick(CLEAR_SLOT, ClickType.LEFT); // reopen the confirm menu
        fireClick(CONFIRM_SLOT, ClickType.LEFT); // confirm empties the box through ClearMail
        assertThat(mail.load(viewer).isEmpty()).isTrue();
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code MailboxView} produced for this fixture (one unread
     * mail), captured once while both paths rendered it identically and frozen here as the contract: one
     * WRITTEN_BOOK mail icon at content slot 0, the clear LAVA_BUCKET button at slot 49, and the two nav ARROWs at
     * slots 48 and 50. The plain names are the bare catalog keys because the test's {@code KeyMessages} returns each
     * key verbatim, so a wrong key or material still mismatches.
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.WRITTEN_BOOK, "messaging.gui.mail.entry-name"));
        baseline.put(48, new Snapshot(Material.ARROW, "messaging.gui.mail.prev"));
        baseline.put(49, new Snapshot(Material.LAVA_BUCKET, "messaging.gui.mail.clear"));
        baseline.put(50, new Snapshot(Material.ARROW, "messaging.gui.mail.next"));
        return baseline;
    }

    private Map<Integer, Snapshot> snapshotEngine() {
        openEngine();
        return snapshot(player.getOpenInventory().getTopInventory());
    }

    /** Build the engine, register the mailbox bindings + specs, and open the list for the player. */
    private void openEngine() {
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());

        MailboxMenu menu = new MailboxMenu(menus, guiText, scheduler, mail, clearMail);
        menu.register(bindings, dataFolder, NOOP);
        menu.open(viewer);
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

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    private record Snapshot(Material material, String name) {}

    /** An in-memory mail store the test seeds; the same shape the production GUI test fake uses. */
    private static final class FakeMail implements MailRepository {
        private final Map<UUID, List<MailItem>> boxes = new HashMap<>();
        private long nextId = 1L;

        void deliver(PlayerRef recipient, String sender, String body) {
            MailItem item = new MailItem(
                    MailId.of(nextId++),
                    recipient,
                    MailSender.system(sender),
                    MessageBody.of(body),
                    Instant.now(),
                    false);
            boxes.computeIfAbsent(recipient.uuid(), k -> new ArrayList<>()).add(item);
        }

        @Override
        public MailBox load(PlayerRef recipient) {
            return MailBox.of(recipient, boxes.getOrDefault(recipient.uuid(), List.of()));
        }

        @Override
        public long unreadCount(PlayerRef recipient) {
            return load(recipient).unreadCount();
        }

        @Override
        public MailItem append(MailItem item) {
            return item;
        }

        @Override
        public void markAllRead(PlayerRef recipient) {
            List<MailItem> items = boxes.get(recipient.uuid());
            if (items == null) {
                return;
            }
            items.replaceAll(MailItem::markRead);
        }

        @Override
        public void clear(PlayerRef recipient) {
            boxes.remove(recipient.uuid());
        }

        @Override
        public int deleteSentBefore(Instant cutoff) {
            return 0;
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
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
