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
import java.util.Map;

import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.LoanService;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Loan;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
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
 * Drives the package-private apply seams of the engine-rendered loan dashboard. The branches the anvil prompts'
 * submit callbacks run. MockBukkit cannot drive a live anvil, so the golden test (in the menu's sibling package)
 * covers the render and the click-driven repayment while this test, in the menu's own package, proves a typed
 * custom repayment reaches {@code LoanService.payInstallment} and a request's amount/installment seam reaches
 * {@code LoanService.takeLoan}, with malformed or out-of-range input running no use case, exactly as the old
 * {@code LoanGuiView} / {@code LoanRequestFlow} did.
 */
class LoanDashboardApplyTest {

    private static final String LOAN_ID = "0123456789abcdef";

    private static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .symbol("$")
            .plural("coins")
            .precision(2)
            .build();

    private ServerMock server;
    private PlayerMock player;
    private PlayerRef viewerRef;
    private LoanService loanService;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Debtor");
        viewerRef = new PlayerRef(player.getUniqueId(), player.getName());
        loanService = mock(LoanService.class);
        // The apply seams re-open the dashboard, which re-reads the profile and loans off the service.
        when(loanService.getCreditScore(any())).thenReturn(new Loan.CreditScore(viewerRef, 600, 0L));
        when(loanService.quote(anyScore())).thenReturn(new LoanService.LoanQuote(BigDecimal.TEN, BigDecimal.ZERO));
        when(loanService.getActiveLoans(any())).thenReturn(List.of());
        when(loanService.payInstallment(any(), any(), any())).thenReturn(Result.ok(Unit.INSTANCE));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aTypedCustomRepaymentReachesPayInstallment() {
        LoanDashboardMenu menu = menu(CurrencyRegistry.single(COINS));

        menu.applyCustomRepayment(player, viewerRef, fixedLoan(), COINS, "42.00");

        verify(loanService).payInstallment(eq(viewerRef), eq(LOAN_ID), eq(Money.of(COINS, new BigDecimal("42.00"))));
    }

    @Test
    void aMalformedCustomRepaymentRunsNoRepayment() {
        LoanDashboardMenu menu = menu(CurrencyRegistry.single(COINS));

        menu.applyCustomRepayment(player, viewerRef, fixedLoan(), COINS, "not-a-number");

        verify(loanService, never()).payInstallment(any(), any(), any());
    }

    @Test
    void aRequestAmountAndInstallmentCountReachTakeLoan() {
        when(loanService.takeLoan(eq(viewerRef), eq(Money.of(COINS, new BigDecimal("500"))), eq(6)))
                .thenReturn(Result.ok(fixedLoan()));
        LoanRequestFlow flow = flow();

        // Drive the amount seam, which on a valid amount opens the installment prompt; then the installment seam.
        flow.applyAmount(player, viewerRef, COINS, "500");
        flow.applyInstallments(player, viewerRef, COINS, Money.of(COINS, new BigDecimal("500")), "6");

        verify(loanService).takeLoan(eq(viewerRef), eq(Money.of(COINS, new BigDecimal("500"))), eq(6));
    }

    @Test
    void aMalformedRequestAmountRunsNoLoan() {
        LoanRequestFlow flow = flow();

        flow.applyAmount(player, viewerRef, COINS, "nope");

        verify(loanService, never()).takeLoan(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void anOutOfRangeInstallmentCountRunsNoLoan() {
        LoanRequestFlow flow = flow();

        flow.applyInstallments(player, viewerRef, COINS, Money.of(COINS, new BigDecimal("500")), "0");

        verify(loanService, never()).takeLoan(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    private LoanDashboardMenu menu(CurrencyRegistry currencies) {
        return new LoanDashboardMenu(
                mock(Menus.class),
                loanService,
                currencies,
                mock(TextInput.class),
                new SyncScheduler(),
                new KeyMessages(),
                new EconomyNotifier(new KeyMessages(), new NoopSink()),
                mock(CurrencyPickerMenu.class));
    }

    private LoanRequestFlow flow() {
        return new LoanRequestFlow(
                loanService,
                CurrencyRegistry.single(COINS),
                mock(TextInput.class),
                new SyncScheduler(),
                new EconomyNotifier(new KeyMessages(), new NoopSink()),
                mock(CurrencyPickerMenu.class),
                p -> {});
    }

    private static int anyScore() {
        return 600;
    }

    private static Loan fixedLoan() {
        return new Loan(
                LOAN_ID,
                new PlayerRef(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"), "Debtor"),
                Money.of(COINS, new BigDecimal("1000.00")),
                Money.of(COINS, new BigDecimal("1100.00")),
                new BigDecimal("0.10"),
                10,
                Money.of(COINS, new BigDecimal("110.00")),
                0L,
                1L);
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
