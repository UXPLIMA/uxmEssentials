package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The narrow seam the punishment-detail view revokes through: lift the {@code target}'s active sanction of
 * {@code kind} on behalf of {@code actor}. The production binding (built in {@code ModerationGuiViews}) routes
 * by kind to the existing {@code unban} / {@code unmute} / {@code unjail} use cases. The same audited paths the
 * {@code /unban} / {@code /unmute} / {@code /unjail} commands take, so the GUI adds no parallel revoke logic.
 *
 * <p>Modelling this as a single-method port (rather than handing the view the whole {@code ModerationServices})
 * keeps the view's surface to exactly what it uses and lets a test drive a revoke without assembling the
 * forty-odd use cases the full services bundle requires. Implementations run off the tick thread.
 */
@FunctionalInterface
public interface PunishmentRevoker {

    /** Lift {@code target}'s active sanction of {@code kind} on {@code actor}'s authority. */
    void revoke(PlayerRef actor, PlayerRef target, PunishmentKind kind);
}
