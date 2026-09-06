package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.ClaimProvider;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;

/**
 * Discovers which land-claim plugins are installed and binds a {@link CompositeClaimProvider} over every one
 * that is present and left enabled in config, or the no-op provider when none applies, mirroring how {@code
 * MapMarkerPublishers} discovers a map plugin and {@code ForeignEconomyProviders} discovers an economy
 * provider.
 *
 * <p>Every candidate (uxmClaims, Lands, GriefPrevention, GriefDefender, ExcellentClaims, SimpleClaimSystem,
 * RClaim, XClaim, Homestead, WorldGuard, Towny, KingdomsX, HuskClaims, HuskTowns, Factions, BentoBox, Residence,
 * PlotSquared,
 * SuperiorSkyblock2)
 * is constructed and asked {@link ClaimProvider#active()}; those that are both
 * active and enabled become composite members, so a server running two claim plugins consults both and their
 * answers are folded per {@link ClaimProvidersConfig#combine()}. Ordering no longer matters. Constructing a
 * candidate never loads its plugin SDK (each typed provider keeps its references behind its own present-guard,
 * and the reflective providers touch no SDK type at class-load time), so probing them on a server without any
 * claim plugin is safe and the no-op {@link #INACTIVE} provider is returned.
 */
@NullMarked
public final class ClaimProviders {

    private ClaimProviders() {}

    private static final ClaimProvider INACTIVE = new InactiveClaimProvider();

    /**
     * The one ordered registry of claim-provider candidates: each pairs a {@code claims.providers} config key
     * with the factory that builds its (lazily-guarded) provider. It is the single source of truth for the
     * provider set. {@link #detectAll} folds every entry into the composite and {@link #candidateKeys()}
     * exposes their keys, so a provider added here is the one edit {@code ClaimProviderCoverageDriftTest}
     * cross-checks against {@code config.conf} and {@code paper-plugin.yml}, failing the build if either the
     * discoverable toggle line or the load-order dependency was forgotten.
     */
    private static final List<Registration> REGISTRY = List.of(
            new Registration("uxmclaims", UxmClaimsClaimProvider::new),
            new Registration("lands", LandsClaimProvider::new),
            new Registration("griefprevention", GriefPreventionClaimProvider::new),
            new Registration("griefdefender", GriefDefenderClaimProvider::new),
            new Registration("excellentclaims", ExcellentClaimsClaimProvider::new),
            new Registration("simpleclaimsystem", SimpleClaimSystemClaimProvider::new),
            new Registration("rclaim", RClaimClaimProvider::new),
            new Registration("xclaim", XClaimClaimProvider::new),
            new Registration("homestead", HomesteadClaimProvider::new),
            new Registration("worldguard", WorldGuardClaimProvider::new),
            new Registration("towny", TownyClaimProvider::new),
            new Registration("kingdoms", KingdomsClaimProvider::new),
            new Registration("huskclaims", HuskClaimsClaimProvider::new),
            new Registration("husktowns", HuskTownsClaimProvider::new),
            new Registration("factions", FactionsClaimProvider::new),
            new Registration("bentobox", BentoBoxClaimProvider::new),
            new Registration("residence", ResidenceClaimProvider::new),
            new Registration("plotsquared", PlotSquaredClaimProvider::new),
            new Registration("superiorskyblock", SuperiorSkyblockClaimProvider::new));

    /**
     * Binds a composite over every claim plugin that is both installed-and-active and enabled in
     * {@code config}, or the no-op provider when none qualifies. The returned provider's answers are folded
     * per {@link ClaimProvidersConfig#combine()}.
     */
    public static ClaimProvider detectAll(ClaimProvidersConfig config, Plugin plugin, Server server, Logger log) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(log, "log");

        List<Candidate> candidates = REGISTRY.stream()
                .map(registration ->
                        new Candidate(registration.key(), registration.factory().create(plugin, server, log)))
                .toList();
        return compose(config, candidates, log);
    }

    /**
     * The {@code claims.providers} key of every registered candidate, in registry order. This is the single
     * source of truth two surfaces read rather than keeping their own copy: {@link ClaimProvidersConfig#from}
     * validates operator keys against it (so a typo'd key that toggles nothing is flagged), and
     * {@code ClaimProviderCoverageDriftTest} walks it to keep the registry, {@code config.conf} and
     * {@code paper-plugin.yml} in lockstep.
     */
    public static List<String> candidateKeys() {
        return REGISTRY.stream().map(Registration::key).toList();
    }

    /**
     * Folds the candidates that are both {@link ClaimProvidersConfig#enabled(String) enabled} and
     * {@link ClaimProvider#active() active} into a composite, or returns the no-op provider when none qualifies.
     * Split from {@link #detectAll} so the enable/active selection can be exercised with fake candidates.
     */
    static ClaimProvider compose(ClaimProvidersConfig config, List<Candidate> candidates, Logger log) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(log, "log");
        List<ClaimProvider> members = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) {
            if (config.enabled(candidate.configKey()) && candidate.provider().active()) {
                log.info(
                        "event=claim_provider_bound provider={} combine={}",
                        candidate.provider().getClass().getSimpleName(),
                        config.combine().configName());
                members.add(candidate.provider());
            }
        }
        return members.isEmpty() ? INACTIVE : new CompositeClaimProvider(members, config.combine());
    }

    /** A discovery candidate: its {@code claims.providers} config key paired with the (lazily-guarded) provider. */
    record Candidate(String configKey, ClaimProvider provider) {

        Candidate {
            Objects.requireNonNull(configKey, "configKey");
            Objects.requireNonNull(provider, "provider");
        }
    }

    /** Builds a candidate's provider from the wiring context; every claim provider shares this constructor shape. */
    @FunctionalInterface
    private interface ProviderFactory {
        ClaimProvider create(Plugin plugin, Server server, Logger log);
    }

    /** A registry entry: a {@code claims.providers} key paired with the factory that builds its provider. */
    private record Registration(String key, ProviderFactory factory) {

        Registration {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(factory, "factory");
        }
    }

    /** The provider used when no claim plugin is installed: inactive, so every policy check short-circuits. */
    private static final class InactiveClaimProvider implements ClaimProvider {

        @Override
        public boolean active() {
            return false;
        }

        @Override
        public Optional<ClaimLookup> claimAt(WorldRef world, int blockX, int blockZ) {
            Objects.requireNonNull(world, "world");
            return Optional.empty();
        }
    }
}
