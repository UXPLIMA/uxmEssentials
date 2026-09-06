package com.uxplima.uxmessentials.teleport.application.port;

import java.math.BigDecimal;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The narrow economy seam the teleport context owns so an {@code /rtp} cost can be charged <em>without</em>
 * a hard dependency on the economy context. This is the entire economy surface RTP needs, an affordability
 * check taken before the search and a withdrawal taken after a successful landing: expressed in teleport's
 * own terms; the composition root supplies an adapter that bridges this to the resolved
 * {@code EconomyProvider}, and the teleport context never imports an economy type.
 *
 * <p>Soft coupling: the wired implementation resolves the provider lazily, so a cost is simply free while the
 * economy module is disabled ({@link #canAfford} always {@code true}, {@link #charge} a no-op). The two steps
 * are deliberately split in time. {@link #canAfford} gates the request before the safe-search runs (a player
 * who cannot pay never triggers a search) and {@link #charge} runs only once the teleport has actually landed,
 * so a failed or refused teleport never bills the player.
 */
public interface TeleportFee {

    /** A no-op fee: every request is affordable and nothing is ever withdrawn. */
    TeleportFee FREE = new TeleportFee() {
        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount) {
            return true;
        }

        @Override
        public void charge(PlayerRef who, BigDecimal amount) {
            // Free: nothing to withdraw.
        }
    };

    /** True when {@code who} can cover {@code amount} in the default currency. */
    boolean canAfford(PlayerRef who, BigDecimal amount);

    /**
     * Withdraw {@code amount} from {@code who}'s balance. Called only after a teleport has landed, so it is
     * fire-and-forget: the affordability was already gated up front, and the debit runs off the tick thread
     * (the balance is DB-backed, never PDC).
     */
    void charge(PlayerRef who, BigDecimal amount);
}
