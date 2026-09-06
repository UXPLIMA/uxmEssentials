package com.uxplima.uxmessentials.vaults.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A vault's stored contents were written through to the database. The save that fires on {@code
 * InventoryCloseEvent} when a viewer closes an open vault GUI. The {@code owner} is whose vault changed and
 * {@code index} the one-based vault number; {@code at} is the save instant, which becomes the vault's
 * {@code lastTouched}.
 *
 * @param owner whose vault changed
 * @param index the one-based vault number saved
 * @param at when the contents were written
 */
public record VaultContentsChanged(PlayerRef owner, int index, Instant at) implements VaultEvent {

    public VaultContentsChanged {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(at, "at");
        if (index < 1) {
            throw new IllegalArgumentException("vault index must be at least 1: " + index);
        }
    }
}
