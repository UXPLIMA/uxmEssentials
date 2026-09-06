package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * A wallet's balance changed on the origin backend, so peers must drop their cached copy of {@code owner}'s
 * wallet and re-read the authoritative row on the next {@code /balance} / {@code /baltop}. The frame carries
 * the owner identity and the currency id, not the new figure. The DB is the source of truth, and a
 * cross-server {@code /pay} commits its guarded debit/credit in one transaction against the shared row; this
 * notification only invalidates the read cache so a peer never serves a stale balance.
 *
 * @param originServer the backend that made the change
 * @param owner the wallet owner whose cached balance peers must invalidate
 * @param currencyId the currency whose balance changed (the default currency for a single-currency setup)
 */
public record BalanceChanged(String originServer, UUID owner, String currencyId) implements NetworkMessage {

    public BalanceChanged {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(currencyId, "currencyId");
    }

    @Override
    public MessageType type() {
        return MessageType.BALANCE_CHANGED;
    }
}
