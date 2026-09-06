package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.EconomyAudit;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * An {@link EconomyAudit} decorator that, alongside the wrapped trail, appends a plain human-readable line to a
 * dedicated {@code economy/operations.log} an operator can grep. Separate from both the structured operator log
 * ({@link LoggingEconomyAudit}) and the {@code transactions} telemetry table. Lines use player names rather than
 * UUIDs (a review reads "Steve paid 500 to Alex", not two UUIDs). Every write is dispatched off the calling
 * thread through the kernel {@link com.uxplima.uxmessentials.shared.application.port.Scheduler}, so the audit
 * never blocks a ledger path on file I/O; a write failure is logged, never swallowed. Off by default.
 */
@NullMarked
public final class FileEconomyAudit implements EconomyAudit {

    private final EconomyAudit delegate;
    private final com.uxplima.uxmessentials.shared.application.port.Scheduler scheduler;
    private final Logger log;
    private final Path file;
    private final boolean enabled;
    private final DateTimeFormatter timestamp =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public FileEconomyAudit(
            EconomyAudit delegate,
            com.uxplima.uxmessentials.shared.application.port.Scheduler scheduler,
            Logger log,
            Path file,
            boolean enabled) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.file = Objects.requireNonNull(file, "file");
        this.enabled = enabled;
        if (enabled) {
            ensureParent();
        }
    }

    @Override
    public void credited(PlayerRef owner, Money amount, EconomyReason reason) {
        delegate.credited(owner, amount, reason);
        append(money(amount) + " +" + amount.amount() + " to " + owner.name() + " (" + reason + ")");
    }

    @Override
    public void debited(PlayerRef owner, Money amount, EconomyReason reason) {
        delegate.debited(owner, amount, reason);
        append(money(amount) + " -" + amount.amount() + " from " + owner.name() + " (" + reason + ")");
    }

    @Override
    public void rejected(PlayerRef owner, Money requested, EconomyReason reason) {
        delegate.rejected(owner, requested, reason);
        append(money(requested) + " REJECTED " + requested.amount() + " for " + owner.name() + " (" + reason + ")");
    }

    @Override
    public void transferred(PlayerRef from, PlayerRef to, Money amount, EconomyReason reason) {
        delegate.transferred(from, to, amount, reason);
        append(money(amount) + " " + from.name() + " -> " + to.name() + " " + amount.amount() + " (" + reason + ")");
    }

    @Override
    public void adminMutation(PlayerRef actor, PlayerRef target, Money amount, EconomyReason reason) {
        delegate.adminMutation(actor, target, amount, reason);
        append(money(amount) + " admin " + actor.name() + " -> " + target.name() + " " + amount.amount() + " (" + reason
                + ")");
    }

    @Override
    public void bulkMutation(PlayerRef actor, Money amount, int affected, EconomyReason reason) {
        delegate.bulkMutation(actor, amount, affected, reason);
        append(money(amount) + " admin " + actor.name() + " bulk " + amount.amount() + " x" + affected + " (" + reason
                + ")");
    }

    @Override
    public void worthSet(PlayerRef actor, String material, Money price) {
        delegate.worthSet(actor, material, price);
        append("worth " + actor.name() + " set " + material + " = " + price.amount() + " " + money(price));
    }

    @Override
    public void worthCleared(PlayerRef actor, String material) {
        delegate.worthCleared(actor, material);
        append("worth " + actor.name() + " cleared " + material);
    }

    private void append(String line) {
        if (!enabled) {
            return;
        }
        String entry = "[" + timestamp.format(Instant.now()) + "] " + line + System.lineSeparator();
        scheduler.async(() -> {
            try {
                Files.writeString(
                        file, entry, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException failure) {
                log.error("Failed to append to the economy operations log", failure);
            }
        });
    }

    private void ensureParent() {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException failure) {
            log.error("Failed to create the economy operations-log directory", failure);
        }
    }

    private static String money(Money amount) {
        return "[" + amount.currency().id().value() + "]";
    }
}
