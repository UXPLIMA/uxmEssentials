package com.uxplima.uxmessentials.moderation.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One staff member's punishment tallies over a report window. The per-staff row a punishment-stats report
 * renders and the unit a most-active-staff leaderboard is ordered by. It counts the four punitive actions a
 * staff member issued ({@link SanctionAction#BAN}, {@link SanctionAction#MUTE}, {@link SanctionAction#WARN},
 * {@link SanctionAction#KICK}); the lifts ({@link SanctionAction#UNBAN}, {@link SanctionAction#UNMUTE}) are
 * reversals, not punishments, so they are never tallied here.
 *
 * <p>Identity follows the {@link Issuer} shape: a live staff member has a present {@link #staff} UUID, a
 * console/system actor an empty one but always a {@link #staffName} (captured at issue time). Every count is
 * non-negative; {@link #total} is the sum the leaderboard sorts on.
 *
 * @param staff the issuing staff member's UUID, or empty for a console/system actor
 * @param staffName the name the punishments were issued under (never blank)
 * @param bans the number of bans (permanent, timed and IP) issued in the window
 * @param mutes the number of mutes (permanent and timed) issued in the window
 * @param warns the number of warnings (standing and timed) issued in the window
 * @param kicks the number of kicks issued in the window
 */
public record StaffPunishmentCount(Optional<UUID> staff, String staffName, int bans, int mutes, int warns, int kicks) {

    public StaffPunishmentCount {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(staffName, "staffName");
        if (staffName.isBlank()) {
            throw new IllegalArgumentException("staffName must not be blank");
        }
        requireNonNegative(bans, "bans");
        requireNonNegative(mutes, "mutes");
        requireNonNegative(warns, "warns");
        requireNonNegative(kicks, "kicks");
    }

    /** The staff member's total punishments in the window: the leaderboard's ordering key. */
    public int total() {
        return bans + mutes + warns + kicks;
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
