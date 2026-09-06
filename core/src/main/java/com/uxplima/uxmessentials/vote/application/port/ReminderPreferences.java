package com.uxplima.uxmessentials.vote.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Per-player preference for vote reminder messages. The default state is opted in. A player who has
 * never run {@code /vote remind} wants reminders. {@link #toggle} flips the preference and returns
 * the new value so the adapter can send the right confirmation key without a second read.
 *
 * <p>Ownership: <b>region-bound</b>. Implementations backed by PDC touch the live {@link
 * org.bukkit.entity.Player}, so reads and writes must happen on the player's region thread (or the
 * command/event thread that already owns it).
 */
public interface ReminderPreferences {

    /**
     * Whether {@code who} currently wants reminder messages. Returns {@code true} for any player who
     * has never toggled (new players opt in by default). Returns {@code false} when the player is
     * offline and the implementation cannot read their PDC.
     */
    boolean wantsReminders(PlayerRef who);

    /**
     * Flip the preference for {@code who} and return the new value ({@code true} = now wants
     * reminders). When the player is offline and the implementation cannot write their PDC, the
     * toggle is a no-op and returns {@code true} (safe default).
     */
    boolean toggle(PlayerRef who);
}
