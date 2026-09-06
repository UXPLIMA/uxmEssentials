package com.uxplima.uxmessentials.holograms.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * A hologram's leaderboard configuration: which ranked data source it shows ({@code providerId}, resolved to a
 * {@code LeaderboardProvider} at the adapter) and how many top rows ({@code limit}). A hologram carries this only
 * when it is a leaderboard; the renderer regenerates the hologram's lines from the provider's top rows on each
 * refresh, so the stored lines are not what a leaderboard displays. Pure: the provider lookup and row formatting
 * live in the adapter.
 *
 * @param providerId the lowercase id of the ranked data source ({@code balance})
 * @param limit how many top rows to show (1 to {@value #MAX_LIMIT})
 */
public record LeaderboardSpec(String providerId, int limit) {

    /** The largest top-N a leaderboard may show, a sane cap so a hologram never asks for thousands of rows. */
    public static final int MAX_LIMIT = 50;

    public LeaderboardSpec {
        Objects.requireNonNull(providerId, "providerId");
        providerId = providerId.strip().toLowerCase(Locale.ROOT);
        if (providerId.isEmpty()) {
            throw new IllegalArgumentException("leaderboard providerId must not be blank");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("leaderboard limit out of range [1, " + MAX_LIMIT + "]: " + limit);
        }
    }
}
