package com.uxplima.uxmessentials.communication.domain;

import java.util.List;
import java.util.Objects;

/**
 * One operator-authored info page: a command name ({@code rules}, {@code motd}, {@code info}) bound to the lines
 * shown when a player runs it. The lines are MiniMessage content the operator writes in {@code info-pages.conf}
 * (or an included text file) and are rendered, one Component per line, by the adapter. They are operator data,
 * never plugin {@code MessageKey}s, so they are not parity-checked.
 *
 * <p>The {@link #command} is the dynamic literal the module registers ({@code /rules} → this page). It is
 * normalised to lower case and validated as a single bare word so it forms a legal Brigadier literal; the
 * registry rejects two pages claiming the same command. A page may carry the {@code {player}} placeholder in its
 * lines, substituted per viewer before MiniMessage parses.
 *
 * <p>A page is paginated: {@link #pageSize} body lines are shown per page, so a long page is read with
 * {@code /<command> [page]} rather than flooding the chat. A page whose body fits in one page renders without
 * pagination chrome. The size is clamped to {@code 1..}{@value #MAX_PAGE_SIZE} so a misconfigured value can
 * never page zero lines or scan an unbounded slice.
 *
 * @param command the bare command literal (no slash) that opens this page, lower case
 * @param lines the page body, one MiniMessage template per line
 * @param pageSize the number of body lines shown per page
 */
public record InfoPage(String command, List<String> lines, int pageSize) {

    /** The page size applied when an operator does not author one. */
    public static final int DEFAULT_PAGE_SIZE = 8;

    /** A defensive ceiling so a single page can never render an unbounded slice. */
    public static final int MAX_PAGE_SIZE = 100;

    public InfoPage {
        Objects.requireNonNull(command, "command");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        String normalised = command.toLowerCase(java.util.Locale.ROOT);
        if (!normalised.matches("[a-z][a-z0-9_-]*")) {
            throw new IllegalArgumentException("info page command must be a single bare word: " + command);
        }
        command = normalised;
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("info page size must be in 1.." + MAX_PAGE_SIZE + ": " + pageSize);
        }
    }

    /** An info page named {@code command} with the given body lines and the default page size. */
    public static InfoPage of(String command, List<String> lines) {
        return new InfoPage(command, lines, DEFAULT_PAGE_SIZE);
    }

    /** An info page named {@code command} with the given body lines, paged {@code pageSize} lines at a time. */
    public static InfoPage of(String command, List<String> lines, int pageSize) {
        return new InfoPage(command, lines, pageSize);
    }

    /** The number of body lines on the page. */
    public int size() {
        return lines.size();
    }

    /** Whether the page has no body lines (a misconfigured or intentionally empty page). */
    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** The total number of pages the body spans at the configured {@link #pageSize}, at least one. */
    public int pageCount() {
        if (lines.isEmpty()) {
            return 1;
        }
        return (lines.size() + pageSize - 1) / pageSize;
    }
}
