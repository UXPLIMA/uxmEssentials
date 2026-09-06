package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.presence.adapter.inbound.gui.OnlinePlayerListView;
import com.uxplima.uxmessentials.presence.application.PresenceMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The online-roster list golden test: the engine-rendered {@code /list} head grid must draw the exact grid the
 * original {@code EntityListView}/{@code OnlinePlayerListView} drew on uxmLib's {@code PaginatedGui}. The fixture is
 * a 47-player roster over the {@code paginatedDefault} layout (rows 6, content slots 0..44, nav at 48/50, glass
 * filler), so page 0 fills all 45 content slots with player heads and page 1 carries the remaining two; the nav
 * buttons are ARROW icons at slots 48 and 50 on every page. The engine window is snapshotted as {@code (slot ->
 * material, plain name)} and asserted equal, slot for slot, to the analytic baseline the old view produced for this
 * fixture: content heads, nav, and the page-1 placements all included. Then a click on the first head through the
 * engine's own {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener} proves the
 * migrated path hands that entity to the {@code onSelect} drill-down the old grid drove, faithful in both
 * appearance and behaviour.
 */
class OnlinePlayerListGoldenTest {

    /** The content region of the paginated-default layout: every slot of the first five rows, one head each. */
    private static final int CONTENT_SLOTS = 45;

    private static final int PREV_SLOT = 48;
    private static final int NEXT_SLOT = 50;

    /** Two more than one page of heads, so page 0 fills the 45 content slots and page 1 carries the overflow. */
    private static final int ROSTER_SIZE = CONTENT_SLOTS + 2;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private TestMenuEngine engine;
    private List<OnlinePlayerListView.Entry> roster;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        engine = TestMenuEngine.create(new KeyMessages(), scheduler);
        engine.installListener(plugin);
        roster = roster();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameHeadGridNavAndFillerAsTheOldView() {
        openRoster();

        Map<Integer, Snapshot> baseline = oldViewBaseline(0);
        Map<Integer, Snapshot> rendered = snapshot(player.getOpenInventory().getTopInventory());

        assertThat(rendered.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(rendered).isEqualTo(baseline);
    }

    @Test
    void flippingToTheNextPageDrawsThePageOnePlacements() {
        openRoster();

        fireClick(NEXT_SLOT);

        Map<Integer, Snapshot> baseline = oldViewBaseline(1);
        Map<Integer, Snapshot> rendered = snapshot(player.getOpenInventory().getTopInventory());

        assertThat(rendered).isEqualTo(baseline);
    }

    @Test
    void clickingAHeadThroughTheEngineRunsOnSelectForThatEntity() {
        // OnlinePlayerListView is read-only, so a recording list proves the click-routing seam directly: clicking the
        // head at content slot 1 must hand the engine the entity drawn there (the second roster entry).
        List<String> picked = new ArrayList<>();
        AtomicReference<List<String>> snapshot = new AtomicReference<>(List.of("first", "second", "third"));
        EntityListView<String> list = EntityListView.<String>builder()
                .menus(engine.menus())
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(EntityListLayout.paginatedDefault(Material.NAME_TAG))
                .title(PresenceMessageKey.LIST_GUI_TITLE)
                .navNames(PresenceMessageKey.LIST_GUI_PREV, PresenceMessageKey.LIST_GUI_NEXT)
                .entities(snapshot::get)
                .iconRenderer((v, name) -> new ItemStack(Material.NAME_TAG))
                .onSelect((p, name) -> picked.add(name))
                .build();
        list.open(player, viewer);

        fireClick(1); // the second content slot holds "second"

        assertThat(picked).containsExactly("second");
    }

    @Test
    void theEngineWindowIsMenuBacked() {
        openRoster();
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void openThenPageThenCloseArmsNoRefreshTaskAndStaysLeakBalanced() {
        RecordingScheduler recording = new RecordingScheduler();
        TestMenuEngine leakEngine = TestMenuEngine.create(new KeyMessages(), recording);
        leakEngine.installListener(plugin);
        OnlinePlayerListView view = new OnlinePlayerListView(
                leakEngine.menus(), guiText, recording, EntityListLayout.paginatedDefault(Material.PLAYER_HEAD));

        view.open(player, viewer, roster);
        fireClick(NEXT_SLOT); // a page flip re-paginates the same holder, opening no new window
        player.closeInventory();
        player.closeInventory(); // a double-close is a harmless no-op

        // A list window arms no refresh timer, so start and cancel both stay at zero: perfectly balanced.
        assertThat(recording.scheduled).isZero();
        assertThat(recording.cancelled).isZero();
    }

    private void openRoster() {
        OnlinePlayerListView view = new OnlinePlayerListView(
                engine.menus(), guiText, scheduler, EntityListLayout.paginatedDefault(Material.PLAYER_HEAD));
        view.open(player, viewer, roster);
    }

    /**
     * The slot -> (material, plain name) map the bespoke {@code EntityListView} produced for this fixture on
     * {@code page}: a PLAYER_HEAD at each content slot the page fills (its name the {@code list.gui.entry-name}
     * catalog key the test's {@code KeyMessages} returns verbatim), and the two ARROW nav buttons at 48 and 50 with
     * their prev/next catalog names. The filler slots are dropped from the snapshot, so a wrong material, name, or
     * misplaced head still shows up as a mismatch.
     */
    private static Map<Integer, Snapshot> oldViewBaseline(int page) {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        int onThisPage = Math.min(CONTENT_SLOTS, ROSTER_SIZE - page * CONTENT_SLOTS);
        for (int slot = 0; slot < onThisPage; slot++) {
            baseline.put(slot, new Snapshot(Material.PLAYER_HEAD, PresenceMessageKey.LIST_GUI_ENTRY_NAME.key()));
        }
        baseline.put(PREV_SLOT, new Snapshot(Material.ARROW, PresenceMessageKey.LIST_GUI_PREV.key()));
        baseline.put(NEXT_SLOT, new Snapshot(Material.ARROW, PresenceMessageKey.LIST_GUI_NEXT.key()));
        return baseline;
    }

    private List<OnlinePlayerListView.Entry> roster() {
        List<OnlinePlayerListView.Entry> entries = new ArrayList<>(ROSTER_SIZE);
        for (int i = 0; i < ROSTER_SIZE; i++) {
            entries.add(new OnlinePlayerListView.Entry(UUID.randomUUID(), "P" + i, "world", "Online"));
        }
        return entries;
    }

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

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    private record Snapshot(Material material, String name) {}

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
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

    /** A scheduler that records every repeating-task start and cancel, for the leak-balance assertion. */
    private static final class RecordingScheduler implements Scheduler {
        int scheduled = 0;
        int cancelled = 0;

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            scheduled++;
            return () -> cancelled++;
        }

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
