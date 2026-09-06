package com.uxplima.uxmessentials.teleport.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.BiomeCatalog;
import com.uxplima.uxmessentials.teleport.application.port.RtpAreaSource;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.RtpSafeLocation;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;

/**
 * The {@code /rtp biome <biome>} use case: land the player in a specific biome. It resolves the operator-typed key
 * against the {@link BiomeCatalog} (an unknown biome is reported and nothing runs), redirects through the configured
 * fallback world when needed, gates cost and cooldown up front exactly like the plain {@code /rtp} (a player who
 * cannot pay or is on cooldown never triggers a search), then hands a biome-targeted {@link BiomeTargetedSearch} the
 * live area constrained to that biome. On a hit the player is teleported through the same charge-after-success engine
 * path as a normal RTP; when neither the per-biome pool slice nor the budgeted search turns one up, a clear message
 * explains that the biome could not be found within the budget.
 *
 * <p>The requester never waits on a chunk load: the search is asynchronous and the landing is applied on the
 * player's own region thread, so a rare biome that needs the full budget just takes a moment longer rather than
 * blocking a tick thread.
 */
public final class ResolveBiomeRtp {

    private final BiomeCatalog catalog;
    private final RtpAreaSource areas;
    private final WorldLookup worlds;
    private final TeleportSettings settings;
    private final TeleportEngine engine;
    private final BiomeTargetedSearch search;
    private final Notifier notifier;
    private final Scheduler scheduler;
    private final RtpRadiusTier radiusTier;

    public ResolveBiomeRtp(
            BiomeCatalog catalog,
            RtpAreaSource areas,
            WorldLookup worlds,
            TeleportSettings settings,
            TeleportEngine engine,
            BiomeTargetedSearch search,
            Notifier notifier,
            Scheduler scheduler,
            RtpRadiusTier radiusTier) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.areas = Objects.requireNonNull(areas, "areas");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.search = Objects.requireNonNull(search, "search");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.radiusTier = Objects.requireNonNull(radiusTier, "radiusTier");
    }

    /**
     * Resolve {@code rawBiome}, gate the player, and kick a biome-targeted search from {@code requestedWorld} (or its
     * fallback). Returns the up-front result; the landing or the not-found notice is applied asynchronously once the
     * search settles.
     */
    public Result<Unit, TeleportError> targeted(PlayerRef who, WorldRef requestedWorld, String rawBiome) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(requestedWorld, "requestedWorld");
        Objects.requireNonNull(rawBiome, "rawBiome");
        Optional<BiomeName> biome = catalog.resolve(rawBiome);
        if (biome.isEmpty()) {
            notifier.send(who, TeleportMessageKey.RTP_BIOME_UNKNOWN, Map.of("biome", rawBiome));
            return Result.err(TeleportError.RTP_BIOME_UNKNOWN);
        }
        Optional<WorldRef> resolved = resolveWorld(requestedWorld);
        if (resolved.isEmpty()) {
            notifier.send(who, TeleportMessageKey.RTP_DISALLOWED);
            return Result.err(TeleportError.RTP_WORLD_DISALLOWED);
        }
        Result<Unit, TeleportError> gate = engine.gateRandom(who);
        if (gate.isErr()) {
            return gate; // gateRandom already notified (cooldown / can't afford / jailed): no search kicked
        }
        Optional<SafeSearchArea> area = areas.areaFor(resolved.get());
        if (area.isEmpty()) {
            notifier.send(who, TeleportMessageKey.RTP_NO_LOCATION);
            return Result.err(TeleportError.RTP_NO_SAFE_LOCATION);
        }
        notifier.send(who, TeleportMessageKey.RTP_SEARCHING);
        // Bound the live search to this player's rtp.radius tier before constraining it to the biome; the shared
        // pool paths (plain /rtp, /rtp <world>) serve world-wide and carry no per-player radius.
        SafeSearchArea clamped = radiusTier.clamp(who, area.get());
        launch(who, clamped.withTargetBiome(biome.get()), biome.get());
        return Result.ok();
    }

    private void launch(PlayerRef who, SafeSearchArea targeted, BiomeName biome) {
        var ignored = search.locate(targeted, biome).thenAccept(found -> onLocated(who, biome, found));
    }

    private void onLocated(PlayerRef who, BiomeName biome, Optional<RtpSafeLocation> found) {
        if (found.isPresent()) {
            RtpSafeLocation location = found.get();
            scheduler.onEntity(who, () -> engine.launchRandom(who, Destination.at(location.position())));
        } else {
            scheduler.onEntity(
                    who,
                    () -> notifier.send(who, TeleportMessageKey.RTP_BIOME_NOT_FOUND, Map.of("biome", biome.value())));
        }
    }

    private Optional<WorldRef> resolveWorld(WorldRef requested) {
        if (areas.areaFor(requested).isPresent()) {
            return Optional.of(requested);
        }
        return settings.rtpFallbackWorld(requested)
                .flatMap(worlds::findByName)
                .filter(w -> areas.areaFor(w).isPresent());
    }
}
