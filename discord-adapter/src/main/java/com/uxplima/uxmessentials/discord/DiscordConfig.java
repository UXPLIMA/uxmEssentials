package com.uxplima.uxmessentials.discord;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Typed, immutable view of {@code discord.conf} (CLAUDE.md: "config via Configurate HOCON (token, channel
 * mappings, which event categories to forward)"). Loaded once on enable by {@link DiscordConfigLoader} and
 * never mutated.
 *
 * <p>The bridge is dormant unless {@link #enabled()} is true and a non-blank {@link #token()} is present; the
 * {@link #channels()} map carries one Discord channel id per {@link EventCategory} the operator opted into.
 * {@code minEcoNotify} suppresses routine low-value economy notifications below the threshold.
 *
 * @param enabled master switch; the bridge stays dormant when false (the on-disk default)
 * @param token the Discord bot token; blank means "not configured" and the bridge stays dormant
 * @param channels per-category target channel ids; a category absent here is not forwarded
 * @param minEcoNotify only economy transactions at or above this amount are mirrored (avoids spamming /pay)
 * @param maxPerMinute flood cap. At most this many notifications are mirrored per rolling minute (load shed)
 */
public record DiscordConfig(
        boolean enabled, String token, Map<EventCategory, String> channels, long minEcoNotify, int maxPerMinute) {

    public DiscordConfig {
        Objects.requireNonNull(token, "token");
        channels = Map.copyOf(Objects.requireNonNull(channels, "channels"));
        if (minEcoNotify < 0) {
            throw new IllegalArgumentException("minEcoNotify must be >= 0");
        }
        if (maxPerMinute <= 0) {
            throw new IllegalArgumentException("maxPerMinute must be > 0");
        }
    }

    /**
     * Whether the bridge should attempt a connection at all: enabled, with a non-blank token, and at least
     * one channel mapped. A config that misses any of these leaves the bridge dormant rather than failing.
     */
    public boolean shouldConnect() {
        return enabled && !token.isBlank() && !channels.isEmpty();
    }

    /** The Discord channel id mapped to the given category, empty when the operator did not map it. */
    public Optional<String> channelFor(EventCategory category) {
        Objects.requireNonNull(category, "category");
        return Optional.ofNullable(channels.get(category));
    }
}
