package com.uxplima.uxmessentials.moderation.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.SanctionBroadcast;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /kick <player> [reason]}: eject one connected player. The exempt target is refused (audit-logged);
 * any other online target is kicked with the rendered reason. A kick mutates no DB state. It is a live
 * disconnect, but it is still audit-logged because it is an admin action.
 *
 * <p>Unless the kick is silent ({@code /kick -s}), the staff broadcast announces it to everyone holding the
 * moderation broadcast node; a silent kick suppresses only that announcement, leaving the actor confirmation
 * and the target's kick screen intact. A successful kick is recorded in the append-only sanction history so it
 * surfaces in {@code /history} and {@code /staffhistory}: a kick carries no expiry.
 */
public final class Kick {

    private final Sanctions sanctions;
    private final ModerationGuard guard;
    private final Notifier notifier;
    private final ModerationAudit audit;
    private final SanctionHistoryRecorder history;
    private final SanctionBroadcast broadcast;

    public Kick(
            Sanctions sanctions,
            ModerationGuard guard,
            Notifier notifier,
            ModerationAudit audit,
            SanctionHistoryRecorder history,
            SanctionBroadcast broadcast) {
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.history = Objects.requireNonNull(history, "history");
        this.broadcast = Objects.requireNonNull(broadcast, "broadcast");
    }

    /** Kick {@code target} with an optional reason, or refuse an exempt target. */
    public Result<Unit, ModerationError> kick(
            PlayerRef actor, PlayerRef target, Optional<String> reason, boolean silent) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        if (guard.isExempt(target)) {
            audit.kicked(actor, target, false, reason);
            notifier.send(actor, ModerationError.TARGET_EXEMPT.messageKey());
            return Result.err(ModerationError.TARGET_EXEMPT);
        }
        MessageKey key = ModerationMessageKey.KICK_KICKED;
        sanctions.kick(target, key, notifier.render(target, key, Map.of("reason", reason.orElse(""))));
        history.kick(actor, target, reason);
        audit.kicked(actor, target, true, reason);
        notifier.send(actor, ModerationMessageKey.KICK_APPLIED, Map.of("player", target.name()));
        if (!silent) {
            broadcast.announce(
                    ModerationMessageKey.MOD_BROADCAST_KICK,
                    Map.of("actor", actor.name(), "target", target.name(), "reason", reason.orElse("")));
        }
        return Result.ok();
    }
}
