package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

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
 * {@link ClaimProvider} backed by HuskClaims, reached <b>entirely by reflection</b>: there is no compile
 * dependency on HuskClaims, so this class loads and runs whether or not it is present.
 *
 * <p>The lookup is {@code BukkitHuskClaimsAPI.getInstance().getClaimAt(position)} over a position adapted from a
 * Bukkit location by HuskClaims' own {@code BukkitHuskClaims.Adapter}. An empty answer is unclaimed ground, which
 * stays free. HuskClaims claims are block-region rather than chunk shaped, but they are height-independent, so the
 * lookup uses a constant Y and never asks for a height, keeping it off the region-bound calls that are unsafe on
 * Folia.
 *
 * <p>Trust is the claim owner or any user in {@code Claim.getTrustedUsers()}, which is HuskClaims' whole
 * trust-level table keyed by player: any level in it is enough to place a home, exactly as an "added" player is on
 * the other providers. An admin claim has no owner, and {@link ClaimLookup#owner()} then answers empty.
 *
 * <p>{@link ClaimLookup#isBanned} is always {@code false}. HuskClaims does have per-user bans, but asking about
 * one needs a HuskClaims {@code User}, which can only be built from a live plugin handle we deliberately do not
 * hold; HuskClaims enforces its own bans, and our gate needs only the trust answer.
 *
 * <p>{@link #active()} consults only the plugin manager, so constructing this provider and asking whether it is
 * active names no {@code net.william278} type. The API chain runs lazily inside {@link #claimAt} past that guard,
 * and any reflective failure logs once and degrades to empty rather than propagating.
 */
@NullMarked
public final class HuskClaimsClaimProvider implements ClaimProvider {

    private static final String HUSKCLAIMS = "HuskClaims";
    private static final String API_CLASS = "net.william278.huskclaims.api.BukkitHuskClaimsAPI";
    private static final String ADAPTER_CLASS = "net.william278.huskclaims.BukkitHuskClaims$Adapter";

    // HuskClaims claims span the full height of their footprint, so a constant Y keeps the lookup off
    // getHighestBlockYAt(), which is region-bound and unsafe on Folia, matching the other providers.
    private static final int CLAIM_LOOKUP_Y = 64;

    private final Server server;
    private final Logger log;
    private final AtomicBoolean warned = new AtomicBoolean();

    public HuskClaimsClaimProvider(Plugin plugin, Server server, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean active() {
        Plugin huskClaims = server.getPluginManager().getPlugin(HUSKCLAIMS);
        return huskClaims != null && huskClaims.isEnabled();
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
            Object position = adapt(new Location(bukkitWorld, blockX, CLAIM_LOOKUP_Y, blockZ));
            Object api = Class.forName(API_CLASS).getMethod("getInstance").invoke(null);
            Object claim = unwrap(singleArg(api, "getClaimAt", position).invoke(api, position));
            if (claim == null) {
                return Optional.empty();
            }
            return Optional.of(new HuskClaimsClaimLookup(new ReflectiveClaimView(claim)));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            degrade(failure);
            return Optional.empty();
        }
    }

    /** {@code BukkitHuskClaims.Adapter.adapt(Location)}, HuskClaims' own Bukkit-to-position conversion. */
    private static Object adapt(Location location) throws ReflectiveOperationException {
        Object position =
                Class.forName(ADAPTER_CLASS).getMethod("adapt", Location.class).invoke(null, location);
        return Objects.requireNonNull(position, "HuskClaims adapted a location to null");
    }

    /**
     * The public single-argument method named {@code name} on {@code target} whose parameter accepts {@code arg}.
     * The position type is HuskClaims' own and is never named here, so the overload is selected by what the adapted
     * position actually is rather than by a type we would have to declare.
     */
    private static Method singleArg(Object target, String name, Object arg) throws ReflectiveOperationException {
        for (Method candidate : target.getClass().getMethods()) {
            if (candidate.getName().equals(name)
                    && candidate.getParameterCount() == 1
                    && candidate.getParameterTypes()[0].isInstance(arg)) {
                return candidate;
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + name);
    }

    /** The value inside an {@link Optional} answer, or {@code null} for anything else (including an empty one). */
    private static @Nullable Object unwrap(@Nullable Object answer) {
        return answer instanceof Optional<?> optional ? optional.orElse(null) : null;
    }

    /**
     * Log the first lookup failure and go quiet. Catches a {@link LinkageError} as well as a reflective or runtime
     * failure: a present-but-version-mismatched claim plugin can throw {@code NoClassDefFoundError} mid-reflection,
     * and a claim gate must degrade to "unclaimed" rather than crash the caller.
     */
    private void degrade(Throwable failure) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=claim_lookup_failed provider=huskclaims reason={}", failure.toString());
        }
    }

    /**
     * One HuskClaims claim's owner and trust table, decoupled from reflection so the ownership and trust fold can
     * be exercised without a live HuskClaims.
     */
    interface ClaimView {

        /** The claim's owner, or empty for an admin claim, which nobody owns. */
        Optional<UUID> owner();

        /** Every player with any trust level on the claim. */
        Set<UUID> trusted();
    }

    /** Adapts a HuskClaims claim to the port's read-only claim view. */
    record HuskClaimsClaimLookup(ClaimView claim) implements ClaimLookup {

        HuskClaimsClaimLookup {
            Objects.requireNonNull(claim, "claim");
        }

        @Override
        public boolean isOwner(UUID player) {
            Objects.requireNonNull(player, "player");
            return claim.owner().map(player::equals).orElse(false);
        }

        @Override
        public boolean isTrusted(UUID player) {
            Objects.requireNonNull(player, "player");
            return isOwner(player) || claim.trusted().contains(player);
        }

        @Override
        public Optional<UUID> owner() {
            return claim.owner();
        }

        @Override
        public boolean isBanned(UUID player) {
            Objects.requireNonNull(player, "player");
            // Asking HuskClaims about a ban needs a User built from a live plugin handle; HuskClaims enforces its
            // own bans, and this gate only needs trust.
            return false;
        }
    }

    /** Reflective {@link ClaimView} over a HuskClaims {@code Claim}, degrading to empty on any failure. */
    private final class ReflectiveClaimView implements ClaimView {

        private final Object claim;

        private ReflectiveClaimView(Object claim) {
            this.claim = claim;
        }

        @Override
        public Optional<UUID> owner() {
            try {
                Object owner = unwrap(claim.getClass().getMethod("getOwner").invoke(claim));
                return owner instanceof UUID id ? Optional.of(id) : Optional.empty();
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                degrade(failure);
                return Optional.empty();
            }
        }

        @Override
        public Set<UUID> trusted() {
            try {
                Object users = claim.getClass().getMethod("getTrustedUsers").invoke(claim);
                if (!(users instanceof Map<?, ?> table)) {
                    return Set.of();
                }
                return table.keySet().stream()
                        .filter(UUID.class::isInstance)
                        .map(UUID.class::cast)
                        .collect(Collectors.toUnmodifiableSet());
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                degrade(failure);
                return Set.of();
            }
        }
    }
}
