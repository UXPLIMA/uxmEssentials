package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.domain.PunishmentReport;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.moderation.domain.StaffPunishmentCount;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /modstats [staff] [days]}: render staff punishment analytics. The server-wide view scans the recent
 * history across every actor and renders a most-active-staff leaderboard; the per-staff view scans one staff
 * member's actions and renders their breakdown. Both fold an optional {@code days} window through the injected
 * {@link Clock}, hand the fetched rows to the pure {@link PunishmentStats} aggregation, and render the result
 * through the {@link Notifier}. The read is bounded ({@link #SCAN_LIMIT}) and the command runs it off
 * the tick thread.
 *
 * <p>The all-time and windowed renderings use distinct catalog keys so the time frame is phrased in the catalog
 * rather than interpolated as a Java literal; an empty result renders the matching empty notice.
 */
public final class ReviewPunishmentStats {

    /** The bounded scan the server-wide and per-staff reads cap at, so the analytics query stays within budget. */
    static final int SCAN_LIMIT = 1000;

    private final SanctionHistory history;
    private final PunishmentStats stats;
    private final Notifier notifier;
    private final Clock clock;

    public ReviewPunishmentStats(SanctionHistory history, PunishmentStats stats, Notifier notifier, Clock clock) {
        this.history = Objects.requireNonNull(history, "history");
        this.stats = Objects.requireNonNull(stats, "stats");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Render the server-wide leaderboard to {@code viewer}, optionally scoped to the last {@code days} days. */
    public void showServerWide(PlayerRef viewer, OptionalInt days) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(days, "days");
        Optional<Instant> since = window(days);
        List<SanctionHistoryEntry> rows = history.allSince(since.orElse(Instant.EPOCH), SCAN_LIMIT);
        renderServerWide(viewer, stats.aggregate(rows, since), days);
    }

    /** Render {@code staff}'s own punishment breakdown to {@code viewer}, optionally over the last {@code days}. */
    public void showForStaff(PlayerRef viewer, PlayerRef staff, OptionalInt days) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(days, "days");
        Optional<Instant> since = window(days);
        List<SanctionHistoryEntry> rows = history.recentByActor(staff.uuid(), SCAN_LIMIT);
        renderForStaff(viewer, staff, stats.aggregate(rows, since), days);
    }

    private Optional<Instant> window(OptionalInt days) {
        return days.isPresent()
                ? Optional.of(clock.instant().minus(Duration.ofDays(days.getAsInt())))
                : Optional.empty();
    }

    private void renderServerWide(PlayerRef viewer, PunishmentReport report, OptionalInt days) {
        if (report.isEmpty()) {
            notifier.send(viewer, ModerationMessageKey.MOD_STATS_EMPTY);
            return;
        }
        ModerationMessageKey header =
                days.isPresent() ? ModerationMessageKey.MOD_STATS_HEADER_WINDOW : ModerationMessageKey.MOD_STATS_HEADER;
        notifier.send(viewer, header, headerPlaceholders(report, days));
        int rank = 1;
        for (StaffPunishmentCount row : report.leaderboard()) {
            notifier.send(viewer, ModerationMessageKey.MOD_STATS_ENTRY, entryPlaceholders(rank++, row));
        }
    }

    private void renderForStaff(PlayerRef viewer, PlayerRef staff, PunishmentReport report, OptionalInt days) {
        if (report.isEmpty()) {
            notifier.send(viewer, ModerationMessageKey.MOD_STATS_STAFF_EMPTY, Map.of("staff", staff.name()));
            return;
        }
        ModerationMessageKey key =
                days.isPresent() ? ModerationMessageKey.MOD_STATS_STAFF_WINDOW : ModerationMessageKey.MOD_STATS_STAFF;
        notifier.send(viewer, key, staffPlaceholders(staff, report.leaderboard().get(0), days));
    }

    private static Map<String, String> headerPlaceholders(PunishmentReport report, OptionalInt days) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("total", Integer.toString(report.total()));
        placeholders.put("bans", Integer.toString(report.totalBans()));
        placeholders.put("mutes", Integer.toString(report.totalMutes()));
        placeholders.put("warns", Integer.toString(report.totalWarns()));
        placeholders.put("kicks", Integer.toString(report.totalKicks()));
        placeholders.put("staffcount", Integer.toString(report.leaderboard().size()));
        days.ifPresent(value -> placeholders.put("days", Integer.toString(value)));
        return placeholders;
    }

    private static Map<String, String> entryPlaceholders(int rank, StaffPunishmentCount row) {
        return Map.of(
                "rank", Integer.toString(rank),
                "staff", row.staffName(),
                "bans", Integer.toString(row.bans()),
                "mutes", Integer.toString(row.mutes()),
                "warns", Integer.toString(row.warns()),
                "kicks", Integer.toString(row.kicks()),
                "total", Integer.toString(row.total()));
    }

    private static Map<String, String> staffPlaceholders(PlayerRef staff, StaffPunishmentCount row, OptionalInt days) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("staff", staff.name());
        placeholders.put("bans", Integer.toString(row.bans()));
        placeholders.put("mutes", Integer.toString(row.mutes()));
        placeholders.put("warns", Integer.toString(row.warns()));
        placeholders.put("kicks", Integer.toString(row.kicks()));
        placeholders.put("total", Integer.toString(row.total()));
        days.ifPresent(value -> placeholders.put("days", Integer.toString(value)));
        return placeholders;
    }
}
