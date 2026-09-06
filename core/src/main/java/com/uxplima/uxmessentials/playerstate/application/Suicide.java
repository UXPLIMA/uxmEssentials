package com.uxplima.uxmessentials.playerstate.application;

import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /suicide}: kill yourself. Self-only. There is no target form (§15.6, docs/permissions.md), so the
 * use case takes a single player. A live-only effect through the {@link PlayerEffects} port; the death itself
 * is the feedback, with a short confirmation message for the player.
 */
public final class Suicide {

    private final PlayerEffects effects;
    private final Notifier notifier;

    public Suicide(PlayerEffects effects, Notifier notifier) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Kill {@code who}. */
    public void suicide(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        effects.kill(who);
        notifier.send(who, PlayerstateMessageKey.SUICIDE);
    }
}
