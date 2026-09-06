package com.uxplima.uxmessentials.messaging.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for the per-player {@code /rtoggle} switch: whether a player takes part in reply routing. A
 * {@code /reply} aimed at a player who has turned reply routing off resolves
 * {@link com.uxplima.uxmessentials.messaging.domain.MessagingError#NO_REPLY_TARGET} before delivery, so the
 * reply is declined exactly as if there were no fresh conversation. It never leaks that the partner is on the
 * server. This is narrower than {@code /msgtoggle}: a named {@code /msg} still reaches the player; only the
 * back-channel {@code /reply} convenience is gated.
 *
 * <p>The flag is per-holder PDC state, not persisted database-grade data. It mirrors the {@code /msgtoggle}
 * and the teleport {@code /tptoggle} model rather than the persistent ignore list.
 */
public interface ReplyRoutingStore {

    /** True when {@code who} currently takes part in reply routing (the default for a fresh player). */
    boolean acceptsReplies(PlayerRef who);

    /** Flip {@code who}'s {@code /rtoggle} state, returning the new "accepts replies" value. */
    boolean toggle(PlayerRef who);
}
