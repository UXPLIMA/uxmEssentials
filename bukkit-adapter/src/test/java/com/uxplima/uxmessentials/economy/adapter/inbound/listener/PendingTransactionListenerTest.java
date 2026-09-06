package com.uxplima.uxmessentials.economy.adapter.inbound.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.application.port.PendingTransactionRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The queued-credit path must never lose physical money. Since {@code getAndClearPending} atomically deletes the
 * rows, a credit that does not actually land (an unknown currency or a rejected credit (e.g. inventory full))
 * has to be re-queued, and the player is told only when the credit succeeds.
 */
class PendingTransactionListenerTest {

    private EconomyProvider economy;
    private PendingTransactionRepository pendingRepo;
    private EconomyNotifier notifier;
    private PendingTransactionListener listener;
    private Currency coins;
    private PlayerRef ref;

    @BeforeEach
    void setUp() {
        economy = mock(EconomyProvider.class);
        pendingRepo = mock(PendingTransactionRepository.class);
        notifier = mock(EconomyNotifier.class);
        Scheduler scheduler = mock(Scheduler.class);

        coins = mock(Currency.class);
        when(coins.id()).thenReturn(CurrencyId.of("coins"));
        when(economy.currencies()).thenReturn(Set.of(coins));
        when(notifier.amount(any())).thenReturn("10");

        listener = new PendingTransactionListener(economy, pendingRepo, notifier, scheduler);
        ref = new PlayerRef(UUID.randomUUID(), "Alice");
    }

    @Test
    void successfulCreditNotifiesAndDoesNotRequeue() {
        when(economy.credit(eq(ref), any())).thenReturn(Result.ok());

        listener.applyQueuedCredit(ref, "coins", new BigDecimal("10"));

        verify(economy).credit(eq(ref), any(Money.class));
        verify(notifier).send(eq(ref), eq(EconomyMessageKey.PHYSICAL_PENDING_RECEIVED), any());
        verify(pendingRepo, never()).queueCredit(any(), any(), any());
    }

    @Test
    void rejectedCreditIsRequeuedAndNotNotified() {
        when(economy.credit(eq(ref), any())).thenReturn(Result.err(TransferError.PHYSICAL_INVENTORY_FULL));

        listener.applyQueuedCredit(ref, "coins", new BigDecimal("10"));

        verify(pendingRepo).queueCredit(ref.uuid(), "coins", new BigDecimal("10"));
        verify(notifier, never()).send(any(), any(), any());
    }

    @Test
    void unknownCurrencyIsRequeuedAndNeverCredited() {
        listener.applyQueuedCredit(ref, "doubloons", new BigDecimal("25"));

        verify(economy, never()).credit(any(), any());
        verify(pendingRepo).queueCredit(ref.uuid(), "doubloons", new BigDecimal("25"));
        verify(notifier, never()).send(any(), any(), any());
    }
}
