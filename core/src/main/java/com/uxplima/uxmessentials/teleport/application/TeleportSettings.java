package com.uxplima.uxmessentials.teleport.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.ArrivalGraceSettings;
import com.uxplima.uxmessentials.teleport.domain.BackCapturePolicy;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.BlockTypeName;
import com.uxplima.uxmessentials.teleport.domain.CooldownStartPhase;
import com.uxplima.uxmessentials.teleport.domain.RespawnChain;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchPolicy;
import com.uxplima.uxmessentials.teleport.domain.TeleportCauseCategory;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.WarmupCancelToggles;
import com.uxplima.uxmessentials.teleport.domain.YBand;

/**
 * A typed read view over the teleport module's {@code teleport.conf} subtree. The use cases consult
 * this rather than dotted config paths, so the config keys live in exactly one place and a reload simply
 * reads a fresh view from the swapped {@link ConfigStore}. Every getter carries a sensible default so a
 * minimal config still yields working behaviour.
 *
 * <p>The store is the module-scoped config (rooted at {@code modules.teleport}); the keys below are
 * relative to that root.
 */
public final class TeleportSettings {

    private final ConfigStore config;

    public TeleportSettings(ConfigStore config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /** When a teleport cooldown's clock starts; default {@link CooldownStartPhase#TELEPORT}. */
    public CooldownStartPhase cooldownStartPhase() {
        String raw = config.getString("cooldown-start-phase", "teleport").trim().toUpperCase(java.util.Locale.ROOT);
        return switch (raw) {
            case "REQUEST" -> CooldownStartPhase.REQUEST;
            case "ACCEPT" -> CooldownStartPhase.ACCEPT;
            default -> CooldownStartPhase.TELEPORT;
        };
    }

    /** How long a {@code tpa} request stays pending before it expires; default 60s. */
    public Duration requestTtl() {
        return Duration.ofSeconds(Math.max(1, config.getInt("request-ttl-seconds", 60)));
    }

    /** True when a new request displaces a pending one rather than queueing behind it; default true. */
    public boolean singleRequestDisplace() {
        return config.getBoolean("single-request-displace", true);
    }

    /** The default teleport warmup in seconds when the player holds no tier node; default 3s. */
    public long defaultWarmupSeconds() {
        return Math.max(0, config.getInt("default-warmup", 3));
    }

    /** The default teleport cooldown in seconds when the player holds no tier node; default 5s. */
    public long defaultCooldownSeconds() {
        return Math.max(0, config.getInt("default-cooldown", 5));
    }

    /**
     * The per-verb cooldown override in seconds for {@code kind}, or {@code -1} to inherit. Read from
     * {@code cooldowns.<verb>} (e.g. {@code cooldowns.back}); a verb the config never names returns {@code -1}.
     * A non-negative value is that verb's fallback cooldown. The numbered {@code uxmessentials.tp.cooldown.<n>}
     * tier still refines it via the min-reducer; {@code -1} keeps the shared {@link #defaultCooldownSeconds()}.
     */
    public long verbCooldownOverrideSeconds(TeleportKind kind) {
        Objects.requireNonNull(kind, "kind");
        return cooldownVerb(kind)
                .map(verb -> config.getInt("cooldowns." + verb, -1))
                .orElse(-1);
    }

    /** The config verb name for {@code kind} under {@code cooldowns.<verb>}, or empty for an unkeyed kind. */
    private static java.util.Optional<String> cooldownVerb(TeleportKind kind) {
        return switch (kind) {
            case BACK -> java.util.Optional.of("back");
            case HOME -> java.util.Optional.of("home");
            case WARP -> java.util.Optional.of("warp");
            case SPAWN -> java.util.Optional.of("spawn");
            case RANDOM -> java.util.Optional.of("rtp");
            case REQUEST, RESPAWN, ADMIN, POSITIONAL -> java.util.Optional.empty();
        };
    }

    /** The per-axis warmup cancel toggles; move-cancel defaults on, rotation, damage and interact off. */
    public WarmupCancelToggles cancelToggles() {
        return new WarmupCancelToggles(
                config.getBoolean("warmup.cancel-on-move", true),
                config.getBoolean("warmup.cancel-on-rotate", false),
                config.getBoolean("warmup.cancel-on-damage", false),
                config.getBoolean("warmup.cancel-on-interact", false),
                Math.max(0.0, config.getDouble("warmup.move-threshold", 0.0)));
    }

    /** Whether a teleport destination is snapped to the block centre (x+0.5, z+0.5); default true. */
    public boolean teleportToCenter() {
        return config.getBoolean("teleport-to-center", true);
    }

    /** Whether to flash a short title on the player's screen when a teleport lands. */
    public boolean arrivalTitle() {
        return config.getBoolean("arrival-title", true);
    }

    /**
     * The arrival sound/particle effect for a {@code kind} teleport, or {@link ArrivalEffect#NONE} when the
     * {@code arrival-effects} master toggle is off or the verb has no enabled {@code effects.<verb>} block.
     * Read from {@code effects.<verb>.{enabled,sound,sound-volume,sound-pitch,particle,particle-count,particle-spread}}.
     */
    public ArrivalEffect arrivalEffect(TeleportKind kind) {
        Objects.requireNonNull(kind, "kind");
        if (!config.getBoolean("arrival-effects", true)) {
            return ArrivalEffect.NONE;
        }
        String base = "effects." + configKey(kind);
        if (!config.getBoolean(base + ".enabled", false)) {
            return ArrivalEffect.NONE;
        }
        return new ArrivalEffect(
                true,
                config.getString(base + ".sound", ""),
                config.getDouble(base + ".sound-volume", 1.0),
                config.getDouble(base + ".sound-pitch", 1.0),
                config.getString(base + ".particle", ""),
                config.getInt(base + ".particle-count", 0),
                config.getDouble(base + ".particle-spread", 0.0));
    }

    /** The list of arrival messages configured for a specific teleport kind. */
    public List<String> getArrivalMessages(TeleportKind kind) {
        Objects.requireNonNull(kind, "kind");
        String key = configKey(kind);

        // Check if the old configuration map structure is present:
        // arrival-messages.home.type and arrival-messages.home.message
        String oldType =
                config.getString("arrival-messages." + key + ".type", "").trim();
        if (!oldType.isEmpty()) {
            boolean enabled = config.getBoolean("arrival-messages." + key + ".enabled", true);
            if (!enabled) {
                return List.of();
            }
            String message = config.getString("arrival-messages." + key + ".message", "teleport.arrived.title")
                    .trim();
            return List.of(oldType.toUpperCase(java.util.Locale.ROOT) + ";" + message);
        }

        List<String> defaultMsgs = defaultMessages(kind);
        return config.getStringList("arrival-messages." + key, defaultMsgs);
    }

    private static String configKey(TeleportKind kind) {
        return switch (kind) {
            case REQUEST -> "request";
            case BACK -> "back";
            case RANDOM -> "rtp";
            case SPAWN -> "spawn";
            case HOME -> "home";
            case WARP -> "warp";
            case RESPAWN -> "respawn";
            case ADMIN -> "admin";
            case POSITIONAL -> "positional";
        };
    }

    private static List<String> defaultMessages(TeleportKind kind) {
        return switch (kind) {
            case HOME, SPAWN, RANDOM, BACK, REQUEST, POSITIONAL -> List.of("TITLE;teleport.arrived.title;150;700;300");
            case WARP, RESPAWN, ADMIN -> List.of();
        };
    }

    /** The vanilla teleport causes that must not overwrite a player's {@code /back} point. */
    public BackCapturePolicy backCapturePolicy() {
        List<String> raw = config.getStringList("back.ignored-causes", List.of("ender_pearl", "chorus_fruit"));
        java.util.Set<TeleportCauseCategory> ignored = raw.stream()
                .map(TeleportSettings::parseCause)
                .flatMap(java.util.Optional::stream)
                .collect(java.util.stream.Collectors.toCollection(
                        () -> java.util.EnumSet.noneOf(TeleportCauseCategory.class)));
        return new BackCapturePolicy(ignored);
    }

    private static java.util.Optional<TeleportCauseCategory> parseCause(String raw) {
        String token = raw.trim().toUpperCase(java.util.Locale.ROOT);
        try {
            return java.util.Optional.of(TeleportCauseCategory.valueOf(token));
        } catch (IllegalArgumentException unknown) {
            return java.util.Optional.empty();
        }
    }

    /** Whether {@code /back} may return to a death location at all (also gated per-player by permission). */
    public boolean backOnDeathEnabled() {
        return config.getBoolean("back.on-death", true);
    }

    /**
     * The minimum wait in seconds after a death before {@code /back} may return to the death point; {@code 0}
     * (the default) disables the delay. The death point is still recorded immediately on death; only the
     * {@code /back} return to it is held off until this window elapses ({@code back.death-delay-seconds}).
     */
    public long backDeathDelaySeconds() {
        return Math.max(0, config.getInt("back.death-delay-seconds", 0));
    }

    /** The safe-search policy (excluded biomes, avoided blocks, Y band, protected-land avoidance) across worlds. */
    public SafeSearchPolicy safeSearchPolicy() {
        List<String> excluded = config.getStringList("rtp.excluded-biomes", List.of("ocean", "deep_ocean", "river"));
        List<String> avoid = config.getStringList("rtp.avoid-blocks", List.of("lava", "magma_block", "fire", "cactus"));
        return new SafeSearchPolicy(
                excluded.stream().map(BiomeName::of).collect(java.util.stream.Collectors.toSet()),
                avoid.stream().map(BlockTypeName::of).collect(java.util.stream.Collectors.toSet()),
                rtpYBand(),
                respectClaims() || respectWorldguard());
    }

    /**
     * Whether {@code /rtp} avoids landing inside land claims ({@code rtp.respect-claims}, default {@code true}). When
     * off, the claim seam is skipped and claimed land is treated as unprotected, the graceful default a server with
     * no claim plugin also lands on.
     */
    public boolean respectClaims() {
        return config.getBoolean("rtp.respect-claims", true);
    }

    /**
     * Whether {@code /rtp} avoids landing inside WorldGuard regions ({@code rtp.respect-worldguard}, default {@code
     * true}). When off, or when WorldGuard is absent, region land is treated as unprotected.
     */
    public boolean respectWorldguard() {
        return config.getBoolean("rtp.respect-worldguard", true);
    }

    /** The min/max landing-Y band an RTP candidate must fall within; unbounded when neither key is set. */
    public YBand rtpYBand() {
        int minY = config.getInt("rtp.min-y", Integer.MIN_VALUE);
        int maxY = config.getInt("rtp.max-y", Integer.MAX_VALUE);
        if (maxY < minY) {
            return YBand.unbounded();
        }
        return new YBand(minY, maxY);
    }

    /** Whether a genuinely new player is moved to the resolved spawn on first join; default on. */
    public boolean spawnOnFirstJoin() {
        return config.getBoolean("spawn.first-join", true);
    }

    /** Whether every join is moved to the resolved spawn; default off. */
    public boolean spawnOnEveryJoin() {
        return config.getBoolean("spawn.every-join", false);
    }

    /** Permission which exempts a player from automatic join-spawn movement. */
    public String joinSpawnExemptPermission() {
        return config.getString("spawn.exempt-permission", "uxmessentials.spawn.join.exempt")
                .strip();
    }

    /** Whether deaths are managed by the configured/default respawn chain; default on. */
    public boolean spawnOnRespawn() {
        return config.getBoolean("spawn.respawn", true);
    }

    /**
     * The per-world respawn chain. {@code respawn.chain.<world>} remains the highest-priority, backwards-compatible
     * override; otherwise {@code spawn.respawn-chain} is used. An explicitly empty per-world list opts that world
     * back into vanilla respawning.
     */
    public RespawnChain respawnChain(WorldRef world) {
        Objects.requireNonNull(world, "world");
        if (!spawnOnRespawn()) {
            return RespawnChain.parse(List.of());
        }
        List<String> missing = List.of("__uxmessentials_missing_respawn_chain__");
        List<String> tokens = config.getStringList("respawn.chain." + world.name(), missing);
        if (tokens.equals(missing)) {
            tokens = config.getStringList("spawn.respawn-chain", List.of("bed", "anchor", "spawn"));
        }
        return RespawnChain.parse(tokens);
    }

    /** The configured fallback world an {@code /rtp} redirects to when its world has no queue, or empty. */
    public java.util.Optional<String> rtpFallbackWorld(WorldRef world) {
        Objects.requireNonNull(world, "world");
        String name = config.getString("rtp.fallback-world." + world.name(), "");
        return name.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(name);
    }

    /**
     * The cost of a manual {@code /rtp} in the default currency; {@code 0} (the default) makes it free. The
     * amount is checked before the search ({@code canAfford}) and withdrawn only after a successful landing.
     */
    public BigDecimal rtpCost() {
        return BigDecimal.valueOf(Math.max(0.0, config.getDouble("rtp.cost", 0.0)));
    }

    /**
     * The post-arrival grace tuning for an {@code /rtp} landing: how long the shield lasts and which of
     * Resistance, Slow-Falling, and the no-fall-damage guard are on. Defaults to a 5-second window with all
     * three enabled, so a fresh drop into far terrain does not kill the player before it renders.
     */
    public ArrivalGraceSettings arrivalGrace() {
        return new ArrivalGraceSettings(
                Math.max(0, config.getInt("rtp.grace-seconds", 5)),
                config.getBoolean("rtp.grace-resistance", true),
                config.getBoolean("rtp.grace-slow-falling", true),
                config.getBoolean("rtp.grace-block-fall-damage", true));
    }

    /**
     * Whether {@code /rtp biome <biome>} learns rare-biome hotspots passively (the {@code ChunkLoadEvent} registry)
     * and biases its search toward them ({@code rtp.biome-targeting}, default {@code true}). When off, the biome
     * search still runs but samples uniformly, so a rare biome is feasible only by luck; the registry and its
     * listener are not wired at all.
     */
    public boolean biomeTargeting() {
        return config.getBoolean("rtp.biome-targeting", true);
    }

    /** The most hotspot chunks the passive rare-biome registry keeps per biome ({@code rtp.biome-hotspot-registry-size}). */
    public int biomeHotspotRegistrySize() {
        return Math.max(1, config.getInt("rtp.biome-hotspot-registry-size", 30));
    }

    /**
     * The share of biome-targeted samples drawn near a known hotspot ({@code rtp.biome-hotspot-weight}, clamped to
     * {@code [0, 1]}, default {@code 0.7}); the remainder are uniform so the search never gets stuck on stale hotspots.
     */
    public double biomeHotspotWeight() {
        return Math.max(0.0, Math.min(1.0, config.getDouble("rtp.biome-hotspot-weight", 0.7)));
    }

    /** The worlds whose players are random-teleported from the pre-warmed pool on death respawn; empty by default. */
    public Set<String> rtpOnRespawnWorlds() {
        return Set.copyOf(config.getStringList("rtp.rtp-on-respawn", List.of()));
    }

    /** Whether a brand-new player is random-teleported from the pre-warmed pool on their first join; default off. */
    public boolean rtpOnFirstJoin() {
        return config.getBoolean("rtp.rtp-on-first-join", false);
    }
}
