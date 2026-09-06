package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code kit_*} / {@code kits_*} placeholders. It is an adapter over
 * the kits context's {@code KitRepository} (the catalog) and the {@code KitAccess} gate (which folds the
 * shared {@code Permissions}, {@code Cooldowns} and one-time-claim store), wired during bootstrap; when the
 * kits module is disabled the seam is absent and every placeholder degrades.
 *
 * <p>The per-kit reads ({@link #cooldownRemaining}, {@link #available}, {@link #hasPermission},
 * {@link #cost}, {@link #claimsLeft}) all answer {@link Optional#empty()} when no kit carries the requested
 * id (or the id is malformed) so the resolver renders the empty default for an unknown kit rather than a
 * misleading {@code no}/{@code 0}. {@link #usableIds} lists the kits a player may claim, the same filter the
 * {@code /kit list} surface renders.
 */
public interface KitsPlaceholders {

    /**
     * The cooldown remaining for {@code who} on the kit named {@code kitId}: present and positive while
     * the player must wait, present-and-zero when the kit exists but is ready, and empty when no kit
     * carries that id.
     */
    Optional<Duration> cooldownRemaining(PlayerRef who, String kitId);

    /**
     * Whether {@code who} may claim the kit named {@code kitId} right now. Its cooldown is ready, they hold
     * the per-kit permission, and they have not already consumed it if it is one-time. Empty when no kit
     * carries that id.
     */
    Optional<Boolean> available(PlayerRef who, String kitId);

    /**
     * Whether {@code who} holds the per-kit permission for {@code kitId} (always true for a kit that is not
     * permission-gated). Empty when no kit carries that id.
     */
    Optional<Boolean> hasPermission(PlayerRef who, String kitId);

    /** The price to claim the kit named {@code kitId} ({@link BigDecimal#ZERO} for a free kit); empty when unknown. */
    Optional<BigDecimal> cost(String kitId);

    /**
     * How many times {@code who} may still claim the kit named {@code kitId}: {@code 1} or {@code 0} for a
     * one-time kit (by whether they have already consumed it), and a negative value, "unlimited", for a
     * repeatable kit. Empty when no kit carries that id.
     */
    Optional<Integer> claimsLeft(PlayerRef who, String kitId);

    /** The ids of the kits {@code who} may claim, in catalog order (the {@code /kit list} filter). */
    List<String> usableIds(PlayerRef who);
}
