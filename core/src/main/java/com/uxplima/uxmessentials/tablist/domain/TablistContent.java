package com.uxplima.uxmessentials.tablist.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The operator-authored content of the per-player tablist: the header and footer line lists, the refresh cadence the
 * render timer reads each cycle, and the set of world names where the tablist is suppressed. Every string is raw
 * MiniMessage source the adapter renders per viewer through the placeholder pipeline; the domain never parses or
 * localises them: it only guards the structural invariants.
 *
 * <p>The header and footer are line lists the adapter joins with newlines, so they carry no count bound of their own.
 * The refresh interval must be strictly positive: a zero or negative cadence would busy-spin the render timer.
 *
 * @param header the tablist header line sources, joined with newlines by the adapter
 * @param footer the tablist footer line sources, joined with newlines by the adapter
 * @param refreshInterval how often the render timer re-renders every viewer; strictly positive
 * @param worldBlacklist world names where the tablist is suppressed entirely
 */
public record TablistContent(
        List<String> header, List<String> footer, Duration refreshInterval, Set<String> worldBlacklist) {

    private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(1L);

    public TablistContent {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(footer, "footer");
        Objects.requireNonNull(refreshInterval, "refreshInterval");
        Objects.requireNonNull(worldBlacklist, "worldBlacklist");
        if (refreshInterval.isZero() || refreshInterval.isNegative()) {
            throw new IllegalArgumentException("refresh interval must be positive, got " + refreshInterval);
        }
        header = List.copyOf(header);
        footer = List.copyOf(footer);
        worldBlacklist = Set.copyOf(worldBlacklist);
    }

    /**
     * The do-nothing default a freshly enabled, unauthored module renders: no header, no footer, suppressed nowhere,
     * refreshing once a second. An operator sees no visible change until they author content.
     */
    public static TablistContent inert() {
        return new TablistContent(List.of(), List.of(), DEFAULT_INTERVAL, Set.of());
    }

    /** True when {@code worldName} is on the blacklist and the tablist must be suppressed there. */
    public boolean suppressedIn(String worldName) {
        Objects.requireNonNull(worldName, "worldName");
        return worldBlacklist.contains(worldName);
    }

    /** True when nothing is configured to show, no header and no footer. */
    public boolean isBlank() {
        return header.isEmpty() && footer.isEmpty();
    }
}
