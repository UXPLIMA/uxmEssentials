package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

import com.uxplima.uxmessentials.economy.adapter.inbound.gui.TransactionsHistoryMenu;
import com.uxplima.uxmessentials.economy.application.port.HistoryRecord;
import com.uxplima.uxmessentials.economy.application.port.TransactionHistory;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuVocabulary;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
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
 * The transaction-history golden test: the engine-rendered read-only history list must draw the exact content grid
 * and close button the original {@code TransactionsHistoryView} drew. The store holds two rows (a CREDIT and an admin
 * mutation), so the list draws a GREEN_WOOL and a GOLD_BLOCK icon (content slots 0 and 1, the kind material the
 * deleted view's switch resolved, the type surfacing through the {@code history_type_key} the test catalog echoes
 * back) and the BARRIER close at slot 49.
 *
 * <p>The original view set its prev/next arrows and then drew its bottom-row glass fillers <em>over</em> the same
 * 48/50 slots, so those arrows never actually showed: the visible bottom row was the close button and glass. The
 * snapshot therefore compares the content grid and the close slot, skipping filler glass and the nav slots, and
 * asserts the engine draws that identical grid; a separate assertion confirms the migrated spec restores working
 * prev/next arrows the original silently buried, the one deliberate improvement of the move. A click on the first
 * row through the engine's own {@link MenuListener} proves the migrated path is inert: a ledger row is immutable.
 */
class TransactionsHistoryListGoldenTest {

    /** The bottom-row nav slots the original view drew arrows into and then covered with glass. */
    private static final int PREV_SLOT = 48;

    private static final int NEXT_SLOT = 50;
    private static final int CLOSE_SLOT = 49;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private FakeHistory history;

    private final Path dataFolder = Path.of("nonexistent");

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        history = new FakeHistory();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameKindIconGridAndCloseAsTheOldView() {
        history.add(record(1L, "CREDIT", "PAY"));
        history.add(record(2L, "DEBIT", "ADMIN_SET"));

        Map<Integer, Snapshot> baseline = oldViewBaseline();
        Map<Integer, Snapshot> engine = contentAndClose(openEngine());

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
    }

    @Test
    void engineRestoresTheWorkingNavArrowsTheOldViewBuriedUnderGlass() {
        history.add(record(1L, "CREDIT", "PAY"));
        Inventory inv = openEngine();

        assertThat(Objects.requireNonNull(inv.getItem(PREV_SLOT)).getType()).isEqualTo(Material.ARROW);
        assertThat(Objects.requireNonNull(inv.getItem(NEXT_SLOT)).getType()).isEqualTo(Material.ARROW);
    }

    @Test
    void clickingAHistoryRowThroughTheEngineIsInert() {
        history.add(record(1L, "CREDIT", "PAY"));
        openEngine();
        Inventory before = player.getOpenInventory().getTopInventory();

        fireClick(0); // the first content cell holds the CREDIT row

        // A ledger row is immutable: the window stays this menu's, still open over the same inventory.
        assertThat(player.getOpenInventory().getTopInventory()).isSameAs(before);
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code TransactionsHistoryView} showed for this fixture (a
     * CREDIT row then an admin row), restricted to the content grid and close button. The slots the original
     * actually displayed. A GREEN_WOOL and a GOLD_BLOCK icon (content slots 0 and 1, the type surfacing through the
     * {@code history_type_key} token) and the BARRIER close at slot 49.
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.GREEN_WOOL, "eco.history.gui-type-credit"));
        baseline.put(1, new Snapshot(Material.GOLD_BLOCK, "eco.history.gui-type-admin"));
        baseline.put(CLOSE_SLOT, new Snapshot(Material.BARRIER, "eco.history.gui-close"));
        return baseline;
    }

    private Inventory openEngine() {
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        // The history spec binds the generic close action, so the shared vocabulary must be registered too.
        MenuVocabulary.registerActions(bindings, menus, false, NOOP);
        MenuVocabulary.registerConditions(bindings, mock(Permissions.class), mock(Logger.class));
        MenuVocabulary.registerPlaceholders(bindings);
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);

        TransactionsHistoryMenu menu = new TransactionsHistoryMenu(menus, history, scheduler, ZoneId.of("UTC"));
        menu.register(bindings, dataFolder, NOOP);
        menu.open(viewer, UUID.randomUUID(), "Target");
        return player.getOpenInventory().getTopInventory();
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private HistoryRecord record(long id, String type, String reason) {
        return new HistoryRecord(
                id, Instant.EPOCH, "u1", "Alice", "u2", "Bob", "coins", new BigDecimal("10"), type, reason);
    }

    /**
     * The slot -> (material, plain name) map for the content grid and the close slot only. The slots the original
     * view actually displayed. Filler glass and the nav slots (which the original buried under glass) are skipped, so
     * the comparison is over the cells both paths agree on.
     */
    private static Map<Integer, Snapshot> contentAndClose(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (slot == PREV_SLOT || slot == NEXT_SLOT) {
                continue;
            }
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.GRAY_STAINED_GLASS_PANE) {
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

    /**
     * Surfaces the entry name's {@code history_type_key} so a row's kind line appears; else the bare key. The engine
     * resolves a {@code @key} line through a lambda {@link MessageKey} carrying only the key string. The name spec is
     * {@code @%history_type_key%}: the engine substitutes the placeholder to the kind key, then looks that key up
     * so echoing the key back here makes the rendered name the kind key, which the baseline asserts.
     */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** A map-backed history store; the menu only reads the player/global/bank queries and flush. */
    private static final class FakeHistory implements TransactionHistory {
        private final List<HistoryRecord> rows = new ArrayList<>();

        void add(HistoryRecord record) {
            rows.add(record);
        }

        @Override
        public List<HistoryRecord> queryTransactions(UUID playerUuid, int limit, int offset) {
            return List.copyOf(rows);
        }

        @Override
        public List<HistoryRecord> queryGlobalTransactions(int limit, int offset) {
            return List.copyOf(rows);
        }

        @Override
        public void recordTransfer(String fromId, String toId, Money amount, EconomyReason reason, long at) {}

        @Override
        public void recordCredit(String ownerId, Money amount, EconomyReason reason, long at) {}

        @Override
        public void recordDebit(String ownerId, Money amount, EconomyReason reason, long at) {}

        @Override
        public List<HistoryRecord> queryBankTransactions(String bankId, int limit, int offset) {
            return List.copyOf(rows);
        }

        @Override
        public void flush() {}
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
