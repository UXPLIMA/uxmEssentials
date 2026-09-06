package com.uxplima.uxmessentials.communication.application;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.communication.domain.InfoPage;

/**
 * Pages an {@link InfoPage} for display: given the page and a requested 1-based page number, it clamps the request
 * into {@code 1..pageCount} and returns the {@link InfoPageView} slice the adapter renders. This is the pure half of
 * the data-driven info commands ({@code /rules}, {@code /motd}, {@code /info}, any operator-added page): it holds no
 * Bukkit, no MiniMessage, and no I/O, so it is unit-tested against plain values; the adapter binds the result to a
 * viewer and draws the header/footer/body.
 *
 * <p>Clamping is deliberate: a viewer who runs {@code /info 99} on a two-page document is served page two rather than
 * an error, mirroring how the economy {@code BalTop} use case clamps an over-large page request.
 */
public final class ShowInfoPage {

    private ShowInfoPage() {}

    /** The slice of {@code page} at 1-based {@code requestedPage}, clamped into {@code 1..pageCount}. */
    public static InfoPageView view(InfoPage page, int requestedPage) {
        Objects.requireNonNull(page, "page");
        int pageCount = page.pageCount();
        int resolved = clamp(requestedPage, pageCount);
        List<String> slice = slice(page, resolved);
        return new InfoPageView(page.command(), slice, resolved, pageCount);
    }

    private static int clamp(int requested, int pageCount) {
        if (requested < 1) {
            return 1;
        }
        return Math.min(requested, pageCount);
    }

    private static List<String> slice(InfoPage page, int resolvedPage) {
        List<String> lines = page.lines();
        if (lines.isEmpty()) {
            return List.of();
        }
        int from = (resolvedPage - 1) * page.pageSize();
        int to = Math.min(from + page.pageSize(), lines.size());
        return List.copyOf(lines.subList(from, to));
    }
}
