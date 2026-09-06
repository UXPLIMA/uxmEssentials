package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationProfile;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /sanction <player>}: an aggregated, read-only punishment summary for one target, their current mute,
 * jail and tempban state plus the count of warnings still in effect. A staff convenience that gathers in one
 * read what {@code /mutelist}, {@code /jailedplayers}, the ban check and {@code /warns} expose separately, so a
 * staff member sees a player's standing at a glance. It rebuilds the live {@link ModerationProfile} from the
 * per-sanction tables in one query and counts the active warnings; it mutates nothing.
 */
public final class SanctionSummary {

    private final ModerationRepository repository;
    private final Notifier notifier;
    private final Clock clock;

    public SanctionSummary(ModerationRepository repository, Notifier notifier, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Render {@code target}'s current mute / jail / ban state and active warning count to {@code actor}. */
    public void summarize(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Instant now = clock.instant();
        ModerationProfile profile = repository.load(target);
        int warns = repository.warns(target, now).size();
        notifier.send(actor, ModerationMessageKey.SANCTION_HEADER, Map.of("player", target.name()));
        mute(actor, profile.mute(), now);
        jail(actor, profile.jail(), now);
        ban(actor, profile.tempban(), now);
        notifier.send(actor, ModerationMessageKey.SANCTION_WARNS, Map.of("count", Integer.toString(warns)));
    }

    private void mute(PlayerRef actor, MuteState mute, Instant now) {
        if (mute.isActiveAt(now)) {
            notifier.send(actor, ModerationMessageKey.SANCTION_MUTE_ACTIVE, Map.of("until", until(mute.expiry())));
        } else {
            notifier.send(actor, ModerationMessageKey.SANCTION_MUTE_NONE);
        }
    }

    private void jail(PlayerRef actor, JailState jail, Instant now) {
        if (jail.isActiveAt(now) && jail instanceof JailState.Active active) {
            notifier.send(
                    actor,
                    ModerationMessageKey.SANCTION_JAIL_ACTIVE,
                    Map.of("jail", active.jail(), "until", jailExpiry(active)));
        } else {
            notifier.send(actor, ModerationMessageKey.SANCTION_JAIL_NONE);
        }
    }

    /** The jail's wall-clock expiry, its remaining online time, or the {@code permanent} marker. */
    private static String jailExpiry(JailState.Active active) {
        if (active.until().isPresent()) {
            return active.until().get().toString();
        }
        return active.remaining().map(SanctionDuration::format).orElse("permanent");
    }

    private void ban(PlayerRef actor, TempbanState tempban, Instant now) {
        if (tempban.isActiveAt(now)) {
            notifier.send(actor, ModerationMessageKey.SANCTION_BAN_ACTIVE, Map.of("until", until(tempban.expiry())));
        } else {
            notifier.send(actor, ModerationMessageKey.SANCTION_BAN_NONE);
        }
    }

    /** A wall-clock expiry rendered as an instant, or the {@code permanent} marker when there is none. */
    private static String until(Optional<Instant> expiry) {
        return expiry.map(Instant::toString).orElse("permanent");
    }
}
