package com.uxplima.uxmessentials.holograms.application.port;

import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * One ranked row a {@link LeaderboardProvider} returns: the display name and the already-formatted score string
 * (a currency-formatted balance, a playtime, …). The provider owns the formatting so the holograms context stays
 * free of any score-domain knowledge: it only lays the rows out into lines. Rank is positional (the list order).
 *
 * @param name the display name shown for this row
 * @param score the already-formatted score shown for this row
 */
@NullMarked
public record LeaderboardEntry(String name, String score) {

    public LeaderboardEntry {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(score, "score");
    }
}
