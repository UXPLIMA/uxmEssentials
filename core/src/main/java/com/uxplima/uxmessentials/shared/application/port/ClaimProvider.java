package com.uxplima.uxmessentials.shared.application.port;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;

/**
 * Port through which claim-policy logic queries the active claim plugin, without coupling to any
 * specific provider SDK. The adapter layer supplies a concrete implementation wired to Lands,
 * GriefPrevention, or whichever plugin is present; the default (no provider installed) is the
 * always-inactive stub that returns {@code active() == false}.
 */
@NullMarked
public interface ClaimProvider {

    /**
     * Whether a claim plugin is currently active and available. When {@code false} every policy
     * check short-circuits to {@code ALLOWED} without querying the provider.
     */
    boolean active();

    /**
     * Returns a {@link ClaimLookup} for the claim that covers the block at
     * ({@code blockX}, {@code blockZ}) in {@code world}, or empty when no claim is present.
     *
     * <p>The coordinates are block-level (i.e. the integer floor of the world coordinates). The
     * implementation is expected to be fast. It is called on the region thread and may be called
     * repeatedly for proximity checks.
     */
    Optional<ClaimLookup> claimAt(WorldRef world, int blockX, int blockZ);

    /**
     * Thin, read-only view of a single claim sufficient for policy evaluation. The adapter
     * translates the claim-plugin's own model into these two predicates.
     */
    @NullMarked
    interface ClaimLookup {

        /**
         * Returns {@code true} when {@code player} is the owner of or a trusted member in this
         * claim and may therefore set a home here.
         */
        boolean isTrusted(UUID player);

        /**
         * Returns {@code true} when {@code player} has been explicitly banned from this claim and
         * should not be permitted to teleport to it.
         */
        boolean isBanned(UUID player);

        /**
         * Returns {@code true} when {@code player} owns this claim, as distinct from merely being a
         * trusted member of it. Callers that must gate an action on ownership, creating a public
         * warp on your own land, for instance, need this narrower question than {@link
         * #isTrusted(UUID)}, which answers owner-or-member.
         *
         * <p>This is the authoritative ownership check regardless of whether {@link #owner()} can
         * name a UUID: a provider whose model owns land collectively, or by team, may still answer
         * whether a given player is one of the owners.
         *
         * <p>The default preserves the legacy behaviour of treating any trusted player as an owner,
         * so providers written against the older two-method contract keep their existing meaning
         * until they supply a real owner check.
         */
        default boolean isOwner(UUID player) {
            Objects.requireNonNull(player, "player");
            return isTrusted(player);
        }

        /**
         * Returns the single owner of this claim, or empty when the claim has no one individual
         * owner. Empty is a legitimate answer, not a failure: a Towny town plot or any collectively
         * held claim is owned by a group, so no single UUID applies. Callers must therefore treat
         * empty as "no single owner here" and fall back to {@link #isOwner(UUID)} to decide whether
         * a specific player owns the land.
         */
        default Optional<UUID> owner() {
            return Optional.empty();
        }
    }
}
