package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.economy.application.port.PendingPayRegistry;
import com.uxplima.uxmessentials.economy.domain.PendingPay;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The one-outstanding-confirmation-per-payer registry behind the {@code /payconfirm} flow
 * ({@code docs/11-economy-integration.md} §9.2): a per-payer slot holding the staged {@link PendingPay}, with
 * a self-cancelling {@link Scheduler#asyncAfter} expiry that discards a stale prompt and logs
 * {@code event=economy_pay_confirm_expired}. Staging never debits. No money has moved while a pending pay is
 * held, so a dropped or expired entry loses nothing.
 *
 * <p>A second large {@code /pay} replaces the first and re-prompts (the slot is overwritten), and the expiry
 * timer only clears the slot if it still holds the <em>same</em> staged pay it was armed for, so a replaced
 * prompt's old timer never wipes the new one. The map is keyed by payer uuid and mutated through {@code put}
 * / {@code remove} on a {@link ConcurrentHashMap}, matching the per-player concurrent-map ownership rule.
 */
@NullMarked
public final class SchedulerPendingPayRegistry implements PendingPayRegistry {

    private final Map<UUID, PendingPay> slots = new ConcurrentHashMap<>();
    private final Scheduler scheduler;
    private final Logger log;
    private final Duration timeout;

    public SchedulerPendingPayRegistry(Scheduler scheduler, Logger log, Duration timeout) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("confirm timeout must be positive: " + timeout);
        }
    }

    @Override
    public void stage(PendingPay pending) {
        Objects.requireNonNull(pending, "pending");
        UUID payer = pending.payer().uuid();
        slots.put(payer, pending);
        scheduler.asyncAfter(timeout, () -> expire(payer, pending));
    }

    @Override
    public Optional<PendingPay> peek(PlayerRef payer) {
        return Optional.ofNullable(
                slots.get(Objects.requireNonNull(payer, "payer").uuid()));
    }

    @Override
    public Optional<PendingPay> take(PlayerRef payer) {
        return Optional.ofNullable(
                slots.remove(Objects.requireNonNull(payer, "payer").uuid()));
    }

    @Override
    public void clear(PlayerRef payer) {
        slots.remove(Objects.requireNonNull(payer, "payer").uuid());
    }

    private void expire(UUID payer, PendingPay staged) {
        // Only drop the slot when it still holds the exact pay this timer was armed for: a replaced prompt
        // installed a fresh slot and its own timer, which this stale one must not clobber.
        if (slots.remove(payer, staged)) {
            log.info(
                    "event=economy_pay_confirm_expired payer={} target={}",
                    payer,
                    staged.target().uuid());
        }
    }
}
