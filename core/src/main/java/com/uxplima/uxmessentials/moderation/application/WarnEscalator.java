package com.uxplima.uxmessentials.moderation.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.WarnEscalation;
import com.uxplima.uxmessentials.moderation.domain.WarnEscalation.EscalationStep;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Turns a freshly-issued warning's running count into an automatic follow-up sanction, per the configured
 * {@link WarnEscalation} ladder. Both {@code /warn} and {@code /tempwarn} run their post-append count through
 * {@link #escalate}: when a rung matches the count exactly, the rung's sanction is applied to the target and
 * the warning's actor is told {@code MOD_WARN_ESCALATED}.
 *
 * <p>The escalated sanction is driven through the existing {@link Mute}/{@link TempBan}/{@link Ban}/{@link Kick}
 * use cases, never back through a warn, so escalation cannot recurse: a tempban from the 5th warning is a
 * plain tempban, it does not re-enter the ladder. It is issued under a stable system actor (so the audit and
 * broadcast attribute it to the server's rule, not the staff member who happened to land the final warning),
 * and inherits the warning's {@code silent} flag, so a silent warn escalates silently.
 */
public final class WarnEscalator {

    /** The stable system actor an escalated sanction is attributed to. */
    private static final PlayerRef SYSTEM_ACTOR = PlayerRef.system("system");

    private final WarnEscalation ladder;
    private final Mute mute;
    private final TempBan tempBan;
    private final Ban ban;
    private final Kick kick;
    private final Notifier notifier;

    public WarnEscalator(WarnEscalation ladder, Mute mute, TempBan tempBan, Ban ban, Kick kick, Notifier notifier) {
        this.ladder = Objects.requireNonNull(ladder, "ladder");
        this.mute = Objects.requireNonNull(mute, "mute");
        this.tempBan = Objects.requireNonNull(tempBan, "tempBan");
        this.ban = Objects.requireNonNull(ban, "ban");
        this.kick = Objects.requireNonNull(kick, "kick");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /**
     * Apply the escalation rung that {@code newWarnCount} trips, if any, against {@code target}; tell
     * {@code actor} which action escalated. A {@code silent} warning escalates silently. No rung for the count
     * is a no-op.
     */
    public void escalate(PlayerRef actor, PlayerRef target, int newWarnCount, boolean silent) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Optional<EscalationStep> step = ladder.stepFor(newWarnCount);
        if (step.isEmpty()) {
            return;
        }
        EscalationStep rung = step.get();
        applyAction(target, rung, silent);
        notifier.send(
                actor,
                ModerationMessageKey.MOD_WARN_ESCALATED,
                Map.of("action", rung.action().name().toLowerCase(java.util.Locale.ROOT)));
    }

    private void applyAction(PlayerRef target, EscalationStep rung, boolean silent) {
        Optional<String> noReason = Optional.empty();
        String span = rung.duration().map(SanctionDuration::format).orElse("");
        switch (rung.action()) {
            case MUTE -> mute.mute(SYSTEM_ACTOR, target, "", noReason, silent);
            case TEMPMUTE -> mute.mute(SYSTEM_ACTOR, target, span, noReason, silent);
            case KICK -> kick.kick(SYSTEM_ACTOR, target, noReason, silent);
            case TEMPBAN -> tempBan.tempban(SYSTEM_ACTOR, target, span, noReason, silent);
            case BAN -> ban.ban(SYSTEM_ACTOR, target, noReason, silent);
        }
    }
}
