package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.JailDirectory;
import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerJailed;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * {@code /jail <player> <jail> [duration] [reason]}: confine a player to a named jail. With no duration the
 * jail is permanent; with one it is timed. Online-only by default (the countdown advances only while the
 * player is connected in the jail), or wall-clock when the operator opts that jail into {@code jail-countdown
 * = wall-clock} in {@code moderation.conf}. The exempt target, an unknown jail and a malformed duration are
 * refused (audit-logged). On success the jail row is upserted, an online target is teleported into the jail
 * via {@link Sanctions}, {@code PlayerJailed} is published, and the teleport context's {@code JailGate} starts
 * blocking their self-teleports on its next read. An offline target is a DB write only, the join listener
 * re-applies it on reconnect (offline jail).
 */
public final class Jail {

    private final ModerationRepository repository;
    private final JailDirectory jails;
    private final Sanctions sanctions;
    private final ModerationGuard guard;
    private final Notifier notifier;
    private final ModerationAudit audit;
    private final DomainEventPublisher events;
    private final Clock clock;

    public Jail(
            ModerationRepository repository,
            JailDirectory jails,
            Sanctions sanctions,
            ModerationGuard guard,
            Notifier notifier,
            ModerationAudit audit,
            DomainEventPublisher events,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.jails = Objects.requireNonNull(jails, "jails");
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Jail {@code target} in {@code jail}: permanent when {@code rawDuration} is blank, timed otherwise. */
    public Result<JailState.Active, ModerationError> jail(
            PlayerRef actor, PlayerRef target, String jail, String rawDuration, Optional<String> reason) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(jail, "jail");
        Objects.requireNonNull(reason, "reason");
        Optional<ModerationError> rejection = validate(target, jail, rawDuration);
        if (rejection.isPresent()) {
            return reject(actor, target, jail, rawDuration, reason, rejection.get());
        }
        return apply(actor, target, jail, SanctionDuration.parse(rawDuration), reason);
    }

    private Optional<ModerationError> validate(PlayerRef target, String jail, String rawDuration) {
        if (guard.isExempt(target)) {
            return Optional.of(ModerationError.TARGET_EXEMPT);
        }
        if (!jails.exists(jail)) {
            return Optional.of(ModerationError.UNKNOWN_JAIL);
        }
        return SanctionDuration.parse(rawDuration).malformed()
                ? Optional.of(ModerationError.BAD_DURATION)
                : Optional.empty();
    }

    private Result<JailState.Active, ModerationError> apply(
            PlayerRef actor, PlayerRef target, String jail, SanctionDuration.Parsed parsed, Optional<String> reason) {
        Instant now = clock.instant();
        JailState.Active sentence = sentence(jail, parsed, Issuer.of(actor), reason, now);
        repository.ensureUserExists(target, now);
        repository.saveJail(target, sentence);
        sanctions.sendToJail(target, jail);
        notifyTarget(target, jail, parsed, reason);
        events.publish(new PlayerJailed(target, sentence, now));
        audit.jailed(actor, target, jail, parsed.duration().map(SanctionDuration::format), true, reason);
        return Result.ok(sentence);
    }

    private JailState.Active sentence(
            String jail, SanctionDuration.Parsed parsed, Issuer issuer, Optional<String> reason, Instant now) {
        Optional<Duration> duration = parsed.duration();
        if (duration.isEmpty()) {
            return (JailState.Active) JailState.permanent(jail, issuer, reason, now);
        }
        if (jails.isWallClock(jail)) {
            return (JailState.Active) JailState.wallClockTimed(jail, now.plus(duration.get()), issuer, reason, now);
        }
        return (JailState.Active) JailState.onlineTimed(jail, duration.get(), issuer, reason, now);
    }

    private void notifyTarget(PlayerRef target, String jail, SanctionDuration.Parsed parsed, Optional<String> reason) {
        Map<String, String> ph = Map.of(
                "jail", jail,
                "duration", parsed.duration().map(SanctionDuration::format).orElse("permanent"),
                "reason", reason.orElse(""));
        notifier.send(target, ModerationMessageKey.JAIL_NOTIFY_TARGET, ph);
    }

    private Result<JailState.Active, ModerationError> reject(
            PlayerRef actor,
            PlayerRef target,
            String jail,
            String rawDuration,
            Optional<String> reason,
            ModerationError error) {
        Optional<String> label = rawDuration.isBlank() ? Optional.empty() : Optional.of(rawDuration);
        audit.jailed(actor, target, jail, label, false, reason);
        notifier.send(actor, error.messageKey());
        return Result.err(error);
    }
}
