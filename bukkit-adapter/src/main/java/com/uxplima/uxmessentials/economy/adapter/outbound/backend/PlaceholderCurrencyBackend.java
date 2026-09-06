package com.uxplima.uxmessentials.economy.adapter.outbound.backend;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Server;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Precision;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.adapter.outbound.BoundedAwait;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * The escape-hatch currency backend: an operator wires an economy nobody wrote a bridge for by pointing this at a
 * PlaceholderAPI placeholder for the balance and two console commands for the credit and the debit. Its id is
 * {@code placeholder:<name>}, named by a currency in {@code currencies.<id>.backend}.
 *
 * <p>A console command has no return value, so this backend <strong>cannot observe whether its take command
 * succeeded</strong>. {@link #debit} reads the balance, refuses when it falls short, dispatches the take command, and
 * returns {@link Result#ok()} optimistically. That blind spot is why {@link #atomicDebit()} is false: the serialising
 * wrapper cannot make a fire-and-forget command a guarded compare-and-take, and why config validation refuses to
 * schedule recurring charges (player-warp rent) against such a currency unless the operator turns on
 * {@code allow-nonatomic-recurring}. The sufficiency check still runs before every take, so a short balance is
 * rejected rather than handed a free purchase.
 *
 * <p>{@code Bukkit.dispatchCommand} and {@code PlaceholderAPI.setPlaceholders} are both tick-thread APIs, so the give
 * and take commands and the balance read all reach the global region thread through the injected {@link Scheduler}. A
 * balance read already on a tick thread resolves inline; otherwise it hops onto the global thread, completes a future,
 * and waits with a short bound so a read can never wedge the command that made it: a stall degrades to zero. The
 * resolve runs through {@link PlaceholderApiSupport}, so no {@code me.clip.placeholderapi} type is named here and a
 * server without PlaceholderAPI loads none of its classes; the placeholder must resolve to a bare number, since
 * {@link #balance} parses it with {@link BigDecimal}.
 */
public final class PlaceholderCurrencyBackend implements CurrencyBackend {

    /** A balance read must never wedge the command that made it; a stalled resolve degrades to zero after this. */
    private static final Duration BALANCE_TIMEOUT = Duration.ofSeconds(2);

    private final String id;
    private final String balancePlaceholder;
    private final String giveCommand;
    private final String takeCommand;
    private final boolean worksOffline;
    private final Precision precision;
    private final Server server;
    private final Logger log;
    private final Scheduler scheduler;
    private final BalanceReader reader;
    private final AtomicBoolean warned = new AtomicBoolean();

    /**
     * Resolves a currency's balance placeholder for one player. Production supplies the {@link PlaceholderApiSupport}
     * seam; a test supplies a fake so the positive path is covered without a live PlaceholderAPI.
     */
    @FunctionalInterface
    interface BalanceReader {
        String resolve(UUID player, String placeholder);
    }

    PlaceholderCurrencyBackend(
            String name,
            String balancePlaceholder,
            String giveCommand,
            String takeCommand,
            boolean worksOffline,
            Precision precision,
            Server server,
            Logger log,
            Scheduler scheduler,
            BalanceReader reader) {
        this.id = "placeholder:" + Objects.requireNonNull(name, "name");
        this.balancePlaceholder = Objects.requireNonNull(balancePlaceholder, "balancePlaceholder");
        this.giveCommand = Objects.requireNonNull(giveCommand, "giveCommand");
        this.takeCommand = Objects.requireNonNull(takeCommand, "takeCommand");
        this.worksOffline = worksOffline;
        this.precision = Objects.requireNonNull(precision, "precision");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.reader = Objects.requireNonNull(reader, "reader");
    }

    /**
     * Build the backend from {@code backends.placeholder.<name>}. Both command templates are validated at load, so an
     * operator who copies a template carrying neither {@code %amount%} nor {@code %price%} gets a startup error naming
     * the currency and the setting rather than a silent no-op at the first charge. {@code integral} defaults false, so
     * a money-like economy keeps its decimals unless the operator opts into whole units; {@code works-offline} defaults
     * false.
     */
    public static CurrencyBackend fromConfig(
            String name, ConfigStore config, Server server, Logger log, Scheduler scheduler) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(scheduler, "scheduler");
        String root = "backends.placeholder." + name;
        String balancePlaceholder = config.getString(root + ".balance-placeholder", "");
        String giveCommand = config.getString(root + ".give-command", "");
        String takeCommand = config.getString(root + ".take-command", "");
        validateCommand(name, "give-command", giveCommand);
        validateCommand(name, "take-command", takeCommand);
        Precision precision = config.getBoolean(root + ".integral", false) ? Precision.INTEGRAL : Precision.DECIMAL;
        boolean worksOffline = config.getBoolean(root + ".works-offline", false);
        return new PlaceholderCurrencyBackend(
                name,
                balancePlaceholder,
                giveCommand,
                takeCommand,
                worksOffline,
                precision,
                server,
                log,
                scheduler,
                (uuid, placeholder) -> PlaceholderApiSupport.messageBridge(uuid).apply(placeholder));
    }

    /** Substitute the player name and the amount; both {@code %amount%} and {@code %price%} are honoured. */
    static String renderCommand(String template, String playerName, String amount) {
        return template.replace("%player%", playerName)
                .replace("%amount%", amount)
                .replace("%price%", amount);
    }

    /** Reject at load a command that can never carry the amount, rather than no-oping at runtime. */
    static void validateCommand(String currency, String setting, String template) {
        if (!template.contains("%amount%") && !template.contains("%price%")) {
            throw new IllegalArgumentException(
                    "currency " + currency + ": " + setting + " must contain %amount% (or %price%); got: " + template);
        }
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean available() {
        return PlaceholderApiSupport.isPresent();
    }

    @Override
    public boolean worksOffline() {
        return worksOffline;
    }

    @Override
    public boolean atomicDebit() {
        return false;
    }

    @Override
    public Precision precision() {
        return precision;
    }

    @Override
    public Money balance(PlayerRef owner, Currency currency) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(currency, "currency");
        // The resolve is scheduled onto the global region, so the only thread that may run it inline is the one that
        // already owns it. On Folia a region tick thread is a tick thread but not the global one, and waiting on a
        // future it cannot complete would wedge that region.
        if (scheduler.onGlobalThread()) {
            return readBalance(owner, currency);
        }
        CompletableFuture<Money> resolved = new CompletableFuture<>();
        scheduler.onGlobal(() -> resolved.complete(readBalance(owner, currency)));
        try {
            return BoundedAwait.get(resolved, BALANCE_TIMEOUT, "placeholder balance " + id);
        } catch (IllegalStateException stalled) {
            return degrade(currency, "balance_timeout");
        }
    }

    /**
     * Resolve and parse the balance on the tick thread {@code PlaceholderAPI.setPlaceholders} requires. A resolve that
     * throws, or a value that will not parse as a number, degrades to zero with one log line rather than escaping into
     * the command that read it.
     */
    private Money readBalance(PlayerRef owner, Currency currency) {
        String text;
        try {
            text = reader.resolve(owner.uuid(), balancePlaceholder);
        } catch (RuntimeException failure) {
            return degrade(currency, "resolve_failed");
        }
        try {
            return Money.of(currency, new BigDecimal(text.trim()));
        } catch (NumberFormatException unparseable) {
            return degrade(currency, "unparseable_balance");
        }
    }

    /** Warn once for this backend so a persistently broken placeholder cannot spam the log, then read as zero. */
    private Money degrade(Currency currency, String reason) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=currency_backend_failed id={} reason=" + reason, id);
        }
        return Money.zero(currency);
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        dispatch(giveCommand, owner, amount);
        return Result.ok();
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        if (balance(owner, amount.currency()).isLessThan(amount)) {
            return Result.err(TransferError.INSUFFICIENT_FUNDS);
        }
        dispatch(takeCommand, owner, amount);
        return Result.ok();
    }

    @Override
    public List<BaltopRow> top(Currency currency, int limit) {
        Objects.requireNonNull(currency, "currency");
        return List.of();
    }

    private void dispatch(String template, PlayerRef owner, Money amount) {
        String value =
                ReflectiveCurrencyBackend.toBackendScale(amount, precision).toPlainString();
        String rendered = renderCommand(template, owner.name(), value);
        scheduler.onGlobal(() -> server.dispatchCommand(server.getConsoleSender(), rendered));
    }
}
