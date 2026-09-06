package com.uxplima.uxmessentials.kits.application.port;

import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Per-player one-time-claim state for kits. A one-time kit is consumed forever after the first claim; this
 * port records and reads that fact. The adapter keeps the stamp in PDC under a per-kit
 * {@link org.bukkit.NamespacedKey NamespacedKey} (created once), since a claim flag is transient per-holder
 * state that may safely die with a world rollback (docs/03-paper-api §3.6). Never the database, which is
 * reserved for state that must survive a rollback.
 *
 * <p>This is intentionally narrow and separate from the shared {@code Cooldowns} port: cooldowns rate-limit
 * a repeatable kit (a "ready-at" timestamp that expires), while a one-time stamp is a permanent
 * "already-claimed" mark with no expiry. A kit can be both, rate-limited and one-time, and the two gates
 * are checked independently. The {@code reset} operations back {@code /kit reset}, clearing a player's
 * stamps so they may claim again.
 *
 * <p>An offline player has no PDC to read, so {@link #hasClaimed} reads {@code false} and {@link #markClaimed}
 * is a no-op for them: the same offline-no-op semantics the cooldown store has.
 */
public interface KitClaimStore {

    /** True when {@code who} has already consumed the one-time kit {@code kit}. */
    boolean hasClaimed(PlayerRef who, KitId kit);

    /** Record that {@code who} has consumed the one-time kit {@code kit}. */
    void markClaimed(PlayerRef who, KitId kit);

    /** Clear {@code who}'s one-time stamp for {@code kit}, so they may claim it again. */
    void reset(PlayerRef who, KitId kit);

    /** Clear every one-time stamp {@code who} holds, across all kits. */
    void resetAll(PlayerRef who);
}
