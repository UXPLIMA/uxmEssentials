package com.uxplima.uxmessentials.playerstate.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for the per-player {@code /clearinventory} confirmation preference set by
 * {@code /clearinventoryconfirmtoggle}. When a player has it on, a self {@code /clearinventory} asks for a
 * second confirmation before it empties the inventory, so a misclick or fat-fingered command does not wipe an
 * inventory outright. The default is off, preserving the historical clear-immediately behaviour.
 *
 * <p>This is a per-player preference that survives relog, so the adapter stamps it in PDC under a single
 * pre-created key: never economy-grade persisted data, just session/PDC state like the teleport flags.
 */
public interface ClearInventoryPreferences {

    /** True when {@code who} has asked {@code /clearinventory} to confirm before clearing. */
    boolean confirmEnabled(PlayerRef who);

    /** Flip {@code who}'s confirmation preference, returning the new "confirm enabled" value. */
    boolean toggleConfirm(PlayerRef who);
}
