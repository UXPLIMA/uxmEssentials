package com.uxplima.uxmessentials.vanish.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for the persistent buffs a vanished player carries: the configured night vision and flight allowance.
 * These are stateful live-player effects (a potion effect, the flight flag), so the adapter applies them on the
 * player's own region/entity thread and re-derives them from the current config each call; the use case only says
 * <em>when</em> to apply or clear them.
 *
 * <p>{@link #apply} is idempotent. Re-applying an already-buffed player is safe, which is what lets the join re-derive
 * ({@code SetVanishLevel.reapply}) top a still-vanished player back up without tracking prior state. {@link #clear}
 * removes the granted night vision and restores the flight allowance to the player's game-mode default (creative and
 * spectator always fly; survival and adventure lose the granted allowance), so reappearing leaves no residual buff.
 * The event-driven protections (no-damage, no-hunger, mob-target) are not buffs. They are cancelled per event by the
 * listeners, so they are not this port's concern.
 */
public interface VanishBuffs {

    /** Apply the configured vanish buffs (night vision, flight allowance) to {@code who}; a no-op when offline. */
    void apply(PlayerRef who);

    /** Remove the granted buffs from {@code who} and restore their flight to the game-mode default; a no-op offline. */
    void clear(PlayerRef who);
}
