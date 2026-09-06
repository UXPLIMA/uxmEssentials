package com.uxplima.uxmessentials.presence.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for a player's {@code /nick} display name. A nickname is transient per-holder preference that
 * survives relog, the same class as a cooldown stamp, so the implementation keeps it in PDC, never the
 * economy-grade DB. Setting a nick both stamps the preference and applies the display name to the live player;
 * clearing both removes the stamp and restores the account name. The application layer validates the nick at
 * entry, so this port trusts the value it is handed.
 */
public interface NickStore {

    /** Stamp {@code who}'s nickname and apply it to the live player. */
    void setNick(PlayerRef who, String nick);

    /** Remove {@code who}'s nickname stamp and restore their account name. */
    void clearNick(PlayerRef who);
}
