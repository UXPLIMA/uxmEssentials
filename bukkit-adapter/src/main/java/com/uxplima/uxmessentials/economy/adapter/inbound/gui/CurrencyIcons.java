package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.util.Locale;
import java.util.Objects;

import org.bukkit.Material;

import com.uxplima.uxmessentials.economy.domain.Currency;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves the GUI icon {@link Material} for a {@link Currency} from its operator-configured
 * {@code icon-material} (in {@code currencies.conf}), falling back to the caller's default when none is set or
 * the configured name does not name a real material. No currency id is hardcoded here. The icon is data, so a
 * server that adds a {@code rubies} currency picks an icon in config rather than waiting on a code change.
 */
@NullMarked
final class CurrencyIcons {

    private CurrencyIcons() {}

    static Material materialFor(Currency currency, Material fallback) {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(fallback, "fallback");
        return currency.iconMaterial()
                .map(name -> Material.matchMaterial(name.toUpperCase(Locale.ROOT)))
                .filter(material -> material != null && !material.isAir())
                .orElse(fallback);
    }
}
