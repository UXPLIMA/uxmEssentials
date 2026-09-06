package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.LoanService;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Background sweep that applies due loan auto-repayments, using the same self-rescheduling loop as the salary
 * task. Each tick enumerates the active debtors and applies their installments off the tick thread through
 * {@link LoanService#processAutoRepayments()}. That path goes through the loan repository's atomic repayment,
 * so the move commits or the cycle is pushed, never a partial debit. The sweep is purely repository work and
 * touches no Bukkit API, so it runs directly on the async scheduler.
 */
@NullMarked
public final class LoanRepaymentTask {

    private final Scheduler scheduler;
    private final LoanService loanService;
    private final Logger log;
    private final boolean enabled;
    private final Duration interval;
    private volatile boolean running;

    public LoanRepaymentTask(
            Scheduler scheduler, LoanService loanService, Logger log, boolean enabled, Duration interval) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.loanService = Objects.requireNonNull(loanService, "loanService");
        this.log = Objects.requireNonNull(log, "log");
        this.enabled = enabled;
        this.interval = Objects.requireNonNull(interval, "interval");
    }

    public void start() {
        if (!enabled) {
            return;
        }
        running = true;
        scheduleNext();
    }

    public void stop() {
        running = false;
    }

    private void scheduleNext() {
        if (!running) {
            return;
        }
        scheduler.asyncAfter(interval, this::tick);
    }

    private void tick() {
        if (!running) {
            return;
        }
        try {
            loanService.processAutoRepayments();
        } catch (RuntimeException failure) {
            log.error("loan auto-repayment sweep failed", failure);
        }
        scheduleNext();
    }
}
