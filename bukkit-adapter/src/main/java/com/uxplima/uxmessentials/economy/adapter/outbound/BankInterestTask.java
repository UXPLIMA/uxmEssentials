package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.BankInterestPolicy;
import com.uxplima.uxmessentials.economy.application.port.BankRepository;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SharedBank;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The self-rescheduling bank-interest loop (the {@link SalaryTask} pattern): on the policy's interval it mints the
 * tiered interest into every shared bank through {@link BankRepository#creditBank}, off the tick thread. Interest
 * is a system credit minted into the bank, no member is debited, and each bank's credit is a single atomic
 * {@code balance = balance + ?} so a concurrent deposit or withdraw is never clobbered. A bank below every tier's
 * minimum earns nothing.
 */
@NullMarked
public final class BankInterestTask {

    private final BankRepository banks;
    private final BankInterestPolicy policy;
    private final Scheduler scheduler;
    private final Logger log;
    private volatile boolean running;

    public BankInterestTask(BankRepository banks, BankInterestPolicy policy, Scheduler scheduler, Logger log) {
        this.banks = Objects.requireNonNull(banks, "banks");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    public void start() {
        if (!policy.enabled()) {
            return;
        }
        running = true;
        scheduleNext();
    }

    public void stop() {
        running = false;
    }

    private void scheduleNext() {
        if (running) {
            scheduler.asyncAfter(policy.interval(), this::tick);
        }
    }

    private void tick() {
        if (!running) {
            return;
        }
        scheduler.async(() -> {
            payAll();
            scheduleNext();
        });
    }

    /** Pay interest into every qualifying bank once; returns how many banks were credited. */
    public int payAll() {
        int paid = 0;
        for (SharedBank bank : banks.findAll()) {
            Money interest = policy.interestOn(bank.balance());
            if (!interest.isZero()) {
                banks.creditBank(bank.id(), interest);
                paid++;
            }
        }
        if (paid > 0) {
            log.info("Bank interest paid to {} bank(s)", paid);
        }
        return paid;
    }
}
