package com.uxplima.uxmessentials.moderation.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /unwarn <player>}: wipe a player's whole warning history. The complement of the append-only
 * {@code /warn}: there is no per-entry removal, the operator clears the lot. A target with no warnings is a
 * no-op that still answers the actor and audit-logs the miss; a cleared target's rows are deleted and the
 * removed count is reported and audit-logged.
 */
public final class ClearWarns {

    private final ModerationRepository repository;
    private final Notifier notifier;
    private final ModerationAudit audit;

    public ClearWarns(ModerationRepository repository, Notifier notifier, ModerationAudit audit) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    /** Clear {@code target}'s warnings, or tell the actor there were none to clear. */
    public void clear(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        int removed = repository.clearWarns(target);
        if (removed == 0) {
            notifier.send(actor, ModerationMessageKey.UNWARN_NONE, Map.of("player", target.name()));
            audit.clearedWarns(actor, target, false, 0);
            return;
        }
        notifier.send(
                actor,
                ModerationMessageKey.UNWARN_CLEARED,
                Map.of("player", target.name(), "count", Integer.toString(removed)));
        audit.clearedWarns(actor, target, true, removed);
    }
}
