package com.uxplima.uxmessentials.security.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/**
 * The pure decision behind the client-brand guard: given a joining player's reported client brand, decide whether
 * the client is allowed and whether it is worth flagging for staff. It is a value object so the rule is
 * unit-testable without a player or the plugin-message channel. The listener feeds it the raw brand string and
 * acts on the {@link ClientVerdict}.
 *
 * <p>Matching is case-insensitive and trims surrounding whitespace, so an operator listing {@code "vanilla"}
 * matches {@code "Vanilla"} and {@code " vanilla "} alike. A {@code null} or blank brand (a client that reported
 * nothing) is treated as the empty brand: it can never match a listed entry, so it is allowed under
 * {@link ClientIdMode#BLOCK_LIST} and denied under {@link ClientIdMode#ALLOW_LIST} unless the operator lists the
 * empty string explicitly.
 *
 * @param mode which side of {@code brands} is the allowed side, or the flag-only observe mode
 * @param brands the configured brand list, normalised to lower-case on construction
 */
public record ClientPolicy(ClientIdMode mode, Set<String> brands) {

    public ClientPolicy(ClientIdMode mode, Set<String> brands) {
        this.mode = Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(brands, "brands");
        this.brands = brands.stream().map(ClientPolicy::normalise).collect(Collectors.toUnmodifiableSet());
    }

    /** The verdict for {@code brand} (as reported by the client, possibly {@code null}) under this policy. */
    public ClientVerdict judge(@Nullable String brand) {
        boolean listed = brands.contains(normalise(brand));
        return switch (mode) {
            case BLOCK_LIST -> listed ? ClientVerdict.deny() : ClientVerdict.clear();
            case ALLOW_LIST -> listed ? ClientVerdict.clear() : ClientVerdict.deny();
            case FLAG -> listed ? ClientVerdict.flag() : ClientVerdict.clear();
        };
    }

    private static String normalise(@Nullable String raw) {
        return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
    }
}
