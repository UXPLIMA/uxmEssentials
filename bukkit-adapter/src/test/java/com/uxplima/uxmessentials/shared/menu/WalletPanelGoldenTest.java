package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

import com.uxplima.uxmessentials.economy.adapter.inbound.gui.TransactionsHistoryMenu;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.WalletPanelMenu;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.application.port.HistoryRecord;
import com.uxplima.uxmessentials.economy.application.port.TransactionHistory;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
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
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The wallet-dashboard golden test: the engine-rendered panel must draw the exact window the original
 * {@code WalletGuiView} drew, and its history button must reopen the migrated transaction-history list. The panel
 * draws the balance display (GOLD_INGOT@11), the banknotes display (PAPER@13), the history button (BOOK@15), and the
 * close button (BARRIER@22) over a grey-glass backdrop, snapshotted as {@code (slot -> material, plain name)} and
 * asserted equal slot for slot to the baseline the old view produced (frozen here so the old class could be deleted).
 * Clicking the history button fires through the engine's own {@link MenuListener} and opens the engine
 * {@code economy-transactions} list; clicking close shuts the panel.
 */
class WalletPanelGoldenTest {

    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;
    private static final int BALANCE_SLOT = 11;
    private static final int BANKNOTES_SLOT = 13;
    private static final int HISTORY_SLOT = 15;
    private static final int CLOSE_SLOT = 22;

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).symbol("$").precision(2).build();

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private GuiText guiText;
    private Scheduler scheduler;
    private EconomyNotifier notifier;
    private FakeHistory history;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        notifier = new EconomyNotifier(new KeyMessages(), new NoopSink());
        history = new FakeHistory();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameDashboardAsTheOldView() {
        openEngine();

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getSize()).isEqualTo(27);
        assertThat(snapshot(inv)).isEqualTo(baseline());
    }

    @Test
    void clickingTheHistoryButtonOpensTheTransactionList() {
        history.add(record());
        openEngine();

        fireClick(HISTORY_SLOT);

        // The history button opens the engine economy-transactions list, a fresh menu over the same player.
        Inventory list = player.getOpenInventory().getTopInventory();
        assertThat(list.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(list.getSize()).isEqualTo(54); // the six-row transaction list
        assertThat(Objects.requireNonNull(list.getItem(0)).getType()).isEqualTo(Material.GREEN_WOOL);
    }

    @Test
    void clickingCloseShutsThePanel() {
        openEngine();

        fireClick(CLOSE_SLOT);

        assertThat(player.getOpenInventory().getType()).isEqualTo(InventoryType.CRAFTING);
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code WalletGuiView} produced: the GOLD_INGOT balance
     * display, the PAPER banknotes display, the BOOK history button, and the BARRIER close, each carrying its catalog
     * key (the test's {@code KeyMessages} returns each key verbatim, so a wrong key or material still mismatches). The
     * grey-glass backdrop fills every other slot.
     */
    private static Map<Integer, Snapshot> baseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        for (int slot = 0; slot < 27; slot++) {
            baseline.put(slot, new Snapshot(FILLER, ""));
        }
        baseline.put(BALANCE_SLOT, new Snapshot(Material.GOLD_INGOT, "wallet.gui-balance-name"));
        baseline.put(BANKNOTES_SLOT, new Snapshot(Material.PAPER, "wallet.gui-banknotes-name"));
        baseline.put(HISTORY_SLOT, new Snapshot(Material.BOOK, "wallet.gui-history-name"));
        baseline.put(CLOSE_SLOT, new Snapshot(Material.BARRIER, "baltop.gui-close"));
        return baseline;
    }

    /** Build the engine, register the wallet + transaction bindings + specs, and open the dashboard for the player. */
    private void openEngine() {
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        MenuVocabulary.registerActions(bindings, menus, false, NOOP);
        MenuVocabulary.registerConditions(bindings, mock(Permissions.class), mock(Logger.class));
        MenuVocabulary.registerPlaceholders(bindings);
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);

        // The history button reopens the migrated economy-transactions list, so that menu is registered too.
        TransactionsHistoryMenu historyView = new TransactionsHistoryMenu(menus, history, scheduler, ZoneId.of("UTC"));
        historyView.register(bindings, specDir(), NOOP);
        WalletPanelMenu menu = new WalletPanelMenu(plugin, menus, new FakeProvider(), scheduler, notifier, historyView);
        menu.register(bindings, specDir(), NOOP);
        menu.open(player, COINS);
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The bundled spec directory under the source tree, so the test loads the shipped wallet + transaction specs. */
    private static Path specDir() {
        Path repoRoot = Path.of("").toAbsolutePath();
        while (repoRoot != null && !java.nio.file.Files.exists(repoRoot.resolve("settings.gradle.kts"))) {
            repoRoot = repoRoot.getParent();
        }
        Objects.requireNonNull(repoRoot, "repo root");
        return repoRoot.resolve("bukkit-adapter/src/main/resources");
    }

    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null) {
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

    private static HistoryRecord record() {
        return new HistoryRecord(
                1L,
                java.time.Instant.EPOCH,
                "u1",
                "Alice",
                "u2",
                "Bob",
                "coins",
                new BigDecimal("10"),
                "CREDIT",
                "PAY");
    }

    private record Snapshot(Material material, String name) {}

    /** A minimal {@link EconomyProvider} answering a fixed balance for the dashboard read. */
    private static final class FakeProvider implements EconomyProvider {
        @Override
        public boolean hasAccount(PlayerRef owner, Currency currency) {
            return true;
        }

        @Override
        public void ensureAccount(PlayerRef owner, Currency currency) {}

        @Override
        public Money balance(PlayerRef owner, Currency currency) {
            return Money.of(currency, new BigDecimal("42"));
        }

        @Override
        public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
            return Result.ok();
        }

        @Override
        public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
            return Result.ok();
        }

        @Override
        public TransferResult transfer(PlayerRef from, PlayerRef to, Money amount) {
            throw new UnsupportedOperationException("transfer is not exercised by this test");
        }

        @Override
        public List<BaltopRow> top(Currency currency, int limit) {
            return List.of();
        }

        @Override
        public Set<Currency> currencies() {
            return Set.of(COINS);
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

    /** A map-backed history store; the menu only reads the player query and flush. */
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
