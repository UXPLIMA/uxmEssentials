package com.uxplima.uxmessentials.shared.application.port;

import java.util.Locale;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for a player's persisted {@code /lang} locale override (docs/13-i18n §7).
 *
 * <p>The override is the first link in the resolution chain: an operator's explicit {@code /lang tr}
 * beats the client locale. It persists per player so the choice survives a relog; the default backing
 * is PDC (a lost language preference is harmless, so it need not survive a world rollback), but the port
 * hides the storage so a server can back it with the DB instead without touching the resolver.
 *
 * <p>An absent override is the common case, a player who has never run {@code /lang}, and is modelled
 * as {@link Optional#empty()} so the resolver falls through to the client locale.
 */
public interface LocaleStore {

    /** The player's stored {@code /lang} override, or empty when they have set none. */
    Optional<Locale> override(PlayerRef player);

    /** Persist {@code locale} as the player's {@code /lang} override, replacing any previous choice. */
    void setOverride(PlayerRef player, Locale locale);

    /** Clear the player's override so resolution falls back to their client locale. */
    void clearOverride(PlayerRef player);
}
