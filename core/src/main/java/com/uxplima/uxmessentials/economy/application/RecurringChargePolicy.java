package com.uxplima.uxmessentials.economy.application;

import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackendRegistry;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;

/**
 * A scheduled bulk charge (player-warp rent, a subscription, a tax sweep) debits many owners with no player
 * watching. A human who clicks "buy" and is charged the wrong amount can see it and complain; a nightly sweep
 * cannot. On a backend whose take is not guarded, a debit that reports failure may in fact have succeeded, so
 * the next pass sees the money still owed and charges it a second time.
 *
 * <p>So a recurring charge against a currency whose backend cannot promise an atomic check-and-take is refused
 * at startup unless the operator sets {@code economy.allow-nonatomic-recurring = true} and accepts that risk.
 */
public final class RecurringChargePolicy {

    private RecurringChargePolicy() {}

    /**
     * Reject a recurring charge that would run against a non-atomic backend.
     *
     * @throws IllegalStateException naming the offending currency, its backend, and the setting that permits it
     */
    public static void validate(
            CurrencyRegistry currencies,
            CurrencyBackendRegistry backends,
            Set<CurrencyId> recurring,
            boolean allowNonAtomic) {
        Objects.requireNonNull(currencies, "currencies");
        Objects.requireNonNull(backends, "backends");
        Objects.requireNonNull(recurring, "recurring");
        if (allowNonAtomic) {
            return;
        }
        for (CurrencyId id : recurring) {
            Currency currency = currencies
                    .find(id)
                    .orElseThrow(
                            () -> new IllegalStateException("recurring charge names unknown currency " + id.value()));
            CurrencyBackend backend = backends.find(currency.backendId())
                    .orElseThrow(() -> new IllegalStateException(
                            "currency " + id.value() + " names unknown backend " + currency.backendId()));
            if (!backend.atomicDebit()) {
                throw new IllegalStateException("currency " + id.value() + " is held by backend '" + backend.id()
                        + "', whose debit is not guarded, so it may not carry a recurring charge. Move the charge to"
                        + " a native currency, or set economy.allow-nonatomic-recurring = true to accept the"
                        + " double-charge risk.");
            }
        }
    }
}
