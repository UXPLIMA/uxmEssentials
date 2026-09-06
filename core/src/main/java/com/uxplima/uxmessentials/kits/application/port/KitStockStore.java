package com.uxplima.uxmessentials.kits.application.port;

import com.uxplima.uxmessentials.kits.domain.KitId;

/**
 * The persistent global claim counter behind a stock-limited kit. The count of how many times a kit has ever
 * been claimed across all players, independent of the per-player one-time stamp and cooldown. A limited-edition
 * kit ({@code stock = 100}) draws from this counter; an unlimited kit never touches it.
 *
 * <p>Unlike the per-player {@code KitClaimStore} (PDC), this is a single global tally with no holder, so it is
 * file-backed rather than PDC-backed. Reservation is atomic: {@link #tryConsume} either takes a unit and returns
 * {@code true} or, when the limit is already reached, takes nothing and returns {@code false}, so two concurrent
 * claims can never both succeed past the cap. A reserved unit is given back with {@link #release} when a later
 * gate in the same claim fails (for instance the player cannot afford a priced, stocked kit).
 */
public interface KitStockStore {

    /**
     * Atomically reserve one unit of {@code kit}'s stock if fewer than {@code limit} have been claimed. Returns
     * {@code true} when a unit was taken (the claim may proceed), {@code false} when the kit is sold out.
     *
     * @param kit the kit whose global counter to draw from
     * @param limit the global cap; always positive (an unlimited kit never reaches here)
     */
    boolean tryConsume(KitId kit, int limit);

    /** Give back one previously reserved unit of {@code kit}'s stock when a later gate in the same claim fails. */
    void release(KitId kit);

    /** How many units of {@code kit} have been claimed so far: the browse menu reads it to show a sold-out icon. */
    long claimed(KitId kit);
}
