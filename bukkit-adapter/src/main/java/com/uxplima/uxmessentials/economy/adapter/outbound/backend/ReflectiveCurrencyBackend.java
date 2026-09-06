package com.uxplima.uxmessentials.economy.adapter.outbound.backend;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Server;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Precision;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.Nullable;

/**
 * Shared scaffolding for the backends reached purely by reflection (PlayerPoints, CoinsEngine, zEssentials). A
 * subclass names the Bukkit plugin it integrates with and implements two reflective primitives, read a balance,
 * change a balance, while this base owns the load-safe contract around them: every call is gated by the
 * plugin-present guard, and any reflective failure (the API absent, or its shape shifted under a version bump) is
 * logged exactly once and degraded to {@link TransferError#CURRENCY_UNSUPPORTED} instead of propagating.
 *
 * <p>A subclass names the provider SDK only by string class-name through {@link Class#forName(String)} and
 * reflective lookups, so no field or method signature here carries an SDK type: constructing one of these on a
 * server without the plugin loads none of its classes, and {@link #available()} short-circuits before any
 * reflection runs. These backends can be written while the owner is offline, and none offers a guarded
 * compare-and-take, so {@link #atomicDebit()} is false and {@code SerialisingCurrencyBackend} wraps them.
 */
abstract class ReflectiveCurrencyBackend implements CurrencyBackend {

    private final String id;
    private final String pluginName;

    /** The sub-currency/economy name to act on, or {@code null} for a single-currency backend like PlayerPoints. */
    protected final @Nullable String currency;

    protected final Server server;
    private final Logger log;
    private final Precision precision;
    private final AtomicBoolean warned = new AtomicBoolean();

    ReflectiveCurrencyBackend(
            String id, String pluginName, @Nullable String currency, Server server, Logger log, Precision precision) {
        this.id = Objects.requireNonNull(id, "id");
        this.pluginName = Objects.requireNonNull(pluginName, "pluginName");
        this.currency = currency;
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
        this.precision = Objects.requireNonNull(precision, "precision");
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final boolean available() {
        return server.getPluginManager().isPluginEnabled(pluginName);
    }

    @Override
    public final boolean worksOffline() {
        return true;
    }

    @Override
    public final boolean atomicDebit() {
        return false;
    }

    @Override
    public final Precision precision() {
        return precision;
    }

    @Override
    public final Money balance(PlayerRef owner, Currency currency) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(currency, "currency");
        if (!available()) {
            return Money.zero(currency);
        }
        try {
            return Money.of(currency, BigDecimal.valueOf(readBalance(owner.uuid())));
        } catch (ReflectiveOperationException | NoClassDefFoundError failure) {
            degrade(failure);
            return Money.zero(currency);
        }
    }

    @Override
    public final Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        return change(owner, amount, true);
    }

    @Override
    public final Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        return change(owner, amount, false);
    }

    private Result<Unit, TransferError> change(PlayerRef owner, Money amount, boolean deposit) {
        if (!available()) {
            return Result.err(TransferError.CURRENCY_UNSUPPORTED);
        }
        try {
            if (changeBalance(owner.uuid(), toBackendScale(amount, precision), deposit)) {
                return Result.ok();
            }
            // A refused withdraw is the backend saying the owner cannot cover it; a refused deposit is the
            // backend declining to hold the amount at all, which the caller reads as an unsupported currency.
            return Result.err(deposit ? TransferError.CURRENCY_UNSUPPORTED : TransferError.INSUFFICIENT_FUNDS);
        } catch (ReflectiveOperationException | NoClassDefFoundError failure) {
            degrade(failure);
            return Result.err(TransferError.CURRENCY_UNSUPPORTED);
        }
    }

    @Override
    public final List<BaltopRow> top(Currency currency, int limit) {
        Objects.requireNonNull(currency, "currency");
        return List.of();
    }

    /** Read {@code player}'s balance in {@link #currency} reflectively; called only past the present-guard. */
    protected abstract double readBalance(UUID player) throws ReflectiveOperationException;

    /** Add (deposit) or remove (withdraw) {@code amount} reflectively; called only past the present-guard. */
    protected abstract boolean changeBalance(UUID player, BigDecimal amount, boolean deposit)
            throws ReflectiveOperationException;

    /** Log the first reflective failure for this backend; subsequent ones stay quiet so a version bump cannot spam. */
    private void degrade(Throwable failure) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=currency_backend_failed id={} reason={}", id(), failure.toString());
        }
    }

    /** Round once at the boundary: an integral backend takes whole units, a decimal one the currency's scale. */
    static BigDecimal toBackendScale(Money amount, Precision precision) {
        return precision == Precision.INTEGRAL ? amount.amount().setScale(0, RoundingMode.HALF_UP) : amount.amount();
    }
}
