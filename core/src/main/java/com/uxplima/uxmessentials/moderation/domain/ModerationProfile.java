package com.uxplima.uxmessentials.moderation.domain;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A target's live sanction state, gathering the {@link MuteState mute}, {@link JailState jail} and
 * {@link TempbanState tempban} for one player into one aggregate. The repository rebuilds it from the
 * per-sanction tables, and the cross-context gates ask it the questions they need: the messaging context's
 * {@code MutePolicy} asks {@link #isMutedAt}, the teleport context's {@code JailGate} asks
 * {@link #isJailedAt}, and the login listener asks {@link #isTempbannedAt}.
 *
 * <p>The aggregate is a pure value: it holds no warning history (that is the append-only {@link Warn} log,
 * read separately) and no last-seen record (the {@link SeenRecord}); those are not part of the "is this
 * player sanctioned right now" question the gates ask. Every time check takes the instant explicitly so the
 * domain never reads the wall clock.
 *
 * @param target the player whose sanctions these are
 * @param mute the mute state (possibly {@link MuteState.None})
 * @param jail the jail state (possibly {@link JailState.None})
 * @param tempban the tempban state (possibly {@link TempbanState.None})
 */
public record ModerationProfile(PlayerRef target, MuteState mute, JailState jail, TempbanState tempban) {

    public ModerationProfile {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(mute, "mute");
        Objects.requireNonNull(jail, "jail");
        Objects.requireNonNull(tempban, "tempban");
    }

    /** A clean profile, no mute, no jail, no tempban. */
    public static ModerationProfile clean(PlayerRef target) {
        return new ModerationProfile(target, MuteState.none(), JailState.none(), TempbanState.none());
    }

    /** True when the target may not send outbound messaging at {@code now} (the {@code MutePolicy} question). */
    public boolean isMutedAt(Instant now) {
        return mute.isActiveAt(now);
    }

    /** True when the target is held in a jail at {@code now} (the {@code JailGate} question). */
    public boolean isJailedAt(Instant now) {
        return jail.isActiveAt(now);
    }

    /** True when the target's login is barred by an active tempban at {@code now}. */
    public boolean isTempbannedAt(Instant now) {
        return tempban.isActiveAt(now);
    }

    /** This profile with a replaced mute, leaving jail and tempban untouched. */
    public ModerationProfile withMute(MuteState replacement) {
        return new ModerationProfile(target, Objects.requireNonNull(replacement, "replacement"), jail, tempban);
    }

    /** This profile with a replaced jail, leaving mute and tempban untouched. */
    public ModerationProfile withJail(JailState replacement) {
        return new ModerationProfile(target, mute, Objects.requireNonNull(replacement, "replacement"), tempban);
    }

    /** This profile with a replaced tempban, leaving mute and jail untouched. */
    public ModerationProfile withTempban(TempbanState replacement) {
        return new ModerationProfile(target, mute, jail, Objects.requireNonNull(replacement, "replacement"));
    }
}
