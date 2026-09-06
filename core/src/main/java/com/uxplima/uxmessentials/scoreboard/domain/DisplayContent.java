package com.uxplima.uxmessentials.scoreboard.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The operator-authored content of the per-player scoreboard sidebar: the sidebar title and lines, the refresh
 * cadence the render timer reads each cycle, and the set of world names where the sidebar is suppressed. Every string
 * is raw MiniMessage source the adapter renders per viewer through the placeholder pipeline; the domain never parses
 * or localises them: it only guards the structural invariants.
 *
 * <p>The authored catalog may contain more than the vanilla {@link #MAX_LINES} visible rows because conditions and
 * empty-value filtering are evaluated per viewer. The renderer applies the visible limit after those filters. The
 * refresh interval must be strictly positive; a zero or negative cadence would busy-spin the render timer.
 *
 * <p>{@code hideScoreNumbers} hides the red per-line score numbers vanilla draws down the right edge of the sidebar
 * (the adapter applies a blank number format to each line unless that line overrides it). It defaults on, the clean, modern look operators
 * expect, and is purely a render concern, not a structural one, so it never affects {@link #isBlank()}.
 *
 */
public final class DisplayContent {

    /** The maximum number of sidebar lines a vanilla scoreboard can show; mirrors uxmLib {@code Sidebar.MAX_LINES}. */
    public static final int MAX_LINES = 15;

    private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(1L);

    /** Defensive ceiling for authored candidates; the frame planner applies the vanilla 15 visible-line limit later. */
    public static final int MAX_CANDIDATE_LINES = 128;

    private final Optional<String> title;
    private final List<SidebarLine> lineDefinitions;
    private final boolean hideScoreNumbers;
    private final Duration refreshInterval;
    private final Set<String> worldBlacklist;

    public DisplayContent(
            Optional<String> title,
            List<String> lines,
            boolean hideScoreNumbers,
            Duration refreshInterval,
            Set<String> worldBlacklist) {
        this(title, legacyLines(lines, hideScoreNumbers), hideScoreNumbers, refreshInterval, worldBlacklist, true);
    }

    private DisplayContent(
            Optional<String> title,
            List<SidebarLine> lines,
            boolean hideScoreNumbers,
            Duration refreshInterval,
            Set<String> worldBlacklist,
            boolean unused) {
        this.title = Objects.requireNonNull(title, "title");
        Objects.requireNonNull(lines, "lines");
        this.refreshInterval = Objects.requireNonNull(refreshInterval, "refreshInterval");
        Objects.requireNonNull(worldBlacklist, "worldBlacklist");
        if (lines.size() > MAX_CANDIDATE_LINES) {
            throw new IllegalArgumentException(
                    "a sidebar accepts at most " + MAX_CANDIDATE_LINES + " candidate lines, got " + lines.size());
        }
        if (refreshInterval.isZero() || refreshInterval.isNegative()) {
            throw new IllegalArgumentException("refresh interval must be positive, got " + refreshInterval);
        }
        long distinctIds = lines.stream().map(SidebarLine::id).distinct().count();
        if (distinctIds != lines.size()) {
            throw new IllegalArgumentException("sidebar line ids must be unique within a board");
        }
        this.lineDefinitions = List.copyOf(lines);
        this.hideScoreNumbers = hideScoreNumbers;
        this.worldBlacklist = Set.copyOf(worldBlacklist);
    }

    public static DisplayContent typed(
            Optional<String> title,
            List<SidebarLine> lines,
            boolean hideScoreNumbers,
            Duration refreshInterval,
            Set<String> worldBlacklist) {
        return new DisplayContent(title, lines, hideScoreNumbers, refreshInterval, worldBlacklist, true);
    }

    public Optional<String> title() {
        return title;
    }

    /** Back-compatible raw text view. New renderers should use {@link #lineDefinitions()}. */
    public List<String> lines() {
        return lineDefinitions.stream().map(SidebarLine::text).toList();
    }

    public List<SidebarLine> lineDefinitions() {
        return lineDefinitions;
    }

    public boolean hideScoreNumbers() {
        return hideScoreNumbers;
    }

    public Duration refreshInterval() {
        return refreshInterval;
    }

    public Set<String> worldBlacklist() {
        return worldBlacklist;
    }

    /**
     * The do-nothing default a freshly enabled, unauthored module renders: no title, no sidebar lines, suppressed
     * nowhere, refreshing once a second. An operator sees no visible change until they author content.
     */
    public static DisplayContent inert() {
        return new DisplayContent(Optional.empty(), List.of(), true, DEFAULT_INTERVAL, Set.of());
    }

    /** True when {@code worldName} is on the blacklist and the sidebar must be suppressed there. */
    public boolean suppressedIn(String worldName) {
        Objects.requireNonNull(worldName, "worldName");
        return worldBlacklist.stream().anyMatch(candidate -> candidate.equalsIgnoreCase(worldName));
    }

    /** True when nothing is configured to show: no title and no lines. */
    public boolean isBlank() {
        return title.isEmpty() && lineDefinitions.isEmpty();
    }

    private static List<SidebarLine> legacyLines(List<String> lines, boolean hideScoreNumbers) {
        Objects.requireNonNull(lines, "lines");
        SidebarNumberFormat format =
                hideScoreNumbers ? SidebarNumberFormat.blank() : SidebarNumberFormat.defaultFormat();
        java.util.ArrayList<SidebarLine> result = new java.util.ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            result.add(new SidebarLine(
                    "line-" + (i + 1),
                    Objects.requireNonNull(lines.get(i), "line"),
                    com.uxplima.uxmessentials.shared.display.DisplayCondition.always(),
                    format,
                    false));
        }
        return List.copyOf(result);
    }
}
