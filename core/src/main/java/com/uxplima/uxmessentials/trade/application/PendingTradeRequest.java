package com.uxplima.uxmessentials.trade.application;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * One in-flight {@code /trade <player>} request awaiting the target's {@code /trade accept} or {@code /trade deny}. It
 * records who asked, who was asked, and when. The {@code createdAt} stamp is what the {@link TradeRequests} book reads
 * to expire a request the target never answered. The request carries no offer: a request only opens the window on
 * accept, and the offers are staked in the window itself.
 *
 * @param requester the player who ran {@code /trade <target>}
 * @param target the player asked to trade
 * @param createdAt the instant the request was sent, for the expiry cutoff
 */
@NullMarked
public record PendingTradeRequest(PlayerRef requester, PlayerRef target, Instant createdAt) {

    public PendingTradeRequest {
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(createdAt, "createdAt");
        if (requester.equals(target)) {
            throw new IllegalArgumentException("a player cannot send a trade request to themselves");
        }
    }
}
