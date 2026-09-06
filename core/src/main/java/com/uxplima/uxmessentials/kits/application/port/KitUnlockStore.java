package com.uxplima.uxmessentials.kits.application.port;

import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Per-player buy-to-unlock state for kits. A kit set to {@code unlock-once} charges its cost once: the first
 * claim debits the price and records the unlock here, and every later claim reads that unlock and is granted
 * free on its normal cooldown. This port records and reads that one-time-purchase fact. The adapter keeps the
 * mark in PDC under a per-kit {@link org.bukkit.NamespacedKey NamespacedKey} (created once), since an unlock is
 * transient per-holder state that may safely die with a world rollback (docs/03-paper-api §3.6), never the
 * database, which is reserved for state that must survive a rollback.
 *
 * <p>This mirrors the narrow {@code KitClaimStore} one-time-claim store but answers a different question: a
 * one-time <em>claim</em> consumes the kit forever (it can never be claimed again), while an <em>unlock</em>
 * permanently waives the kit's price (it stays claimable, just free). A kit could in principle be both, and the
 * two marks are recorded independently.
 *
 * <p>An offline player has no PDC to read, so {@link #hasUnlocked} reads {@code false} and {@link #markUnlocked}
 * is a no-op for them: the same offline-no-op semantics the claim store has.
 */
public interface KitUnlockStore {

    /** True when {@code who} has already bought the one-time unlock for {@code kit}. */
    boolean hasUnlocked(PlayerRef who, KitId kit);

    /** Record that {@code who} has bought the one-time unlock for {@code kit}, waiving its future price. */
    void markUnlocked(PlayerRef who, KitId kit);
}
