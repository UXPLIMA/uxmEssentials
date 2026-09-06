package com.uxplima.uxmessentials.economy.domain;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The closed set of currencies the economy serves, with one marked default. Built once when the economy
 * module starts from {@code economy.currencies.<id>} and never mutated. Currencies cannot be minted at
 * runtime (the economy GLOSSARY), so a lookup of an unknown id is an error a command surfaces, never a
 * silent fall-back to the default. The default is the currency every command resolves to when the optional
 * {@code [currency]} argument is omitted, and the one a legacy Vault provider degrades to.
 *
 * <p>A fresh install ships exactly one currency (the default); operators add more in config. The registry
 * keeps insertion order so {@code /baltop}-less listings and config round-trips stay stable.
 */
public final class CurrencyRegistry {

    private final Map<CurrencyId, Currency> byId;
    private final Currency defaultCurrency;

    private CurrencyRegistry(Map<CurrencyId, Currency> byId, Currency defaultCurrency) {
        this.byId = byId;
        this.defaultCurrency = defaultCurrency;
    }

    /**
     * Build a registry from the configured currencies and the chosen default. Rejects an empty set or a
     * default that is not among the configured currencies, so a misconfigured economy fails at start rather
     * than serving a half-formed currency set.
     */
    public static CurrencyRegistry of(Collection<Currency> currencies, CurrencyId defaultId) {
        Objects.requireNonNull(currencies, "currencies");
        Objects.requireNonNull(defaultId, "defaultId");
        if (currencies.isEmpty()) {
            throw new IllegalArgumentException("at least one currency must be configured");
        }
        Map<CurrencyId, Currency> byId = new LinkedHashMap<>();
        for (Currency currency : currencies) {
            byId.put(currency.id(), currency);
        }
        Currency resolved = byId.get(defaultId);
        if (resolved == null) {
            throw new IllegalArgumentException("default currency is not among the configured currencies: " + defaultId);
        }
        return new CurrencyRegistry(byId, resolved);
    }

    /** A single-currency registry: the out-of-the-box default, the path the worked example walks. */
    public static CurrencyRegistry single(Currency only) {
        Objects.requireNonNull(only, "only");
        return of(Set.of(only), only.id());
    }

    /** The currency every command resolves to when {@code [currency]} is omitted. */
    public Currency defaultCurrency() {
        return defaultCurrency;
    }

    /** The currency under {@code id}, or empty when no such currency is configured (an error, not a default). */
    public Optional<Currency> find(CurrencyId id) {
        return Optional.ofNullable(byId.get(Objects.requireNonNull(id, "id")));
    }

    /** Every configured currency, in declaration order. */
    public Set<Currency> all() {
        return Set.copyOf(byId.values());
    }

    /** The configured currency ids, for the {@code currency.unknown} message that lists the valid choices. */
    public Set<CurrencyId> ids() {
        return Set.copyOf(byId.keySet());
    }
}
