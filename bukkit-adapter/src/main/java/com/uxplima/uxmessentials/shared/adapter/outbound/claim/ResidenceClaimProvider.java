package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.ClaimProvider;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@link ClaimProvider} backed by Residence, reached <b>entirely by reflection</b>. There is no compile
 * dependency on Residence, so this class loads and runs whether or not Residence is present. Here a "claim" is
 * a residence: the block belongs to a claim exactly when Residence reports a residence covering it, so a warp
 * can be gated to a residence you own or have build rights in.
 *
 * <p>The lookup is {@code Residence.getResidenceManager().getByLoc(Location)}, which returns {@code null} in
 * unclaimed space or a non-Residence world; a {@code null} residence reads as unclaimed and {@link #claimAt}
 * returns empty, so unclaimed land stays free. A covering residence is mapped to the port's owner/trust model
 * against Residence's owner-name and build-flag concepts.
 *
 * <p>Residence is <b>name-keyed</b>, not UUID-keyed: a residence records its owner as a name
 * ({@code ClaimedResidence.getOwner()}) and grants access by name ({@code ResidencePermissions.playerHas(name,
 * flag, def)}). The port hands us a {@link UUID}, so every lookup first resolves that UUID to the server-known
 * name via {@code server.getOfflinePlayer(uuid).getName()}. That read consults the local user cache and is
 * safe on the region thread for a player the server has seen; for a UUID it has never seen the name is
 * {@code null}, which we treat as "no match": an unknown player is neither owner nor trusted.
 *
 * <p>Ownership is the resolved name matching the residence owner name, compared case-insensitively because
 * Residence itself compares owner names that way. Trust widens ownership to anyone the residence grants the
 * {@code build} flag, matching the owner-or-member reading the other providers use.
 *
 * <p>{@link ClaimLookup#owner()} stays empty: Residence exposes only a name, and resolving a name back to a
 * UUID for an offline player is unreliable, so there is no dependable single owner UUID to hand back, callers
 * gate on {@link ClaimLookup#isOwner(UUID)} instead, as they do for WorldGuard. Residence has no per-player ban
 * list (access is governed by the ambiguous {@code move} flag, which cannot be told apart from a global deny)
 * so {@link ClaimLookup#isBanned} is always {@code false}, matching WorldGuard's lack of a ban concept.
 *
 * <p>{@link #active()} consults only the plugin manager, so constructing this provider and asking whether it is
 * active names no {@code com.bekvon} type: a server without Residence loads none of its classes. The Residence
 * API chain runs lazily inside {@link #claimAt} past that guard, and any reflective failure logs once and
 * degrades to empty rather than propagating.
 */
@NullMarked
public final class ResidenceClaimProvider implements ClaimProvider {

    private static final String RESIDENCE = "Residence";
    private static final String RESIDENCE_CLASS = "com.bekvon.bukkit.residence.Residence";

    // Residences are x/z-bounded areas that (for warp-placement purposes) claim their column regardless of
    // height, so a constant Y keeps the lookup off getHighestBlockYAt(), which is region-bound and unsafe on
    // Folia, matching the other providers.
    private static final int CLAIM_LOOKUP_Y = 64;

    private final Server server;
    private final Logger log;
    private final AtomicBoolean warned = new AtomicBoolean();

    public ResidenceClaimProvider(Plugin plugin, Server server, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean active() {
        Plugin residence = server.getPluginManager().getPlugin(RESIDENCE);
        return residence != null && residence.isEnabled();
    }

    @Override
    public Optional<ClaimLookup> claimAt(WorldRef world, int blockX, int blockZ) {
        Objects.requireNonNull(world, "world");
        if (!active()) {
            return Optional.empty();
        }
        World bukkitWorld = server.getWorld(world.uid());
        if (bukkitWorld == null) {
            return Optional.empty();
        }
        try {
            Location location = new Location(bukkitWorld, blockX, CLAIM_LOOKUP_Y, blockZ);
            Object residence = residenceAt(location);
            if (residence == null) {
                return Optional.empty();
            }
            return Optional.of(new ResidenceClaimLookup(new ReflectiveResidenceView(residence)));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            degrade(failure);
            return Optional.empty();
        }
    }

    /** {@code Residence.getResidenceManager().getByLoc(Location)}, the residence at the block, or {@code null}. */
    private static @Nullable Object residenceAt(Location location) throws ReflectiveOperationException {
        Object manager =
                Class.forName(RESIDENCE_CLASS).getMethod("getResidenceManager").invoke(null);
        if (manager == null) {
            return null;
        }
        return manager.getClass().getMethod("getByLoc", Location.class).invoke(manager, location);
    }

    /**
     * Log the first lookup failure and go quiet. Catches a {@link LinkageError} as well as a reflective or runtime
     * failure: a present-but-version-mismatched claim plugin can throw {@code NoClassDefFoundError} mid-reflection, and
     * a claim gate must degrade to "unclaimed" rather than crash the caller.
     */
    private void degrade(Throwable failure) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=claim_lookup_failed provider=residence reason={}", failure.toString());
        }
    }

    /**
     * One Residence's name-based answers, decoupled from reflection so the case-insensitive owner match, the
     * build-flag trust widening and, crucially, the UUID-to-name resolution and its {@code null} case can be
     * exercised without a live Residence. Residence is name-keyed, so resolving the port's UUID to a name is part
     * of every decision and belongs on this seam alongside the owner-name and flag questions.
     */
    interface ResidenceView {

        /** The residence owner's Residence name. */
        String ownerName();

        /** Whether the player named {@code playerName} holds {@code flag} on this residence. */
        boolean playerHas(String playerName, String flag);

        /** The server-known name for {@code player}, or empty when the server has never seen this UUID. */
        Optional<String> resolveName(UUID player);
    }

    /** Reflective {@link ResidenceView} over a Residence {@code ClaimedResidence}, degrading on any failure. */
    private final class ReflectiveResidenceView implements ResidenceView {

        private final Object residence;

        private ReflectiveResidenceView(Object residence) {
            this.residence = residence;
        }

        @Override
        public String ownerName() {
            try {
                Object owner = residence.getClass().getMethod("getOwner").invoke(residence);
                return owner instanceof String name ? name : "";
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                degrade(failure);
                return "";
            }
        }

        @Override
        public boolean playerHas(String playerName, String flag) {
            try {
                Object permissions =
                        residence.getClass().getMethod("getPermissions").invoke(residence);
                if (permissions == null) {
                    return false;
                }
                Object answer = permissions
                        .getClass()
                        .getMethod("playerHas", String.class, String.class, boolean.class)
                        .invoke(permissions, playerName, flag, false);
                return Boolean.TRUE.equals(answer);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                degrade(failure);
                return false;
            }
        }

        @Override
        public Optional<String> resolveName(UUID player) {
            // getOfflinePlayer(UUID) reads the local user cache; the name is null for a UUID the server has
            // never seen, and that null is the "unknown player, no match" case the lookup relies on.
            return Optional.ofNullable(server.getOfflinePlayer(player).getName());
        }
    }

    /** Adapts a Residence to the port's read-only claim view, resolving the port's UUID to a Residence name. */
    record ResidenceClaimLookup(ResidenceView residence) implements ClaimLookup {

        private static final String BUILD_FLAG = "build";

        ResidenceClaimLookup {
            Objects.requireNonNull(residence, "residence");
        }

        @Override
        public boolean isOwner(UUID player) {
            Objects.requireNonNull(player, "player");
            // A null resolved name (a UUID the server has never seen) reads as no match, never as ownership.
            return residence
                    .resolveName(player)
                    .map(name -> name.equalsIgnoreCase(residence.ownerName()))
                    .orElse(false);
        }

        @Override
        public boolean isTrusted(UUID player) {
            Objects.requireNonNull(player, "player");
            return residence
                    .resolveName(player)
                    .map(name -> name.equalsIgnoreCase(residence.ownerName()) || residence.playerHas(name, BUILD_FLAG))
                    .orElse(false);
        }

        @Override
        public Optional<UUID> owner() {
            // Residence exposes only an owner name; resolving a name back to a UUID for an offline player is
            // unreliable, so there is no dependable single owner UUID to return, callers gate on isOwner.
            return Optional.empty();
        }

        @Override
        public boolean isBanned(UUID player) {
            Objects.requireNonNull(player, "player");
            // Residence has no per-player ban list. Access is governed by the ambiguous "move" flag, which
            // cannot be told apart from a global deny, so no ban is inferred, matching WorldGuard.
            return false;
        }
    }
}
