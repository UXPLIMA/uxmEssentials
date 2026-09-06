package com.uxplima.uxmessentials.vote.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The cooldown state for one player on one vote site: the site name, the last time they voted
 * there (if ever), and the configured cooldown window. Pure value, no I/O.
 *
 * @param site        the site key this cooldown belongs to
 * @param lastVoteAt  when the player last voted on this site, or empty if they never have
 * @param cooldown    the configured per-site cooldown; zero means always votable
 */
public record SiteCooldown(String site, Optional<Instant> lastVoteAt, Duration cooldown) {

    public SiteCooldown {
        Objects.requireNonNull(site, "site");
        Objects.requireNonNull(lastVoteAt, "lastVoteAt");
        Objects.requireNonNull(cooldown, "cooldown");
    }

    /**
     * Whether the player can vote on this site right now. True when they have never voted here,
     * or when enough time has elapsed since their last vote (i.e. {@code now} is at or after
     * {@code lastVoteAt + cooldown}).
     */
    public boolean isVotable(Instant now) {
        Objects.requireNonNull(now, "now");
        if (lastVoteAt.isEmpty()) {
            return true;
        }
        Instant availableAt = lastVoteAt.get().plus(cooldown);
        return !now.isBefore(availableAt);
    }

    /**
     * How long remains until the player can vote on this site. Returns {@link Duration#ZERO} when
     * the site is already votable.
     */
    public Duration remaining(Instant now) {
        Objects.requireNonNull(now, "now");
        if (isVotable(now)) {
            return Duration.ZERO;
        }
        Instant availableAt = lastVoteAt.get().plus(cooldown);
        return Duration.between(now, availableAt);
    }
}
