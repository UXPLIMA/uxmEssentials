package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.economy.application.BankService;
import com.uxplima.uxmessentials.economy.domain.BankError;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SharedBank;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Drives the package-private deposit/withdraw apply seam of the engine-rendered bank-actions panel, the branch the
 * anvil prompt's submit callback runs. MockBukkit cannot drive a live anvil, so the golden test (in the menu's
 * sibling package) covers the render and navigation while this test, in the menu's own package, proves a typed
 * amount parses against the bank's currency and reaches {@code BankService.deposit/withdraw}, exactly as the old
 * {@code BankActionsView} did, and that a malformed amount runs no op and reopens the list.
 */
class BankActionsMenuApplyTest {

    private static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .symbol("$")
            .plural("coins")
            .precision(2)
            .build();

    private ServerMock server;
    private PlayerMock player;
    private PlayerRef viewerRef;
    private SharedBank bank;

    private BankService bankService;
    private BankListMenu listMenu;
    private final AtomicReference<BankNavigation> navigationHolder = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Alice");
        viewerRef = new PlayerRef(player.getUniqueId(), player.getName());
        bank = new SharedBank("eEa12523", "Vault", Money.of(COINS, new BigDecimal("100")), viewerRef, List.of(), 0L);
        bankService = mock(BankService.class);
        listMenu = mock(BankListMenu.class);
        navigationHolder.set(new BankNavigation(listMenu, mock(BankActionsMenu.class), mock(BankMembersMenu.class)));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void depositApplySeamReachesBankServiceDeposit() {
        BankActionsMenu menu = menu();
        when(bankService.deposit(eq(viewerRef), eq(bank.id()), any(Money.class)))
                .thenReturn(Result.<BankError>ok());

        menu.applyTransfer(player, viewerRef, bank, "25", true);

        verify(bankService).deposit(eq(viewerRef), eq(bank.id()), eq(Money.of(COINS, new BigDecimal("25"))));
    }

    @Test
    void withdrawApplySeamReachesBankServiceWithdraw() {
        BankActionsMenu menu = menu();
        when(bankService.withdraw(eq(viewerRef), eq(bank.id()), any(Money.class)))
                .thenReturn(Result.<BankError>ok());

        menu.applyTransfer(player, viewerRef, bank, "10", false);

        verify(bankService).withdraw(eq(viewerRef), eq(bank.id()), eq(Money.of(COINS, new BigDecimal("10"))));
    }

    @Test
    void aMalformedAmountRunsNoOpAndReopensTheList() {
        BankActionsMenu menu = menu();

        menu.applyTransfer(player, viewerRef, bank, "not-a-number", true);

        verify(bankService, never()).deposit(any(), any(), any());
        verify(listMenu).open(player);
    }

    private BankActionsMenu menu() {
        Supplier<BankNavigation> navigation = () -> Objects.requireNonNull(navigationHolder.get(), "navigation");
        return new BankActionsMenu(
                mock(Menus.class),
                bankService,
                mock(TextInput.class),
                new SyncScheduler(),
                new KeyMessages(),
                mock(com.uxplima.uxmessentials.economy.adapter.inbound.gui.TransactionsHistoryMenu.class),
                navigation);
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, java.util.Map<String, String> placeholders) {
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
}
