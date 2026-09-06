package com.uxplima.uxmessentials.tablist.adapter.outbound;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.tablist.domain.TablistSkinSource;
import com.uxplima.uxmessentials.tablist.domain.TablistSkinSource.PlayerName;
import com.uxplima.uxmessentials.tablist.domain.TablistSkinSource.Texture;
import com.uxplima.uxmlib.packet.tablist.TabSkin;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves a {@link TablistSkinSource} to a uxmLib {@link TabSkin} for the render path, non-blocking, so the renderer
 * never waits on a Mojang call. Three paths by source kind:
 *
 * <ul>
 *   <li>{@link Texture}. The base64 value (and optional signature) is used as-is; no lookup;</li>
 *   <li>{@link PlayerName} naming an online player. Their texture is read inline from the live profile (no I/O);</li>
 *   <li>{@link PlayerName} naming an offline player. The texture is served from a small bounded Caffeine cache; on a
 *       cache miss the resolver returns empty <em>now</em> (the renderer takes the native no-skin path this tick) and
 *       schedules a one-shot {@code async} fetch that fills the cache, so a later refresh picks the skin up.</li>
 * </ul>
 *
 * <p>The cache holds a present-or-absent result ({@link Optional}) keyed by lower-cased name so a name that resolves to
 * no texture (a never-joined name, a fetch failure) is remembered for the TTL rather than re-fetched every refresh. An
 * {@link #inFlight} guard keeps a slow fetch from being scheduled many times while it is running. Every failure path
 * falls back to no skin: the resolver never blocks, never throws.
 */
@NullMarked
public final class TablistSkinResolver {

    // Bounded so a config typo or a churn of names cannot grow the cache without limit; an offline server runs a
    // handful
    // of named skins at most.
    private static final long MAX_CACHED_NAMES = 256L;
    private static final Duration CACHE_TTL = Duration.ofMinutes(30L);

    private final MojangProfileSource profiles;
    private final Scheduler scheduler;
    private final Cache<String, Optional<TabSkin>> offlineCache;
    private final Cache<String, Boolean> inFlight;

    public TablistSkinResolver(MojangProfileSource profiles, Scheduler scheduler) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.offlineCache = Caffeine.newBuilder()
                .maximumSize(MAX_CACHED_NAMES)
                .expireAfterWrite(CACHE_TTL)
                .build();
        // A short window dedupes concurrent fetch scheduling for the same name; it expires so a failed fetch can retry.
        this.inFlight = Caffeine.newBuilder()
                .maximumSize(MAX_CACHED_NAMES)
                .expireAfterWrite(CACHE_TTL)
                .build();
    }

    /** The skin for {@code source} right now, or empty when none is available yet (the renderer takes the native path). */
    public Optional<TabSkin> resolve(TablistSkinSource source) {
        Objects.requireNonNull(source, "source");
        return switch (source) {
            case Texture texture ->
                Optional.of(new TabSkin(texture.value(), texture.signature().orElse(null)));
            case PlayerName playerName -> resolvePlayer(playerName.name());
        };
    }

    private Optional<TabSkin> resolvePlayer(String name) {
        Optional<TabSkin> online = profiles.onlineTexture(name);
        if (online.isPresent()) {
            return online;
        }
        return offlineSkin(name);
    }

    private Optional<TabSkin> offlineSkin(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        Optional<TabSkin> cached = offlineCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        scheduleFetch(name, key);
        return Optional.empty();
    }

    private void scheduleFetch(String name, String key) {
        // Schedule one fetch per key while it is in flight; the guard expires so a failed fetch can be retried later.
        if (inFlight.getIfPresent(key) != null) {
            return;
        }
        inFlight.put(key, Boolean.TRUE);
        scheduler.async(() -> {
            try {
                offlineCache.put(key, profiles.fetchTexture(name));
            } finally {
                inFlight.invalidate(key);
            }
        });
    }
}
