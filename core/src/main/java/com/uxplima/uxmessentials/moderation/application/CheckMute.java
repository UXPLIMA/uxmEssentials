package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /checkmute <player>}: report whether a player is currently muted, and if so the issuer, reason and
 * expiry of the active mute. A read-only point lookup against the DB-backed sanction store ({@code loadMute})
 *. Unlike {@code /mutehistory}, which lists the full record, this answers the single "is this player muted
 * right now?" question staff ask before a fresh sanction. The command runs the read off the tick thread.
 *
 * <p>A timed mute whose expiry has already passed reports as not muted, so the check reflects the live gate the
 * messaging context sees rather than the lingering row. The rendered expiry is blank for a permanent mute.
 */
public final class CheckMute {

    private final ModerationRepository repository;
    private final Notifier notifier;
    private final Clock clock;

    public CheckMute(ModerationRepository repository, Notifier notifier, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Report {@code target}'s current mute state to {@code viewer}. */
    public void show(PlayerRef viewer, PlayerRef target) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        MuteState mute = repository.loadMute(target);
        Instant now = clock.instant();
        if (!mute.isActiveAt(now)) {
            notifier.send(viewer, ModerationMessageKey.MOD_CHECK_NOT_MUTED, Map.of("player", target.name()));
            return;
        }
        notifier.send(viewer, ModerationMessageKey.MOD_CHECK_MUTED, active(target, mute));
    }

    private static Map<String, String> active(PlayerRef target, MuteState mute) {
        Issuer issuer = issuerOf(mute);
        return Map.of(
                "player", target.name(),
                "actor", issuer.name(),
                "reason", reasonOf(mute).orElse(""),
                "expiry", mute.expiry().map(Object::toString).orElse(""));
    }

    private static Issuer issuerOf(MuteState mute) {
        return switch (mute) {
            case MuteState.Permanent permanent -> permanent.issuer();
            case MuteState.Timed timed -> timed.issuer();
            case MuteState.None none -> throw new IllegalStateException("an inactive mute has no issuer");
        };
    }

    private static Optional<String> reasonOf(MuteState mute) {
        return switch (mute) {
            case MuteState.Permanent permanent -> permanent.reason();
            case MuteState.Timed timed -> timed.reason();
            case MuteState.None none -> Optional.empty();
        };
    }
}
