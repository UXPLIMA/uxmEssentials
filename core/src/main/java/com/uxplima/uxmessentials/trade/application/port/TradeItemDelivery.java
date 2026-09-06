package com.uxplima.uxmessentials.trade.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Delivers a cross-server trade's escrowed items back into a local player's inventory. The {@code itemData} is the
 * opaque, adapter-owned string the escrow row holds; the adapter decodes it to real stacks, hops to the player's
 * region (Folia-safe), and adds them, dropping any overflow at the player's feet so no stack is ever lost. Money moves
 * through the {@link TradeEconomy} deposit, not here: a DB-backed credit works whether or not the player is online.
 *
 * <p>Item delivery needs the recipient present, so {@link #isOnline(PlayerRef)} lets the coordinator defer a claim
 * whose recipient is offline to their next join (the escrow row survives and is reconciled then), keeping delivery
 * exactly-once without losing items to a delivery aimed at an absent player.
 */
public interface TradeItemDelivery {

    /** Whether the player is currently online on this backend and can receive an item delivery now. */
    boolean isOnline(PlayerRef player);

    /** Deliver the escrowed {@code itemData} to {@code player} on their region thread; a no-op for blank data. */
    void deliver(PlayerRef player, String itemData);
}
