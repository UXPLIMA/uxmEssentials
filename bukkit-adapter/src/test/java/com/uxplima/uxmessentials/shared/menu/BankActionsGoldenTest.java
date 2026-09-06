package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankActionsMenu;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankListMenu;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankMembersMenu;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankNavigation;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.TransactionsHistoryMenu;
import com.uxplima.uxmessentials.economy.application.BankService;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SharedBank;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The per-bank actions panel golden test: the engine-rendered panel must draw the exact window the original
 * {@code BankActionsView} drew, and its buttons must keep the behaviour. The panel draws deposit GOLD_INGOT@10,
 * withdraw IRON_INGOT@12, members PLAYER_HEAD@14, logs BOOK@16 and back BARRIER@22 over a grey-glass backdrop
 * snapshotted as {@code (slot -> material, plain name)} and asserted equal slot for slot to the baseline the old
 * view produced (frozen here so the old class could be deleted). Deposit / withdraw capture an amount through the
 * anvil seam (MockBukkit cannot drive a live anvil), so the apply branch their submit callback drives is verified
 * through the package-private apply seam to reach {@code BankService.deposit/withdraw}; the members, logs and back
 * clicks route through the engine's own listener to the right migrated engine menus.
 */
class BankActionsGoldenTest {

    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;
    private static final int DEPOSIT_SLOT = 10;
    private static final int WITHDRAW_SLOT = 12;
    private static final int MEMBERS_SLOT = 14;
    private static final int LOGS_SLOT = 16;
    private static final int BACK_SLOT = 22;

    private static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .symbol("$")
            .plural("coins")
            .precision(2)
            .build();

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewerRef;
    private SharedBank bank;

    private BankService bankService;
    private GuiText guiText;
    private Scheduler scheduler;
    private TestMenuEngine engine;
    private TextInput textInput;
    private AnvilInput anvil;
    private TransactionsHistoryMenu historyView;
    private BankMembersMenu membersMenu;
    private BankListMenu listMenu;
    private final AtomicReference<BankNavigation> navigationHolder = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewerRef = new PlayerRef(player.getUniqueId(), player.getName());
        bank = new SharedBank("eEa12523", "Vault", Money.of(COINS, new BigDecimal("100")), viewerRef, List.of(), 0L);

        bankService = mock(BankService.class);
        when(bankService.getBank(eq(bank.id()))).thenReturn(java.util.Optional.of(bank));
        when(bankService.getBankIdsForPlayer(any(PlayerRef.class))).thenReturn(List.of());

        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        engine = TestMenuEngine.create(new KeyMessages(), scheduler);
        engine.installListener(plugin);
        anvil = new AnvilInput(plugin);
        anvil.install();
        Guis.install(plugin);
        textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP);
        historyView = mock(TransactionsHistoryMenu.class);
        membersMenu = mock(BankMembersMenu.class);
        listMenu = mock(BankListMenu.class);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameActionsPanelAsTheOldView() {
        BankActionsMenu menu = wire();
        menu.open(player, bank);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getSize()).isEqualTo(27);
        assertThat(snapshot(inv)).isEqualTo(baseline());
    }

    // Deposit / withdraw capture an amount through the anvil seam, which MockBukkit leaves unimplemented (no
    // player.openAnvil), so the prompt-opening click cannot be exercised here. The apply seam, the branch the
    // prompt's submit callback drives. Is verified to reach BankService.deposit/withdraw with Money.of(currency,
    // amount) in BankActionsMenuApplyTest, which lives in the menu's own package and so can drive the package-private
    // applyTransfer. This golden test covers the click-driven flows the engine routes: the render and the
    // members/logs/back navigation.

    @Test
    void membersClickOpensTheEngineMembersMenu() {
        BankActionsMenu menu = wire();
        menu.open(player, bank);

        fireClick(MEMBERS_SLOT);

        verify(membersMenu).open(player, bank);
    }

    @Test
    void logsClickOpensTheBankTransactionList() {
        BankActionsMenu menu = wire();
        menu.open(player, bank);

        fireClick(LOGS_SLOT);

        verify(historyView).openForBank(viewerRef, bank.id(), bank.name());
    }

    @Test
    void backClickReopensTheEngineBankList() {
        BankActionsMenu menu = wire();
        menu.open(player, bank);

        fireClick(BACK_SLOT);

        verify(listMenu).open(player);
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code BankActionsView} produced: deposit GOLD_INGOT@10,
     * withdraw IRON_INGOT@12, members PLAYER_HEAD@14, logs BOOK@16, and back BARRIER@22, each carrying its catalog key
     * (the test's {@code KeyMessages} returns each key verbatim, so a wrong key or material still mismatches). The
     * grey-glass backdrop fills every other slot.
     */
    private static Map<Integer, Snapshot> baseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        for (int slot = 0; slot < 27; slot++) {
            baseline.put(slot, new Snapshot(FILLER, ""));
        }
        baseline.put(
                DEPOSIT_SLOT, new Snapshot(Material.GOLD_INGOT, key(EconomyMessageKey.BANK_ACTIONS_GUI_DEPOSIT_NAME)));
        baseline.put(
                WITHDRAW_SLOT,
                new Snapshot(Material.IRON_INGOT, key(EconomyMessageKey.BANK_ACTIONS_GUI_WITHDRAW_NAME)));
        baseline.put(
                MEMBERS_SLOT, new Snapshot(Material.PLAYER_HEAD, key(EconomyMessageKey.BANK_ACTIONS_GUI_MEMBERS_NAME)));
        baseline.put(LOGS_SLOT, new Snapshot(Material.BOOK, key(EconomyMessageKey.BANK_ACTIONS_GUI_LOGS_NAME)));
        baseline.put(BACK_SLOT, new Snapshot(Material.ARROW, key(EconomyMessageKey.BANK_ACTIONS_GUI_BACK)));
        return baseline;
    }

    private BankActionsMenu wire() {
        MenuBindings bindings = engine.bindings();
        Supplier<BankNavigation> navigation = () -> Objects.requireNonNull(navigationHolder.get(), "navigation");
        BankActionsMenu menu = new BankActionsMenu(
                engine.menus(), bankService, textInput, scheduler, new KeyMessages(), historyView, navigation);
        menu.register(bindings, specDir(), NOOP);
        navigationHolder.set(new BankNavigation(listMenu, menu, membersMenu));
        return menu;
    }

    private static String key(EconomyMessageKey messageKey) {
        return messageKey.key();
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The bundled spec directory under the source tree, so the menu loads the shipped actions-panel spec. */
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

    private record Snapshot(Material material, String name) {}

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
