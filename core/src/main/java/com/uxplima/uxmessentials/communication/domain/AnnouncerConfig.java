package com.uxplima.uxmessentials.communication.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * The rotating announcer's full configuration: the config-wide {@link #defaultInterval} between ticks, the
 * {@link #minOnlinePlayers} gate below which a tick is skipped (an empty or near-empty server is not spammed), the
 * {@link #ordering} that selects among announcements, and the operator-authored {@link #announcements} the cursor
 * rotates through. It holds a list of rich {@link Announcement}s, each with its own condition, channels, sound,
 * and optional interval override; the codec parses {@code announcer.conf} straight into it (a legacy flat
 * {@code lines = [ ... ]} block maps to a single default CHAT announcement).
 *
 * <p>The config is pure data, rebuilt atomically on reload (a new {@code AtomicReference<AnnouncerConfig>} swapped
 * in); the adapter's timer reads the current config each tick, so an interval or announcement change takes effect
 * on the next tick without re-scheduling. The {@code NextAnnouncement} use case reads it to decide whether a tick
 * fires and which announcement it picks, advancing an {@link AnnouncerCursor} across ticks. The per-announcement
 * {@link Announcement#intervalOverride() interval override} is an adapter scheduling concern, not consulted here.
 *
 * @param defaultInterval the wall-clock gap between announcer ticks for announcements without their own override;
 *     must be positive
 * @param minOnlinePlayers the minimum online count for a tick to fire; zero means "always fire"
 * @param ordering how a tick selects its announcement ({@link Ordering#RANDOM} pairs with no-immediate-repeat)
 * @param announcements the operator-authored announcements the cursor rotates through
 */
public record AnnouncerConfig(
        Duration defaultInterval, int minOnlinePlayers, Ordering ordering, List<Announcement> announcements) {

    public AnnouncerConfig {
        Objects.requireNonNull(defaultInterval, "defaultInterval");
        Objects.requireNonNull(ordering, "ordering");
        announcements = List.copyOf(Objects.requireNonNull(announcements, "announcements"));
        if (defaultInterval.isZero() || defaultInterval.isNegative()) {
            throw new IllegalArgumentException("announcer default interval must be positive: " + defaultInterval);
        }
        if (minOnlinePlayers < 0) {
            throw new IllegalArgumentException("minOnlinePlayers must not be negative: " + minOnlinePlayers);
        }
    }

    /**
     * An empty config with no announcements: the announcer never broadcasts. Used when the feature is configured
     * off. The interval is nominal (one minute) since no tick ever fires; it only satisfies the positive-interval
     * invariant.
     */
    public static AnnouncerConfig empty() {
        return new AnnouncerConfig(Duration.ofMinutes(1), 0, Ordering.SEQUENTIAL, List.of());
    }

    /** Whether the announcer has any announcement to broadcast at all. */
    public boolean hasAnnouncements() {
        return !announcements.isEmpty();
    }

    /** The number of announcements configured. */
    public int announcementCount() {
        return announcements.size();
    }

    /**
     * Whether a tick should fire given {@code onlinePlayers}: there is at least one announcement and the online
     * count meets the minimum-players gate. A gate of zero always passes.
     */
    public boolean shouldFire(int onlinePlayers) {
        if (onlinePlayers < 0) {
            throw new IllegalArgumentException("onlinePlayers must not be negative: " + onlinePlayers);
        }
        return hasAnnouncements() && onlinePlayers >= minOnlinePlayers;
    }

    /** The announcement at {@code index}; callers obtain {@code index} from the cursor advanced by the use case. */
    public Announcement announcementAt(int index) {
        Objects.checkIndex(index, announcements.size());
        return announcements.get(index);
    }

    /**
     * The same config restricted to the announcements that rotate on the default cadence. Those <em>without</em> an
     * {@link Announcement#intervalOverride() interval override}. The adapter drives the shared rotation cursor over
     * this view; each override announcement runs on its own independent timer outside the rotation, so it must not
     * also appear in the cursor's selection set. Preserves the interval, gate, and ordering.
     */
    public AnnouncerConfig rotating() {
        List<Announcement> withoutOverride = announcements.stream()
                .filter(announcement -> announcement.intervalOverride().isEmpty())
                .toList();
        return new AnnouncerConfig(defaultInterval, minOnlinePlayers, ordering, withoutOverride);
    }
}
