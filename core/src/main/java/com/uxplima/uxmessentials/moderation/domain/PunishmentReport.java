package com.uxplima.uxmessentials.moderation.domain;

import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * The aggregated punishment-analytics report: the per-staff {@link StaffPunishmentCount} leaderboard, ordered
 * most-active first, plus the server-wide totals derived from it. A server-wide report carries one row per
 * staff member who issued a punishment in the window; a per-staff report carries at most one row. An empty
 * leaderboard means no punishments fell in the window.
 *
 * <p>The totals are derived from the leaderboard so the report cannot disagree with itself, the server-wide
 * figures are exactly the column sums of the rows shown.
 *
 * @param leaderboard the per-staff rows, ordered by {@link StaffPunishmentCount#total} descending
 */
public record PunishmentReport(List<StaffPunishmentCount> leaderboard) {

    public PunishmentReport {
        leaderboard = List.copyOf(Objects.requireNonNull(leaderboard, "leaderboard"));
    }

    /** True when no punishments fell in the report window. */
    public boolean isEmpty() {
        return leaderboard.isEmpty();
    }

    /** The server-wide ban total: the sum of every row's ban count. */
    public int totalBans() {
        return sum(StaffPunishmentCount::bans);
    }

    /** The server-wide mute total: the sum of every row's mute count. */
    public int totalMutes() {
        return sum(StaffPunishmentCount::mutes);
    }

    /** The server-wide warn total: the sum of every row's warn count. */
    public int totalWarns() {
        return sum(StaffPunishmentCount::warns);
    }

    /** The server-wide kick total: the sum of every row's kick count. */
    public int totalKicks() {
        return sum(StaffPunishmentCount::kicks);
    }

    /** The server-wide punishment total across every staff member and every kind. */
    public int total() {
        return sum(StaffPunishmentCount::total);
    }

    private int sum(ToIntFunction<StaffPunishmentCount> field) {
        return leaderboard.stream().mapToInt(field).sum();
    }
}
