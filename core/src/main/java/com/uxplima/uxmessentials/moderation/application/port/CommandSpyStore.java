package com.uxplima.uxmessentials.moderation.application.port;

import java.util.List;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for the staff {@code /commandspy} toggle, which staff are currently watching the commands
 * other players run (audit-relevant). When a player runs a command, every spying staff member who is not the
 * one running it receives a spy line. The flag is per-holder session state, dropped on relog by default; the
 * store also enumerates the active spies so the command-watch listener can fan out without scanning every
 * online player. Mirrors the messaging {@code SocialSpyStore} shape. Same staff-tool semantics, a different
 * channel (commands rather than private messages).
 */
public interface CommandSpyStore {

    /** True when {@code who} currently has {@code /commandspy} on. */
    boolean isSpying(PlayerRef who);

    /** Flip {@code who}'s {@code /commandspy} state, returning the new "is spying" value. */
    boolean toggle(PlayerRef who);

    /** Every staff member currently spying, for the command-watch fan-out. */
    List<PlayerRef> activeSpies();
}
