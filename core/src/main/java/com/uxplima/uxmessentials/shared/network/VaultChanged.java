package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * One player vault's contents changed on the origin backend (a vault window closed and saved), so peers must
 * drop their cached copy of that vault and re-read the authoritative contents on the next open. A vault is
 * keyed by its owner and index, so the frame carries both; the durable contents live in the shared database
 * (vault contents are DB-backed, never PDC, so they survive a world rollback, the same hard invariant as
 * economy balances).
 *
 * @param originServer the backend that made the change
 * @param owner the vault owner whose cached vault peers must invalidate
 * @param vaultIndex the numbered vault index that changed
 */
public record VaultChanged(String originServer, UUID owner, int vaultIndex) implements NetworkMessage {

    public VaultChanged {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(owner, "owner");
        if (vaultIndex < 1) {
            throw new IllegalArgumentException("vaultIndex must be positive: " + vaultIndex);
        }
    }

    @Override
    public MessageType type() {
        return MessageType.VAULT_CHANGED;
    }
}
