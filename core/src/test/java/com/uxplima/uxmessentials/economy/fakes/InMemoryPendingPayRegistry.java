package com.uxplima.uxmessentials.economy.fakes;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.economy.application.port.PendingPayRegistry;
import com.uxplima.uxmessentials.economy.domain.PendingPay;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A hand-rolled {@link PendingPayRegistry} for the pay-confirm tests. One outstanding {@link PendingPay} per
 * payer, replaced on re-stage and cleared on take. The real adapter adds the {@code Scheduler.asyncAfter}
 * expiry; the use-case tests only need stage/peek/take/clear.
 */
public final class InMemoryPendingPayRegistry implements PendingPayRegistry {

    private final Map<PlayerRef, PendingPay> pending = new ConcurrentHashMap<>();

    @Override
    public void stage(PendingPay value) {
        pending.put(value.payer(), value);
    }

    @Override
    public Optional<PendingPay> peek(PlayerRef payer) {
        return Optional.ofNullable(pending.get(payer));
    }

    @Override
    public Optional<PendingPay> take(PlayerRef payer) {
        return Optional.ofNullable(pending.remove(payer));
    }

    @Override
    public void clear(PlayerRef payer) {
        pending.remove(payer);
    }
}
