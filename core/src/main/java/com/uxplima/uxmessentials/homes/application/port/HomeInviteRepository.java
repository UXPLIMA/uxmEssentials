package com.uxplima.uxmessentials.homes.application.port;

import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Outbound port for the per-home guest list. The set of players an owner has invited to a private home,
 * keyed by {@code (owner, slot)}. A private home is reachable by its owner and by any invited player; a
 * public home needs no invites. Invites are durable storage, not transient state, so they survive a
 * restart and a world rollback the way the home itself does.
 *
 * <p>Removing a home cascades to its invites: the application layer calls {@link #removeAll} after a single
 * slot is deleted and {@link #removeAllForOwner} after an owner's whole set is cleared, so no orphaned
 * guest rows outlive the home they were granted against.
 */
@NullMarked
public interface HomeInviteRepository {

    /** The UUIDs the owner has invited to the home in {@code slot}; empty when none. */
    Set<UUID> invites(PlayerRef owner, HomeSlot slot);

    /** Grant {@code invited} access to the owner's home in {@code slot}; a no-op if already invited. */
    void addInvite(PlayerRef owner, HomeSlot slot, UUID invited);

    /** Revoke {@code invited}'s access to the owner's home in {@code slot}; a no-op if not invited. */
    void removeInvite(PlayerRef owner, HomeSlot slot, UUID invited);

    /** Clear every invite on the owner's home in {@code slot}; used when that slot is deleted. */
    void removeAll(PlayerRef owner, HomeSlot slot);

    /** Clear every invite on every one of the owner's homes; used when their whole set is cleared. */
    void removeAllForOwner(PlayerRef owner);
}
