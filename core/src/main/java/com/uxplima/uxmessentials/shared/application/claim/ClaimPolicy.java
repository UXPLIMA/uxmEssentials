package com.uxplima.uxmessentials.shared.application.claim;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.ClaimProvider;
import com.uxplima.uxmessentials.shared.application.port.ClaimProvider.ClaimLookup;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.domain.claim.ClaimDecision;
import org.jspecify.annotations.NullMarked;

/**
 * Provider-agnostic claim policy. Evaluates whether a player may place a home at a position or
 * teleport to one, based on the operator-configured {@link ClaimPolicySettings} and whatever the
 * injected {@link ClaimProvider} reports about the target block.
 *
 * <p>When the provider is inactive (no claim plugin loaded), every check returns
 * {@link ClaimDecision#ALLOWED} without touching the provider.
 */
@NullMarked
public final class ClaimPolicy {

    private final ClaimProvider provider;
    private final ClaimPolicySettings settings;

    public ClaimPolicy(ClaimProvider provider, ClaimPolicySettings settings) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Determines whether {@code player} may place a home at ({@code blockX}, {@code blockZ}) in
     * {@code world}. Applies the claim hook's canCreateHomeAt logic.
     *
     * <ul>
     *   <li>Provider inactive → {@code ALLOWED}.
     *   <li>Block inside a claim the player is banned from → {@code DENIED_ACCESS}, ahead of every
     *       trust or ownership test: a ban overrides membership.
     *   <li>Block inside a claim whose membership the player satisfies → {@code ALLOWED}. Membership
     *       is ownership alone when {@code ownerOnly} is set, otherwise owner-or-member.
     *   <li>Block inside a foreign claim and {@code blockForeignClaims} is on → {@code DENIED_FOREIGN}.
     *   <li>Block inside a foreign claim and {@code blockForeignClaims} is off → {@code ALLOWED}.
     *   <li>Block in unclaimed land and {@code requireClaim} is on → {@code DENIED_REQUIRED}.
     *   <li>Block in unclaimed land and {@code foreignChunkDistance} &gt; 0 → proximity check →
     *       {@code DENIED_TOO_CLOSE} when a claim the player is not a member of is within that chunk
     *       radius (membership honours {@code ownerOnly} the same way).
     *   <li>Otherwise → {@code ALLOWED}.
     * </ul>
     */
    public ClaimDecision canPlace(UUID player, WorldRef world, int blockX, int blockZ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(world, "world");

        if (!provider.active()) {
            return ClaimDecision.ALLOWED;
        }

        Optional<ClaimLookup> claimOpt = provider.claimAt(world, blockX, blockZ);
        if (claimOpt.isPresent()) {
            return withinClaimPlacement(claimOpt.get(), player);
        }

        // Block is in unclaimed land.
        if (settings.requireClaim()) {
            return ClaimDecision.DENIED_REQUIRED;
        }

        int distance = settings.foreignChunkDistance();
        if (distance > 0) {
            return checkProximity(player, world, blockX, blockZ, distance);
        }

        return ClaimDecision.ALLOWED;
    }

    /** Placement outcome when the target block already sits inside a claim. */
    private ClaimDecision withinClaimPlacement(ClaimLookup lookup, UUID player) {
        if (lookup.isBanned(player)) {
            return ClaimDecision.DENIED_ACCESS;
        }
        if (permitted(lookup, player)) {
            return ClaimDecision.ALLOWED;
        }
        // Foreign claim exists: either block or allow based on config.
        return settings.blockForeignClaims() ? ClaimDecision.DENIED_FOREIGN : ClaimDecision.ALLOWED;
    }

    /**
     * Determines whether {@code player} may teleport to ({@code blockX}, {@code blockZ}) in
     * {@code world}. Returns {@code ALLOWED} immediately when {@code checkTeleportAccess} is off.
     * When a claim covers the block, a player banned from it is {@code DENIED_ACCESS} ahead of the
     * trust test, and a player who is not trusted there is {@code DENIED_ACCESS} as well.
     */
    public ClaimDecision canAccess(UUID player, WorldRef world, int blockX, int blockZ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(world, "world");

        if (!provider.active()) {
            return ClaimDecision.ALLOWED;
        }

        if (!settings.checkTeleportAccess()) {
            return ClaimDecision.ALLOWED;
        }

        Optional<ClaimLookup> claimOpt = provider.claimAt(world, blockX, blockZ);
        if (claimOpt.isEmpty()) {
            // No claim at destination: claim may have been removed; allow teleport.
            return ClaimDecision.ALLOWED;
        }

        ClaimLookup lookup = claimOpt.get();
        if (lookup.isBanned(player)) {
            return ClaimDecision.DENIED_ACCESS;
        }
        return lookup.isTrusted(player) ? ClaimDecision.ALLOWED : ClaimDecision.DENIED_ACCESS;
    }

    /**
     * Whether ({@code blockX}, {@code blockZ}) in {@code world} falls inside <em>any</em> claim, the
     * player-agnostic presence check, independent of owner, trust, and every placement knob. An inactive
     * provider (no claim plugin) reports {@code false}. This is what {@link ClaimService#isProtected} answers
     * for random-teleport, whose shared pool has no owning player to run {@link #canPlace}/{@link #canAccess}
     * against.
     */
    public boolean isWithinClaim(WorldRef world, int blockX, int blockZ) {
        Objects.requireNonNull(world, "world");
        return provider.active() && provider.claimAt(world, blockX, blockZ).isPresent();
    }

    /**
     * Scans the chunk grid in the range {@code [-distance, distance]} around the base chunk of
     * ({@code blockX}, {@code blockZ}), skipping the centre. If any chunk contains a claim the
     * player is not a member of, returns {@code DENIED_TOO_CLOSE}. Membership honours {@code
     * ownerOnly} through {@link #permitted}, so an owner-only policy treats a neighbouring claim the
     * player is only trusted in as foreign.
     */
    private ClaimDecision checkProximity(UUID player, WorldRef world, int blockX, int blockZ, int distance) {
        int baseChunkX = blockX >> 4;
        int baseChunkZ = blockZ >> 4;

        for (int dx = -distance; dx <= distance; dx++) {
            for (int dz = -distance; dz <= distance; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                // Query a representative block inside the neighbouring chunk.
                int sampleX = (baseChunkX + dx) << 4;
                int sampleZ = (baseChunkZ + dz) << 4;
                Optional<ClaimLookup> nearby = provider.claimAt(world, sampleX, sampleZ);
                if (nearby.isPresent() && !permitted(nearby.get(), player)) {
                    return ClaimDecision.DENIED_TOO_CLOSE;
                }
            }
        }

        return ClaimDecision.ALLOWED;
    }

    /**
     * The in-claim membership test placement uses: ownership alone under {@code ownerOnly}, otherwise
     * owner-or-member. Both placement sites route through here so the two can never drift.
     */
    private boolean permitted(ClaimLookup lookup, UUID player) {
        return settings.ownerOnly() ? lookup.isOwner(player) : lookup.isTrusted(player);
    }
}
