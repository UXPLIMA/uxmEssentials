package com.uxplima.uxmessentials.vaults.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player opened one of their vaults ({@code /vault} or {@code /vault <n>}). The {@code index} is the
 * one-based vault number opened; {@code at} is when it was opened. The admin form ({@code /vault <player> [n]})
 * raises this too, with {@code viewer} the staff member and {@code owner} the audited player, the audit trail
 * is the {@code VaultAudit} port's separate concern.
 *
 * @param viewer who opened the vault (the owner, or staff for the admin form)
 * @param owner whose vault was opened
 * @param index the one-based vault number opened
 * @param at when the vault was opened
 */
public record VaultOpened(PlayerRef viewer, PlayerRef owner, int index, Instant at) implements VaultEvent {

    public VaultOpened {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(at, "at");
        if (index < 1) {
            throw new IllegalArgumentException("vault index must be at least 1: " + index);
        }
    }
}
