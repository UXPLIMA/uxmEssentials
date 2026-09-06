package com.uxplima.uxmessentials.migration.convert.essentialsx.parse;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.NullMarked;

/**
 * A parsed EssentialsX mute, source-shaped: the {@code muted} flag, the optional reason, and the
 * optional expiry epoch-millis ({@code timestamps.mute}, or the legacy top-level {@code muteTimeout}).
 * EssentialsX stores a permanent mute as {@code muted: true} with no expiry (or an expiry of {@code 0});
 * a timed mute carries a future epoch-millis. This is foreign detail. The mapper turns it into a
 * {@code MuteState} (docs/12-migration §5.1). An expiry already in the past is still carried as-is; the
 * mapper decides what to do with a lapsed mute.
 *
 * @param reason the free-text mute reason, empty when EssentialsX stored none
 * @param expiryEpochMillis the wall-clock expiry in epoch-millis, empty for a permanent mute
 */
@NullMarked
public record EssXMute(Optional<String> reason, Optional<Long> expiryEpochMillis) {

    public EssXMute {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(expiryEpochMillis, "expiryEpochMillis");
    }
}
