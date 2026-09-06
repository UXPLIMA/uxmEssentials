package com.uxplima.uxmessentials.discord;

import java.util.Optional;

/**
 * The notification categories the bridge can forward, each mapped to a Discord channel id in
 * {@code discord.conf}. A category with no configured channel is simply not forwarded, the operator opts in
 * per category (docs/09-deployment.md Path C: the audit channel and an optional separate economy channel).
 *
 * <p>The {@code configKey} is the HOCON node under {@code channels} that carries the target channel id; it
 * matches the dashed glossary noun convention used across the host configs.
 */
public enum EventCategory {

    /** Moderation and admin audit events (mute/jail/tempban, eco-admin, migration imports, reloads). */
    AUDIT("audit"),

    /** High-value economy notifications ({@code /pay} and eco-admin) above the configured threshold. */
    ECONOMY("economy");

    private final String configKey;

    EventCategory(String configKey) {
        this.configKey = configKey;
    }

    /** The HOCON key under {@code channels.<key>} that holds this category's Discord channel id. */
    public String configKey() {
        return configKey;
    }

    /** Resolve a category from its config key, empty when the key matches no known category. */
    public static Optional<EventCategory> fromConfigKey(String key) {
        for (EventCategory category : values()) {
            if (category.configKey.equals(key)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
