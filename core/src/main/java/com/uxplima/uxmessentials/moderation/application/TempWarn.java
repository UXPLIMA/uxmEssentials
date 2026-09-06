package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.SanctionBroadcast;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerWarned;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * {@code /tempwarn <player> <duration> [reason]}: append a warning that lapses on its own. It is the timed
 * sibling of {@code /warn} ({@link IssueWarn}), same exempt refusal, same append-only history, same
 * {@code PlayerWarned} event and audit line, same warn permission, same staff broadcast and same escalation
 * differing only in that the warning carries a wall-clock expiry, so it stops counting once the span elapses
 * without an operator running {@code /unwarn}.
 *
 * <p>Like {@code /tempban} there is no permanent form here: a blank, malformed or zero duration is refused
 * with {@code BAD_DURATION} (use {@code /warn} for a warning that stands until cleared). The {@code <duration>}
 * is the warning's lapse span, not a ban/mute span, so it is <em>not</em> run through the {@code maxduration}
 * tier ({@link SanctionDurationLimit} caps bans and mutes only). The new total the target is told is the
 * repository's post-append count, which already excludes warnings that have lapsed.
 */
public final class TempWarn {

    private final ModerationRepository repository;
    private final ModerationGuard guard;
    private final Notifier notifier;
    private final ModerationAudit audit;
    private final DomainEventPublisher events;
    private final SanctionHistoryRecorder history;
    private final SanctionBroadcast broadcast;
    private final WarnEscalator escalator;
    private final Clock clock;

    public TempWarn(
            ModerationRepository repository,
            ModerationGuard guard,
            Notifier notifier,
            ModerationAudit audit,
            DomainEventPublisher events,
            SanctionHistoryRecorder history,
            SanctionBroadcast broadcast,
            WarnEscalator escalator,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.events = Objects.requireNonNull(events, "events");
        this.history = Objects.requireNonNull(history, "history");
        this.broadcast = Objects.requireNonNull(broadcast, "broadcast");
        this.escalator = Objects.requireNonNull(escalator, "escalator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Warn {@code target} for the parsed span of {@code rawDuration}, returning the appended warning. */
    public Result<PlayerWarned, ModerationError> warn(
            PlayerRef actor, PlayerRef target, String rawDuration, Optional<String> reason, boolean silent) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(rawDuration, "rawDuration");
        Objects.requireNonNull(reason, "reason");
        if (guard.isExempt(target)) {
            audit.warned(actor, target, false, reason);
            notifier.send(actor, ModerationError.TARGET_EXEMPT.messageKey());
            return Result.err(ModerationError.TARGET_EXEMPT);
        }
        Optional<Duration> span = SanctionDuration.parse(rawDuration).duration();
        if (span.isEmpty()) {
            // A tempwarn must be timed: a permanent or malformed input is BAD_DURATION (use /warn instead).
            audit.warned(actor, target, false, reason);
            notifier.send(actor, ModerationError.BAD_DURATION.messageKey());
            return Result.err(ModerationError.BAD_DURATION);
        }
        return apply(actor, target, span.get(), reason, silent);
    }

    private Result<PlayerWarned, ModerationError> apply(
            PlayerRef actor, PlayerRef target, Duration span, Optional<String> reason, boolean silent) {
        Instant now = clock.instant();
        Instant lapse = now.plus(span);
        Warn warn = Warn.timed(Issuer.of(actor), reason, now, lapse);
        repository.ensureUserExists(target, now);
        int total = repository.appendWarn(target, warn);
        PlayerWarned event = new PlayerWarned(target, warn, total);
        history.warn(actor, target, reason, Optional.of(lapse));
        notifier.send(target, ModerationMessageKey.WARN_NOTIFY_TARGET, Map.of("reason", reason.orElse("")));
        events.publish(event);
        audit.warned(actor, target, true, reason);
        notifier.send(actor, ModerationMessageKey.TEMPWARN_APPLIED, applied(target, total, span));
        if (!silent) {
            broadcast.announce(
                    ModerationMessageKey.MOD_BROADCAST_WARN,
                    Map.of("actor", actor.name(), "target", target.name(), "reason", reason.orElse("")));
        }
        escalator.escalate(actor, target, total, silent);
        return Result.ok(event);
    }

    private static Map<String, String> applied(PlayerRef target, int total, Duration span) {
        return Map.of(
                "player", target.name(),
                "count", Integer.toString(total),
                "duration", SanctionDuration.format(span));
    }
}
