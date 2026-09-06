package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Optional;
import java.util.OptionalInt;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code balance}, {@code balance_formatted}, {@code
 * balance_compact}, {@code baltop_position}, the per-currency {@code balance_<currency>} family, the indexed
 * {@code baltop_<n>_*} rows, and the default-currency {@code currency_name}/{@code currency_symbol}
 * placeholders. It is an adapter over the economy context's resolved {@code EconomyProvider} (balance and
 * baltop read-model) plus the operator-selected {@code AmountFormat} (the v2.1 compact format), all wired
 * during bootstrap; when the economy module is disabled the seam is absent and the placeholders degrade.
 *
 * <p>The no-currency methods read the configured default currency. The placeholder surface that carries no
 * currency argument. The currency-bearing methods resolve a currency by its id so a multi-currency server can
 * surface a balance or leaderboard for any configured currency. Every balance is served from the offline
 * read-cache the provider sits in front of, so an offline player's balance still resolves.
 */
public interface EconomyPlaceholders {

    /** {@code who}'s balance in the default currency. */
    Money balance(PlayerRef who);

    /** {@code who}'s balance rendered with the operator-selected amount format (full or compact). */
    String formatted(PlayerRef who);

    /** {@code who}'s balance in the default currency rendered in the compact form ({@code 1.2K}/{@code 1.2M}). */
    String compact(PlayerRef who);

    /** {@code who}'s 1-based rank on the default-currency leaderboard, or empty when unranked/exempt. */
    OptionalInt baltopPosition(PlayerRef who);

    /** The configured currency with id {@code currencyId}, or empty when no such currency is configured. */
    Optional<Currency> currency(String currencyId);

    /** The default currency the no-argument placeholders read. */
    Currency defaultCurrency();

    /** {@code who}'s balance in {@code currency}. */
    Money balance(PlayerRef who, Currency currency);

    /**
     * The {@code rank}-th (1-based) row of {@code currency}'s leaderboard, or empty when {@code rank} is out of
     * range. Served from the same bounded ranked snapshot {@code /baltop} reads.
     */
    Optional<BaltopRow> baltopRow(Currency currency, int rank);
}
