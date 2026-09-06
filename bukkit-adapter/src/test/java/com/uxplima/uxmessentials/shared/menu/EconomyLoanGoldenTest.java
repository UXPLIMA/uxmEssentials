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

import com.uxplima.uxmessentials.economy.adapter.inbound.gui.CurrencyPickerMenu;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.LoanDashboardMenu;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.LoanService;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Loan;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuVocabulary;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The {@code /loan} dashboard golden test: the engine-rendered panel must draw the exact window the original
 * {@code LoanGuiView} drew, and its loan strip and request button must keep their behaviour. The panel draws the
 * SUNFLOWER@4 credit profile, one BOOK per active loan across slots 10..16, the EMERALD_BLOCK@20 request button and
 * the BARRIER@22 close over a grey-glass backdrop, snapshotted as {@code (slot -> material, plain name)} and
 * asserted equal slot for slot to the baseline the old view produced (frozen here so the old class could be
 * deleted), with the loan entry's lore asserted line for line. A left click on a loan pays an installment, a right
 * click pays it off, and a shift-click prompts a custom amount. All running the {@code LoanService} repayment; the
 * custom-amount and the request flows ride the anvil seam, which MockBukkit leaves unimplemented, so they are driven
 * through the package-private apply seam, and the request button is shown to open the engine currency picker.
 */
class EconomyLoanGoldenTest {

    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;
    private static final int PROFILE_SLOT = 4;
    private static final int LOAN_FIRST_SLOT = 10;
    private static final int REQUEST_SLOT = 20;
    private static final int CLOSE_SLOT = 22;

    private static final String LOAN_ID = "0123456789abcdef";
    private static final java.util.UUID DEBTOR_ID = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .symbol("$")
            .plural("coins")
            .precision(2)
            .build();
    private static final Currency GEMS = Currency.builder(CurrencyId.of("gems"))
            .symbol("♦")
            .plural("gems")
            .precision(0)
            .build();

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewerRef;

