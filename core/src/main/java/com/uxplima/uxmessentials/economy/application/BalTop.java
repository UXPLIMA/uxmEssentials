package com.uxplima.uxmessentials.economy.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /baltop [currency] [page]}: render the ranked leaderboard for one currency. The ordering and
 * exemption are the provider/snapshot's concern ({@code docs/11-economy-integration.md} §11), rows arrive
 * already sorted descending by balance and with exempt owners filtered out, so this use case pages the
 * result and renders a header, one row per entry, or an empty notice. The query is budgeted and off-tick at
 * the adapter; this use case only formats what it is handed.
 */
public final class BalTop {

    /** A defensive ceiling so a single page can never ask the provider for an unbounded scan. */
    public static final int MAX_PAGE_SIZE = 100;

    private final EconomyProvider economy;
    private final EconomyNotifier notifier;
    private final int pageSize;

    public BalTop(EconomyProvider economy, EconomyNotifier notifier, int pageSize) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("baltop page size must be in 1.." + MAX_PAGE_SIZE + ": " + pageSize);
        }
        this.pageSize = pageSize;
    }

    /** Show {@code viewer} page {@code page} (1-based) of the {@code currency} leaderboard. */
    public List<BaltopRow> show(PlayerRef viewer, Currency currency, int page) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(currency, "currency");
        int requested = Math.max(1, page);
        List<BaltopRow> ranked = economy.top(currency, requested * pageSize);
        List<BaltopRow> pageRows = paginate(ranked, requested);
        render(viewer, currency, requested, pageRows);
        return pageRows;
    }

    private List<BaltopRow> paginate(List<BaltopRow> ranked, int page) {
        int from = Math.min((page - 1) * pageSize, ranked.size());
        int to = Math.min(from + pageSize, ranked.size());
        return ranked.subList(from, to);
    }

    private void render(PlayerRef viewer, Currency currency, int page, List<BaltopRow> rows) {
        if (rows.isEmpty()) {
            notifier.send(viewer, EconomyMessageKey.BALTOP_EMPTY);
            return;
        }
        notifier.send(
                viewer,
                EconomyMessageKey.BALTOP_HEADER,
                Map.of("currency", currency.plural(), "page", Integer.toString(page)));
        int rank = (page - 1) * pageSize + 1;
        for (BaltopRow row : rows) {
            renderRow(viewer, rank++, row);
        }
    }

    private void renderRow(PlayerRef viewer, int rank, BaltopRow row) {
        notifier.send(
                viewer,
                EconomyMessageKey.BALTOP_ROW,
                Map.of(
                        "rank", Integer.toString(rank),
                        "player", row.owner().name(),
                        "amount", notifier.amount(row.balance())));
    }
}
