package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import com.uxplima.uxmessentials.economy.application.AmountFormat;
import com.uxplima.uxmessentials.economy.application.MoneyFormat;
import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@link EconomyPlaceholders} over the economy context's resolved {@link EconomyProvider} and the
 * operator-selected {@link AmountFormat}. The same provider every {@code /balance} reads is queried here, so
 * the placeholder balance matches the command's: served from the offline read-cache the provider fronts.
 *
 * <p>The formatted value uses the v2.1 compact format when the operator selected {@code economy.amount-format
 * = compact}, otherwise the full grouped figure, through the shared {@link MoneyFormat}; the compact
 * placeholder always renders the abbreviated form regardless of that setting. The baltop reads the ranked
 * snapshot bounded by a fixed cap (a placeholder that walks an unbounded leaderboard would be a footgun)
 * and the position/row resolve empty for anything outside that window (exempt or simply unranked). A
 * currency-bearing placeholder resolves its {@link Currency} from the provider's configured set and degrades
 * when the id names no configured currency.
 */
@NullMarked
public final class ProviderEconomyPlaceholders implements EconomyPlaceholders {

    private final EconomyProvider provider;
    private final Currency defaultCurrency;
    private final AmountFormat amountFormat;
    private final int baltopScanLimit;

    public ProviderEconomyPlaceholders(
            EconomyProvider provider, Currency defaultCurrency, AmountFormat amountFormat, int baltopScanLimit) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.defaultCurrency = Objects.requireNonNull(defaultCurrency, "defaultCurrency");
        this.amountFormat = Objects.requireNonNull(amountFormat, "amountFormat");
        if (baltopScanLimit <= 0) {
            throw new IllegalArgumentException("baltopScanLimit must be positive: " + baltopScanLimit);
        }
        this.baltopScanLimit = baltopScanLimit;
    }

    @Override
    public Money balance(PlayerRef who) {
        return provider.balance(Objects.requireNonNull(who, "who"), defaultCurrency);
    }

    @Override
    public String formatted(PlayerRef who) {
        return MoneyFormat.withSymbol(balance(who), amountFormat);
    }

    @Override
    public String compact(PlayerRef who) {
        return MoneyFormat.withSymbol(balance(who), AmountFormat.COMPACT);
    }

    @Override
    public OptionalInt baltopPosition(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        List<BaltopRow> ranked = provider.top(defaultCurrency, baltopScanLimit);
        for (int i = 0; i < ranked.size(); i++) {
            if (ranked.get(i).owner().equals(who)) {
                return OptionalInt.of(i + 1);
            }
        }
        return OptionalInt.empty();
    }

    @Override
    public Optional<Currency> currency(String currencyId) {
        Objects.requireNonNull(currencyId, "currencyId");
        return provider.currencies().stream()
                .filter(c -> c.id().value().equalsIgnoreCase(currencyId))
                .findFirst();
    }

    @Override
    public Currency defaultCurrency() {
        return defaultCurrency;
    }

    @Override
    public Money balance(PlayerRef who, Currency currency) {
        Objects.requireNonNull(who, "who");
        return provider.balance(who, Objects.requireNonNull(currency, "currency"));
    }

    @Override
    public Optional<BaltopRow> baltopRow(Currency currency, int rank) {
        Objects.requireNonNull(currency, "currency");
        if (rank < 1 || rank > baltopScanLimit) {
            return Optional.empty();
        }
        List<BaltopRow> ranked = provider.top(currency, baltopScanLimit);
        return rank <= ranked.size() ? Optional.of(ranked.get(rank - 1)) : Optional.empty();
    }
}
