package com.uxplima.uxmessentials.messaging.adapter.outbound;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The mail-expiry sweep: a self-rescheduling async loop that deletes mail older than the configured
 * retention window so a mailbox cannot grow without bound (docs/10-feature-modules.md §15.7, mail expiry).
 * It runs off the tick thread (the delete is a bounded {@code DELETE ... WHERE sent_at < ?} on the indexed
 * column) and observes the module's {@code running} flag so it stops cleanly on disable, the §6.10
 * self-rescheduling-loop pattern, matching the teleport context's request-expiry sweep.
 *
 * <p>A non-positive retention disables expiry: the sweep keeps running but deletes nothing, so an operator
 * who wants mail to live forever simply sets the window to zero. The first sweep is delayed by one interval
 * so enable is not blocked.
 */
@NullMarked
public final class MailExpirySweep {

    private final Scheduler scheduler;
    private final MailRepository mail;
    private final Duration interval;
    private final Duration retention;
    private final BooleanSupplier running;
    private final Logger log;
    private final Clock clock;

    public MailExpirySweep(
            Scheduler scheduler,
            MailRepository mail,
            Duration interval,
            Duration retention,
            BooleanSupplier running,
            Logger log,
            Clock clock) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.mail = Objects.requireNonNull(mail, "mail");
        this.interval = Objects.requireNonNull(interval, "interval");
        this.retention = Objects.requireNonNull(retention, "retention");
        this.running = Objects.requireNonNull(running, "running");
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Arm the first sweep; subsequent sweeps reschedule themselves until the module stops. */
    public void start() {
        scheduleNext();
    }

    private void scheduleNext() {
        if (!running.getAsBoolean()) {
            return;
        }
        scheduler.asyncAfter(interval, this::sweepOnce);
    }

    private void sweepOnce() {
        if (!running.getAsBoolean()) {
            return;
        }
        try {
            purgeExpired();
        } catch (RuntimeException failure) {
            // A throwing delete must not skip the reschedule below, or expiry would stall and mailboxes grow forever.
            log.error("mail expiry sweep failed", failure);
        }
        scheduleNext();
    }

    private void purgeExpired() {
        if (retention.isZero() || retention.isNegative()) {
            return;
        }
        int removed = mail.deleteSentBefore(clock.instant().minus(retention));
        if (removed > 0) {
            log.debug("mail expiry sweep removed {} expired item(s)", removed);
        }
    }
}
