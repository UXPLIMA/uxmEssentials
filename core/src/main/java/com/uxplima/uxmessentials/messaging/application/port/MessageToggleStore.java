package com.uxplima.uxmessentials.messaging.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for the per-player {@code /msgtoggle} switch. Whether a player accepts incoming private
 * messages. When a player has toggled DMs off, a {@code /msg} or {@code /reply} to them resolves
 * {@link com.uxplima.uxmessentials.messaging.domain.MessagingError#TARGET_TOGGLED_OFF} before delivery, but
 * <em>mail still delivers</em> (the toggle gates only the real-time channel, per docs/permissions.md). The
 * flag is per-holder PDC state, not persisted database-grade data. It mirrors the teleport context's
 * {@code /tptoggle} model rather than the persistent ignore list.
 */
public interface MessageToggleStore {

    /** True when {@code who} currently accepts incoming private messages (the default for a fresh player). */
    boolean acceptsMessages(PlayerRef who);

    /** Flip {@code who}'s {@code /msgtoggle} state, returning the new "accepts messages" value. */
    boolean toggle(PlayerRef who);
}
