package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.ClaimProvider;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@link ClaimProvider} backed by the in-house uxmClaims plugin, reached <b>entirely by reflection</b>: there
 * is no compile dependency on uxmClaims, so this class loads and runs whether or not uxmClaims is present.
 *
 * <p>The API chain runs through the {@code UxmClaimsHook}: {@code UxmClaimBukkitAPI.getInstance()} →
 * {@code claimFacade()} → {@code findByLocation(new com.uxplima.claim.domain.model.vo.Location(world, x, y, z))}
 * → a {@code Claim} exposing {@code isOwner(UUID)}, {@code findMemberByUid(UUID)} (an {@link Optional}) and
 * {@code hasBanByUid(UUID)}. Trust is owner-or-member; ownership is the {@code isOwner(UUID)} predicate; ban is
 * {@code hasBanByUid}. The {@code Claim} exposes no single owner-UUID accessor, so {@link ClaimLookup#owner()}
 * keeps its empty default while {@link ClaimLookup#isOwner(java.util.UUID)} still answers ownership.
 *
 * <p>{@link #active()} is {@code true} only when the API class resolves and {@code getInstance()} is non-null.
 * Reflective handles are resolved on first success and cached. Any reflective failure logs once and degrades
 * to inactive/empty: it never propagates.
 */
@NullMarked
public final class UxmClaimsClaimProvider implements ClaimProvider {

    private static final String API_CLASS = "com.uxplima.claim.bukkit.api.UxmClaimBukkitAPI";
    private static final String LOCATION_CLASS = "com.uxplima.claim.domain.model.vo.Location";
    private static final int SENTINEL_Y = 64;

    private final Logger log;

    // Cached reflective handles, populated on the first successful lookup. Null until then; a resolution
    // failure leaves them null so the provider stays inactive without re-logging on every call.
    private @Nullable Method getInstance;
    private @Nullable Method claimFacade;
    private @Nullable Method findByLocation;
    private @Nullable Constructor<?> locationCtor;
    private @Nullable Method isOwner;
    private @Nullable Method findMemberByUid;
    private @Nullable Method hasBanByUid;
    private boolean unavailableLogged;

    public UxmClaimsClaimProvider(Plugin plugin, Server server, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean active() {
        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            Method instance = apiClass.getMethod("getInstance");
            return instance.invoke(null) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public Optional<ClaimLookup> claimAt(WorldRef world, int blockX, int blockZ) {
        Objects.requireNonNull(world, "world");
        try {
            return lookup(world, blockX, blockZ);
        } catch (Throwable t) {
            logUnavailableOnce(t);
            return Optional.empty();
        }
    }

    private Optional<ClaimLookup> lookup(WorldRef world, int blockX, int blockZ) throws Exception {
        resolveHandles();
        Object api = requireHandle(getInstance).invoke(null);
        if (api == null) {
            return Optional.empty();
        }
        Object facade = requireHandle(claimFacade).invoke(api);
        if (facade == null) {
            return Optional.empty();
        }
        // uxmClaims resolves claims by 2D column; Y is not used for matching. SENTINEL_Y avoids a
        // region-bound getHighestBlockYAt() call, making the proximity scan safe on Folia.
        Object location = requireCtor(locationCtor)
                .newInstance(world.name(), (double) blockX, (double) SENTINEL_Y, (double) blockZ);
        Object claimOpt = requireHandle(findByLocation).invoke(facade, location);
        if (!(claimOpt instanceof Optional<?> opt) || opt.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new UxmClaimLookup(opt.get()));
    }

    private void resolveHandles() throws ClassNotFoundException, NoSuchMethodException {
        if (getInstance != null) {
            return;
        }
        Class<?> apiClass = Class.forName(API_CLASS);
        Class<?> locationClass = Class.forName(LOCATION_CLASS);
        Class<?> claimClass = Class.forName("com.uxplima.claim.domain.model.Claim");
        Method facadeAccessor = apiClass.getMethod("claimFacade");
        getInstance = apiClass.getMethod("getInstance");
        claimFacade = facadeAccessor;
        findByLocation = facadeAccessor.getReturnType().getMethod("findByLocation", locationClass);
        locationCtor = locationClass.getConstructor(String.class, double.class, double.class, double.class);
        isOwner = claimClass.getMethod("isOwner", UUID.class);
        findMemberByUid = claimClass.getMethod("findMemberByUid", UUID.class);
        hasBanByUid = claimClass.getMethod("hasBanByUid", UUID.class);
    }

    private void logUnavailableOnce(Throwable t) {
        if (!unavailableLogged) {
            unavailableLogged = true;
            log.warn("event=claim_lookup_failed provider=uxmclaims reason={}", t);
        }
    }

    private static Method requireHandle(@Nullable Method method) {
        return Objects.requireNonNull(method, "reflective method handle not resolved");
    }

    private static Constructor<?> requireCtor(@Nullable Constructor<?> ctor) {
        return Objects.requireNonNull(ctor, "reflective constructor handle not resolved");
    }

    /** Adapts a reflectively-held uxmClaims {@code Claim} to the port's read-only claim view. */
    private final class UxmClaimLookup implements ClaimLookup {

        private final Object claim;

        private UxmClaimLookup(Object claim) {
            this.claim = claim;
        }

        @Override
        public boolean isTrusted(UUID player) {
            try {
                if (Boolean.TRUE.equals(requireHandle(isOwner).invoke(claim, player))) {
                    return true;
                }
                Object member = requireHandle(findMemberByUid).invoke(claim, player);
                return member instanceof Optional<?> opt && opt.isPresent();
            } catch (Throwable t) {
                logUnavailableOnce(t);
                return false;
            }
        }

        @Override
        public boolean isBanned(UUID player) {
            try {
                return Boolean.TRUE.equals(requireHandle(hasBanByUid).invoke(claim, player));
            } catch (Throwable t) {
                logUnavailableOnce(t);
                return false;
            }
        }

        @Override
        public boolean isOwner(UUID player) {
            try {
                return Boolean.TRUE.equals(requireHandle(isOwner).invoke(claim, player));
            } catch (Throwable t) {
                logUnavailableOnce(t);
                return false;
            }
        }
    }
}