    private GuiText guiText;
    private Scheduler scheduler;
    private TestMenuEngine engine;
    private TextInput textInput;
    private AnvilInput anvil;
    private EconomyNotifier notifier;
    private LoanService loanService;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Debtor");
        viewerRef = new PlayerRef(player.getUniqueId(), player.getName());

        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        engine = TestMenuEngine.create(new KeyMessages(), scheduler);
        engine.installListener(plugin);
        anvil = new AnvilInput(plugin);
        anvil.install();
        Guis.install(plugin);
        textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP);
        notifier = new EconomyNotifier(new KeyMessages(), new NoopSink());

        loanService = mock(LoanService.class);
        when(loanService.getCreditScore(eq(viewerRef))).thenReturn(new Loan.CreditScore(viewerRef, 600, 0L));
        when(loanService.quote(600))
                .thenReturn(new LoanService.LoanQuote(new BigDecimal("10000"), new BigDecimal("0.22")));
        when(loanService.getActiveLoans(eq(viewerRef))).thenReturn(List.of(fixedLoan()));
        when(loanService.payInstallment(any(), any(), any())).thenReturn(Result.ok(Unit.INSTANCE));
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameDashboardAsTheOldView() {
        LoanDashboardMenu menu = wire(CurrencyRegistry.single(COINS));
        menu.open(player);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getSize()).isEqualTo(27);
        assertThat(snapshot(inv)).isEqualTo(baseline());
    }

    @Test
    void theLoanEntryLoreMatchesTheOldView() {
        LoanDashboardMenu menu = wire(CurrencyRegistry.single(COINS));
        menu.open(player);

        ItemStack loan = Objects.requireNonNull(
                player.getOpenInventory().getTopInventory().getItem(LOAN_FIRST_SLOT), "loan entry");
        assertThat(plainLore(TileText.body(loan))).isEqualTo(loanLoreBaseline());
    }

    @Test
    void leftClickPaysAnInstallment() {
        LoanDashboardMenu menu = wire(CurrencyRegistry.single(COINS));
        menu.open(player);

        fireClick(LOAN_FIRST_SLOT, ClickType.LEFT);
        // The installment amount is what is paid on a left click.
        verify(loanService).payInstallment(eq(viewerRef), eq(LOAN_ID), eq(Money.of(COINS, new BigDecimal("110.00"))));
    }

    @Test
    void rightClickPaysOffTheLoan() {
        LoanDashboardMenu menu = wire(CurrencyRegistry.single(COINS));
        menu.open(player);

        fireClick(LOAN_FIRST_SLOT, ClickType.RIGHT);
        // The remaining amount is what is paid on a right click (pay off).
        verify(loanService).payInstallment(eq(viewerRef), eq(LOAN_ID), eq(Money.of(COINS, new BigDecimal("1100.00"))));
    }

    // The shift-click custom-amount prompt and the request flow's amount/installment prompts ride the anvil seam,
    // which MockBukkit leaves unimplemented (no player.openAnvil), so the click that opens them cannot be exercised
    // here. Their apply branches, the seam the prompt's submit callback drives, are verified in
    // LoanDashboardApplyTest, which lives in the menu's own package and so can drive the package-private
    // applyCustomRepayment / applyAmount / applyInstallments. This golden test covers the click-driven flows the
    // engine routes: the render, the left/right repayment clicks, the request-button picker open, and close.

    @Test
    void theRequestButtonOpensTheCurrencyPickerWithMultipleCurrencies() {
        LoanDashboardMenu menu = wire(CurrencyRegistry.of(List.of(COINS, GEMS), COINS.id()));
        menu.open(player);

        fireClick(REQUEST_SLOT, ClickType.LEFT); // [Request New Loan] -> the engine currency picker
        Inventory picker = player.getOpenInventory().getTopInventory();
        assertThat(picker.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(picker.getSize()).isEqualTo(54); // the six-row picker
        assertThat(picker.getItem(0)).isNotNull();
        assertThat(picker.getItem(1)).isNotNull();
    }

    @Test
    void closeClickShutsTheDashboard() {
        LoanDashboardMenu menu = wire(CurrencyRegistry.single(COINS));
        menu.open(player);
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);

        fireClick(CLOSE_SLOT, ClickType.LEFT);
        assertThat(player.getOpenInventory().getType()).isEqualTo(InventoryType.CRAFTING);
    }

    /**
     * The slot -> (material, plain name) map the dashboard produces for a one-loan fixture: the SUNFLOWER@4 profile,
     * the BOOK@10 loan, the EMERALD_BLOCK@20 request and the BARRIER@22 close, each carrying its catalog key (the
     * test's {@code KeyMessages} returns the key verbatim, so a wrong key or material still mismatches). The
     * grey-glass backdrop fills every other slot. Except the unfilled loan-strip cells (11..16), which the engine
     * list runtime leaves empty rather than backed with filler, the same convention every engine list menu follows.
     */
    private static Map<Integer, Snapshot> baseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        for (int slot = 0; slot < 27; slot++) {
            baseline.put(slot, new Snapshot(FILLER, ""));
        }
        // The loan strip's content slots 11..16 hold no loan in this one-loan fixture; the list runtime clears them,
        // so they read as empty (skipped by the snapshot) rather than filler.
        for (int slot = LOAN_FIRST_SLOT + 1; slot <= 16; slot++) {
            baseline.remove(slot);
        }
        baseline.put(PROFILE_SLOT, new Snapshot(Material.SUNFLOWER, "loan.gui-profile-name"));
        baseline.put(LOAN_FIRST_SLOT, new Snapshot(Material.BOOK, "loan.gui-loan-name"));
        baseline.put(REQUEST_SLOT, new Snapshot(Material.EMERALD_BLOCK, "loan.gui-request-name"));
        baseline.put(CLOSE_SLOT, new Snapshot(Material.BARRIER, "loan.gui-close"));
        return baseline;
    }

    /** The loan entry's lore the old view emitted, line for line: principal, remaining, interest, installments-left,
     * installment-payout, the overdue next-debit, the divider, and the three hints. The test's KeyMessages returns
     * each key verbatim, so the line set and its order are asserted exactly. */
    private static List<String> loanLoreBaseline() {
        return List.of(
                "loan.gui-loan-principal",
                "loan.gui-loan-remaining",
                "loan.gui-loan-interest",
                "loan.gui-loan-installments-left",
                "loan.gui-loan-installment-payout",
                "loan.gui-loan-next-debit-overdue",
                "loan.gui-loan-divider",
                "loan.gui-loan-hint-installment",
                "loan.gui-loan-hint-full",
                "loan.gui-loan-hint-custom");
    }

    /** A fixed loan: id frozen for a deterministic name, nextPaymentAt in the past so the next-debit line is OVERDUE. */
    private static Loan fixedLoan() {
        return new Loan(
                LOAN_ID,
                new PlayerRef(DEBTOR_ID, "Debtor"),
                Money.of(COINS, new BigDecimal("1000.00")),
                Money.of(COINS, new BigDecimal("1100.00")),
                new BigDecimal("0.10"),
                10,
                Money.of(COINS, new BigDecimal("110.00")),
                0L,
                1L);
    }

    private LoanDashboardMenu wire(CurrencyRegistry currencies) {
        MenuBindings bindings = engine.bindings();
        Menus menus = engine.menus();
        // The dashboard's close button rides the engine's generic close action, registered by the menu vocabulary.
        MenuVocabulary.registerActions(bindings, menus, false, NOOP);
        CurrencyPickerMenu picker = new CurrencyPickerMenu(menus, new KeyMessages(), scheduler);
        LoanDashboardMenu menu = new LoanDashboardMenu(
                menus, loanService, currencies, textInput, scheduler, new KeyMessages(), notifier, picker);
        picker.register(bindings, specDir(), NOOP);
        menu.register(bindings, specDir(), NOOP);
        return menu;
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The bundled spec directory under the source tree, so the menu loads the shipped loan-dashboard spec. */
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

    private static List<String> plainLore(List<Component> lore) {
        return lore.stream()
                .map(line -> PlainTextComponentSerializer.plainText().serialize(line))
                .toList();
    }

    private record Snapshot(Material material, String name) {}

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
