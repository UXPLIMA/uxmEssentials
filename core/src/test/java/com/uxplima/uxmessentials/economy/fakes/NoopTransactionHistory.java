package com.uxplima.uxmessentials.economy.fakes;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.HistoryRecord;
import com.uxplima.uxmessentials.economy.application.port.TransactionHistory;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;

/** A {@link TransactionHistory} that records nothing: for tests that do not assert on the audit trail. */
public final class NoopTransactionHistory implements TransactionHistory {

    @Override
    public List<HistoryRecord> queryTransactions(UUID playerUuid, int limit, int offset) {
        return List.of();
    }

    @Override
    public List<HistoryRecord> queryGlobalTransactions(int limit, int offset) {
        return List.of();
    }

    @Override
    public void recordTransfer(String fromId, String toId, Money amount, EconomyReason reason, long at) {}

    @Override
    public void recordCredit(String ownerId, Money amount, EconomyReason reason, long at) {}

    @Override
    public void recordDebit(String ownerId, Money amount, EconomyReason reason, long at) {}

    @Override
    public List<HistoryRecord> queryBankTransactions(String bankId, int limit, int offset) {
        return List.of();
    }

    @Override
    public void flush() {}
}
