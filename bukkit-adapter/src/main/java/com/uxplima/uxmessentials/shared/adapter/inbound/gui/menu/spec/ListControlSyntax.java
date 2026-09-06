package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The grammar of the three list-control action refs a browse menu's bottom bar drives its paged list with:
 * {@code list-sort:<listId>[:direction]}, {@code list-filter:<listId>:<key>=<value>} and
 * {@code list-search:<listId>:<key>}. Each names the source id of the paged list it targets (a menu may hold more
 * than one), so the id is always explicit rather than inferred.
 *
 * <p>Pure and Bukkit-free, so it lives beside the rest of the spec grammar ({@link Ref#parse}) and is shared by the
 * three consumers that must read a ref identically: the action pack that dispatches a live click, the
 * {@code MenuBindings} validator that rejects a sort button wired to a list with no sorts at startup, and the tests
 * that pin the grammar. It parses only the ref's already-split value, the {@code list-sort:} / {@code list-filter:} /
 * {@code list-search:} head has been peeled off by the action registry before this sees it.
 *
 * <p>A list source id may itself carry colons ({@code pw:browse}), so a sort's optional trailing direction is
 * recognised by matching the last colon-delimited token against the three direction keywords rather than by counting
 * colons; a value whose real last segment happens to spell {@code next}/{@code prev}/{@code reset} is the one
 * inherent ambiguity of the compact form, so a source id is expected not to end that way.
 */
public final class ListControlSyntax {

    /** The action id a spec writes to advance, step back or reset a paged list's sort. */
    public static final String SORT_ACTION = "list-sort";

    /** The action id a spec writes to set or clear a paged list's filter. */
    public static final String FILTER_ACTION = "list-filter";

    /** The action id a spec writes to prompt for a line of text and store it as a paged list's filter. */
    public static final String SEARCH_ACTION = "list-search";

    private ListControlSyntax() {}

    /** Which way a {@code list-sort} step moves through the list's offered sorts. */
    public enum SortDirection {
        NEXT,
        PREVIOUS,
        RESET;

        /** The direction a trailing token names, or empty when it is not a direction keyword (so it is part of the id). */
        public static Optional<SortDirection> fromToken(String token) {
            Objects.requireNonNull(token, "token");
            return switch (token.strip().toLowerCase(Locale.ROOT)) {
                case "next" -> Optional.of(NEXT);
                case "prev", "previous" -> Optional.of(PREVIOUS);
                case "reset" -> Optional.of(RESET);
                default -> Optional.empty();
            };
        }
    }

    /** A parsed {@code list-sort} ref: the target list and the direction, defaulting to {@link SortDirection#NEXT}. */
    public record SortRef(String listId, SortDirection direction) {
        public SortRef {
            Objects.requireNonNull(listId, "listId");
            Objects.requireNonNull(direction, "direction");
        }
    }

    /** A parsed {@code list-filter} ref: the target list, the filter key, and the value (empty means clear). */
    public record FilterRef(String listId, String key, String value) {
        public FilterRef {
            Objects.requireNonNull(listId, "listId");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
        }
    }

    /** A parsed {@code list-search} ref: the target list and the filter key the typed line is stored under. */
    public record SearchRef(String listId, String key) {
        public SearchRef {
            Objects.requireNonNull(listId, "listId");
            Objects.requireNonNull(key, "key");
        }
    }

    /**
     * Parse a {@code list-sort} value ({@code <listId>} or {@code <listId>:<direction>}). Empty for a blank value or a
     * value that leaves no list id once a trailing direction is peeled off ({@code :prev}).
     */
    public static Optional<SortRef> parseSort(String value) {
        Objects.requireNonNull(value, "value");
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        int lastColon = trimmed.lastIndexOf(':');
        if (lastColon >= 0) {
            Optional<SortDirection> direction = SortDirection.fromToken(trimmed.substring(lastColon + 1));
            if (direction.isPresent()) {
                String listId = trimmed.substring(0, lastColon);
                return listId.isBlank() ? Optional.empty() : Optional.of(new SortRef(listId, direction.get()));
            }
        }
        return Optional.of(new SortRef(trimmed, SortDirection.NEXT));
    }

    /**
     * Parse a {@code list-filter} value ({@code <listId>:<key>=<value>}). The value is everything past the first
     * {@code =}, so a filter value may itself carry {@code :} or further {@code =}; an empty value is the clear signal.
     * The list id and key split on the last {@code :} before the {@code =}. Empty for a value missing the {@code =}, the
     * key separator, or leaving a blank list id or key.
     */
    public static Optional<FilterRef> parseFilter(String value) {
        Objects.requireNonNull(value, "value");
        String trimmed = value.strip();
        int equals = trimmed.indexOf('=');
        if (equals < 0) {
            return Optional.empty();
        }
        String left = trimmed.substring(0, equals);
        String filterValue = trimmed.substring(equals + 1);
        int lastColon = left.lastIndexOf(':');
        if (lastColon < 0) {
            return Optional.empty();
        }
        String listId = left.substring(0, lastColon);
        String key = left.substring(lastColon + 1);
        if (listId.isBlank() || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new FilterRef(listId, key, filterValue));
    }

    /**
     * Parse a {@code list-search} value ({@code <listId>:<key>}). The list id and key split on the last {@code :}.
     * Empty for a value with no {@code :} or one that leaves a blank list id or key.
     */
    public static Optional<SearchRef> parseSearch(String value) {
        Objects.requireNonNull(value, "value");
        String trimmed = value.strip();
        int lastColon = trimmed.lastIndexOf(':');
        if (lastColon < 0) {
            return Optional.empty();
        }
        String listId = trimmed.substring(0, lastColon);
        String key = trimmed.substring(lastColon + 1);
        if (listId.isBlank() || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new SearchRef(listId, key));
    }
}
