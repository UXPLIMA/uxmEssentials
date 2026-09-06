package com.uxplima.uxmessentials.migration.convert.essentialsx.parse;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.NullMarked;

/**
 * The parsed moderation block of one EssentialsX {@code userdata/<uuid>.yml}: the optional mute and the
 * optional per-player jail sentence. Both are absent on a clean player, so a userdata file with no
 * sanctions parses to {@link #none()} and contributes no moderation record (docs/12-migration §5.1).
 * Source-shaped foreign detail; the mapper turns it into a {@code ModerationProfile}.
 *
 * @param mute the mute, empty when the player is not muted
 * @param jail the jail sentence, empty when the player is not jailed
 */
@NullMarked
public record EssXModeration(Optional<EssXMute> mute, Optional<EssXJail> jail) {

    private static final EssXModeration NONE = new EssXModeration(Optional.empty(), Optional.empty());

    public EssXModeration {
        Objects.requireNonNull(mute, "mute");
        Objects.requireNonNull(jail, "jail");
    }

    /** The clean block, no mute, no jail. */
    public static EssXModeration none() {
        return NONE;
    }

    /** True when this player carries neither a mute nor a jail, so no moderation record is emitted. */
    public boolean isEmpty() {
        return mute.isEmpty() && jail.isEmpty();
    }
}
