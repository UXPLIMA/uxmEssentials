package com.uxplima.uxmessentials.presence.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The immutable per-player presence the presence context owns: the AFK flag with its optional reason, the
 * instant of the player's last activity, and the vanish flag. Held in a
 * {@code ConcurrentHashMap<UUID, PlayerPresence>} and only ever replaced wholesale through {@code compute}
 * every transition here returns a new aggregate rather than mutating this one, so a reader on another region
 * thread (or the async AFK sweep) always sees a coherent value (docs/02-concurrency.md §6.9).
 *
 * <p>The aggregate owns two transition rules. The AFK auto-transition: {@link #isIdlePast} compares {@code now}
 * against {@link #lastActivity} plus the configured idle threshold, and the async sweep flips an idle,
 * not-yet-AFK player to AFK through {@link #markAfk}. Any activity (a move, a chat line, a command)
 * re-stamps {@link #lastActivity} through {@link #cleared}, which also clears an auto-AFK flag, returning
 * from AFK on the first sign of life. Manual {@code /afk} toggles directly through {@link #markAfk} (with the
 * player's chosen reason) and {@link #cleared}.
 *
 * <p>Vanish is independent of AFK: {@link #vanish} / {@link #unvanish} flip only the vanished flag, leaving the
 * AFK state untouched, because a staff member may be both vanished and AFK at once.
 *
 * @param afk whether the player is currently AFK (auto or manual)
 * @param afkReason the reason shown for a manual {@code /afk}, or empty for auto-AFK and when not AFK
 * @param lastActivity the instant of the player's last recorded activity, the auto-AFK clock reads this
 * @param vanished whether the player is vanished ({@code /vanish})
 */
public record PlayerPresence(boolean afk, Optional<String> afkReason, Instant lastActivity, boolean vanished) {

    public PlayerPresence {
        Objects.requireNonNull(afkReason, "afkReason");
        Objects.requireNonNull(lastActivity, "lastActivity");
    }

    /** The neutral starting state: not AFK, no reason, active as of {@code now}, not vanished. */
    public static PlayerPresence active(Instant now) {
        return new PlayerPresence(false, Optional.empty(), Objects.requireNonNull(now, "now"), false);
    }

    /**
     * This presence re-stamped to {@code now} and cleared of any AFK flag. The player did something, so they
     * are no longer idle and no longer AFK. The vanished flag is preserved: activity does not reveal a vanished
     * player. Callers compare {@link #afk()} before and after to decide whether a return-from-AFK occurred.
     */
    public PlayerPresence cleared(Instant now) {
        Objects.requireNonNull(now, "now");
        return new PlayerPresence(false, Optional.empty(), now, vanished);
    }

    /** This presence re-stamped to {@code now}, keeping AFK and vanish as they are: used when only the clock moves. */
    public PlayerPresence touched(Instant now) {
        Objects.requireNonNull(now, "now");
        return new PlayerPresence(afk, afkReason, now, vanished);
    }

    /** This presence flipped to AFK with an optional {@code reason}; activity stamp and vanish are preserved. */
    public PlayerPresence markAfk(Optional<String> reason) {
        Objects.requireNonNull(reason, "reason");
        return new PlayerPresence(true, reason, lastActivity, vanished);
    }

    /** This presence with the vanished flag set; AFK state untouched. */
    public PlayerPresence vanish() {
        return new PlayerPresence(afk, afkReason, lastActivity, true);
    }

    /** This presence with the vanished flag cleared; AFK state untouched. */
    public PlayerPresence unvanish() {
        return new PlayerPresence(afk, afkReason, lastActivity, false);
    }

    /**
     * Whether the player has been idle longer than {@code threshold} as of {@code now}, the auto-AFK rule.
     * A non-positive {@code threshold} disables auto-AFK (never idle), so an operator can switch it off by
     * setting the idle window to zero. An already-AFK player is never re-flagged.
     */
    public boolean isIdlePast(Instant now, Duration threshold) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(threshold, "threshold");
        if (afk || threshold.isZero() || threshold.isNegative()) {
            return false;
        }
        return now.isAfter(lastActivity.plus(threshold));
    }
}
