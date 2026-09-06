package com.uxplima.uxmessentials.messaging.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * One player's last private-message partner and when the conversation was last touched, the target a
 * {@code /reply} resolves to. It is transient session state (a private message is real-time, never
 * persisted), captured both when the player sends a {@code /msg} and when they receive one, so either side
 * can {@code /reply} without naming the other.
 *
 * <p>The aggregate owns the reply-TTL rule: a {@code /reply} only resolves while the conversation is fresh.
 * {@link #isFresh} compares {@code now} against {@link #lastTouched} plus the configured time-to-live, so a
 * stale conversation (the partner long gone, the window elapsed) declines to misdirect a reply to whoever
 * the player last spoke to hours ago. A non-positive TTL is treated as "never expires" so an operator can
 * switch the bound off.
 *
 * @param partner the other side of the conversation a {@code /reply} addresses
 * @param lastTouched the instant the conversation was last sent to or received from
 */
public record LastConversation(PlayerRef partner, Instant lastTouched) {

    public LastConversation {
        Objects.requireNonNull(partner, "partner");
        Objects.requireNonNull(lastTouched, "lastTouched");
    }

    /** A conversation with {@code partner} touched at {@code at}. */
    public static LastConversation with(PlayerRef partner, Instant at) {
        return new LastConversation(partner, at);
    }

    /** A copy re-stamped to {@code at}, keeping the same partner: the conversation continued. */
    public LastConversation touchedAt(Instant at) {
        return new LastConversation(partner, Objects.requireNonNull(at, "at"));
    }

    /**
     * Whether a {@code /reply} at {@code now} still resolves to this partner under {@code ttl}. A non-positive
     * {@code ttl} never expires; otherwise the conversation is fresh until {@code lastTouched + ttl}.
     */
    public boolean isFresh(Instant now, Duration ttl) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            return true;
        }
        return !now.isAfter(lastTouched.plus(ttl));
    }
}
