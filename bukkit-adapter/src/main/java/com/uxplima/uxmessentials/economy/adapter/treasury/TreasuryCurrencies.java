package com.uxplima.uxmessentials.economy.adapter.treasury;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import org.jspecify.annotations.NullMarked;

/**
 * Maps between this plugin's {@link Currency} and Treasury's currency identifiers at the adapter boundary, so
 * nothing above {@link TreasuryEconomyAdapter} sees a Treasury type ({@code docs/11-economy-integration.md}
 * §5, §6). A configured {@link Currency} is matched to a Treasury currency by id; only currencies the
 * foreign provider can actually serve are reported by {@link #served()}, so a {@code transfer} in a currency
 * Treasury does not hold resolves to {@code CURRENCY_UNSUPPORTED} rather than silently converting: there is
 * no implicit cross-currency conversion anywhere in this plugin.
 */
@NullMarked
final class TreasuryCurrencies {

    private final me.lokka30.treasury.api.economy.EconomyProvider treasury;
    private final CurrencyRegistry registry;

    TreasuryCurrencies(me.lokka30.treasury.api.economy.EconomyProvider treasury, CurrencyRegistry registry) {
        this.treasury = Objects.requireNonNull(treasury, "treasury");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /** The Treasury currency matching {@code currency}'s id, or empty when the foreign provider cannot serve it. */
    Optional<me.lokka30.treasury.api.economy.currency.Currency> toTreasury(Currency currency) {
        Objects.requireNonNull(currency, "currency");
        return treasury.findCurrency(currency.id().value());
    }

    /** The configured currencies the foreign Treasury provider can actually hold (intersection of both sets). */
    Set<Currency> served() {
        Set<Currency> served = new LinkedHashSet<>();
        for (CurrencyId id : registry.ids()) {
            registry.find(id).ifPresent(currency -> {
                if (toTreasury(currency).isPresent()) {
                    served.add(currency);
                }
            });
        }
        return served.isEmpty() ? Set.of(registry.defaultCurrency()) : served;
    }
}
