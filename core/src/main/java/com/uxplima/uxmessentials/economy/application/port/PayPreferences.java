package com.uxplima.uxmessentials.economy.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for a player's persisted accept-pay flag ({@code /paytoggle}). The flag is a queryable
 * key-value row on the player's economy state, not a JSON blob, so it survives relog and can be read
 * without materialising a wallet. When a player has turned incoming pay off, a {@code /pay} to them resolves
 * {@code TransferError.TARGET_DISABLED} before the debit leg runs, so the payer is never charged for a
 * transfer the target refuses ({@code docs/11-economy-integration.md} §9.1).
 */
public interface PayPreferences {

    /**
     * True when {@code target} currently accepts incoming pay. A player who has never run {@code /paytoggle}
     * takes the configured {@code economy.pay.toggle-default}.
     */
    boolean acceptsPay(PlayerRef target);

    /** Flip {@code who}'s accept-pay flag and return its new value. */
    boolean toggle(PlayerRef who);
}
