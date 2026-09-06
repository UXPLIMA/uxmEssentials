package com.uxplima.uxmessentials.moderation.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.PunishmentReport;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.moderation.domain.StaffPunishmentCount;

/**
 * Pure aggregation of the append-only sanction history into a {@link PunishmentReport}: it groups the fetched
 * rows by the staff member who issued them and tallies the four punitive actions (ban/mute/warn/kick) each
 * issued, over an optional {@code since} window, then orders the result into a most-active-staff leaderboard.
 * It reads no port and touches no clock. The caller fetches the (bounded) rows and supplies the window
 * threshold, so the maths here is a deterministic function of its inputs and is unit-tested against fixture
 * rows.
 *
 * <p>The lifts ({@link SanctionAction#UNBAN}, {@link SanctionAction#UNMUTE}) are reversals rather than
 * punishments, so they are never counted; a staff member who only lifted sanctions therefore does not appear
 * in the leaderboard. Grouping is by issuer identity, the UUID when present, else a console key derived from
 * the name, and each group's display name is taken from its most recent row, so a since-renamed staff member
 * shows their current name. The leaderboard orders by total punishments descending, breaking ties by name so
 * the ordering is stable.
 */
public final class PunishmentStats {

    /**
     * Aggregate {@code rows} into a per-staff leaderboard, counting only punitive rows applied at or after
     * {@code since} (or every punitive row when {@code since} is empty).
     */
    public PunishmentReport aggregate(List<SanctionHistoryEntry> rows, Optional<Instant> since) {
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(since, "since");
        Map<String, Accumulator> byStaff = new LinkedHashMap<>();
        for (SanctionHistoryEntry row : rows) {
            if (withinWindow(row, since) && isPunishment(row.action())) {
                accumulate(byStaff, row);
            }
        }
        return new PunishmentReport(leaderboard(byStaff));
    }

    private static boolean withinWindow(SanctionHistoryEntry row, Optional<Instant> since) {
        return since.map(threshold -> !row.at().isBefore(threshold)).orElse(true);
    }

    private static boolean isPunishment(SanctionAction action) {
        return switch (action) {
            case BAN, MUTE, WARN, KICK -> true;
            case UNBAN, UNMUTE -> false;
        };
    }

    private static void accumulate(Map<String, Accumulator> byStaff, SanctionHistoryEntry row) {
        Accumulator accumulator = byStaff.computeIfAbsent(identity(row.actor()), key -> new Accumulator(row.actor()));
        accumulator.observe(row.actor(), row.at());
        accumulator.count(row.action());
    }

    /** The grouping key: the issuer UUID when present, else a console key so console rows fold together by name. */
    private static String identity(Issuer actor) {
        return actor.uuid().map(UUID::toString).orElseGet(() -> "console:" + actor.name());
    }

    private static List<StaffPunishmentCount> leaderboard(Map<String, Accumulator> byStaff) {
        return byStaff.values().stream()
                .map(Accumulator::toCount)
                .sorted(Comparator.comparingInt(StaffPunishmentCount::total)
                        .reversed()
                        .thenComparing(count -> count.staffName().toLowerCase(Locale.ROOT))
                        .thenComparing(StaffPunishmentCount::staffName))
                .toList();
    }

    /** A mutable per-staff tally folded from the rows of one issuer, order-independent for the display name. */
    private static final class Accumulator {

        private final Optional<UUID> uuid;
        private String name;
        private Instant latestAt;
        private int bans;
        private int mutes;
        private int warns;
        private int kicks;

        Accumulator(Issuer issuer) {
            this.uuid = issuer.uuid();
            this.name = issuer.name();
            this.latestAt = Instant.MIN;
        }

        /** Adopt {@code issuer}'s name when {@code at} is the newest row seen, so a rename resolves to the latest. */
        void observe(Issuer issuer, Instant at) {
            if (at.isAfter(latestAt)) {
                this.name = issuer.name();
                this.latestAt = at;
            }
        }

        void count(SanctionAction action) {
            switch (action) {
                case BAN -> bans++;
                case MUTE -> mutes++;
                case WARN -> warns++;
                case KICK -> kicks++;
                case UNBAN, UNMUTE -> {
                    // Lifts are filtered before this method; the exhaustive switch keeps the compiler honest.
                }
            }
        }

        StaffPunishmentCount toCount() {
            return new StaffPunishmentCount(uuid, name, bans, mutes, warns, kicks);
        }
    }
}
