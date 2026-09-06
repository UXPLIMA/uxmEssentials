package com.uxplima.uxmessentials.vote.domain;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Describes one configured vote site: its display label, the Votifier service key used to match an
 * incoming vote to this site, the optional public URL, and the per-player cooldown before the same
 * player can vote on that site again. The cooldown is a domain concept. The adapter reads it from
 * config and passes the full catalog in at wire-time; no config type appears here.
 *
 * <p>The {@code name} is purely a display label ({@code /vote next}, {@code /vote last}, the GUI
 * icon). The {@code service} is the key the cooldown is tracked under: it must equal the Votifier
 * vote-list/service string the vote arrives with, because {@code HandleVote} records the cooldown
 * under the vote's raw service name and the read paths look it up under {@code service}. Keeping the
 * two separate lets the operator show a friendly label while still matching the exact Votifier key.
 *
 * @param name     the display label shown to players (case-preserved, non-blank)
 * @param service  the Votifier service key the cooldown is matched on (case-preserved, non-blank);
 *                 defaults to {@code name} when the operator does not configure it explicitly
 * @param url      the public vote URL shown to players in {@code /vote next} and {@code /vote}
 * @param cooldown how long after voting the site is unavailable to the same player; zero means
 *                 the site is always votable
 */
public record VoteSiteSpec(String name, String service, Optional<String> url, Duration cooldown) {

    public VoteSiteSpec {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("site name must not be blank");
        }
        Objects.requireNonNull(service, "service");
        if (service.isBlank()) {
            throw new IllegalArgumentException("site service must not be blank");
        }
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(cooldown, "cooldown");
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("cooldown must not be negative: " + cooldown);
        }
    }

    /**
     * Convenience for the common case where the display label is also the Votifier service key:
     * {@code service} defaults to {@code name}. Existing callers and configs that never distinguish
     * the two keep their previous behaviour.
     */
    public VoteSiteSpec(String name, Optional<String> url, Duration cooldown) {
        this(name, name, url, cooldown);
    }
}
