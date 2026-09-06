package com.uxplima.uxmessentials.security.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/**
 * The pure decision behind op-command protection: given the command a player is about to run and when they last
 * proved their second factor, decide whether the command is a protected one that needs a fresh re-authentication.
 * It is a value object so the rule is unit-testable without a player, a keypad, or the Bukkit command map, the
 * listener feeds it the typed command root and the in-memory last-verified instant and acts on the verdict.
 *
 * <p>Matching is forgiving on purpose: a configured entry and the incoming root are both normalised the same way
 * (a leading {@code /} dropped, only the first token kept, a {@code namespace:} prefix stripped, lower-cased), so an
 * operator listing {@code op} catches {@code /op Steve} and {@code minecraft:op} alike. A command that is not on the
 * protected list is always allowed; a protected one is allowed only while the player's last verification is still
 * inside the re-auth window, and otherwise requires a fresh proof.
 *
 * @param protectedCommands the normalised roots that demand a recent verification
 * @param window how long a verification counts as recent, a verify at or before {@code now - window} has lapsed
 */
public record ReauthPolicy(Set<String> protectedCommands, Duration window) {

    public ReauthPolicy(Set<String> protectedCommands, Duration window) {
        Objects.requireNonNull(protectedCommands, "protectedCommands");
        Objects.requireNonNull(window, "window");
        if (window.isNegative()) {
            throw new IllegalArgumentException("re-auth window must not be negative: " + window);
        }
        this.protectedCommands = protectedCommands.stream()
                .map(ReauthPolicy::normalise)
                .filter(root -> !root.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.window = window;
    }

    /** Whether the protected-command list is empty, so the guard can short-circuit before any lookup. */
    public boolean isEmpty() {
        return protectedCommands.isEmpty();
    }

    /** Whether {@code commandRoot} (in any leading-slash / namespaced / argument-tailed form) is a protected command. */
    public boolean isProtected(String commandRoot) {
        Objects.requireNonNull(commandRoot, "commandRoot");
        return protectedCommands.contains(normalise(commandRoot));
    }

    /**
     * Decide whether running {@code commandRoot} at {@code now} is allowed given the player's {@code lastVerified}
     * instant (or {@code null} if they have not verified this session): allowed when the command is not protected or
     * the verification is still inside the window, and otherwise a fresh re-auth is required.
     */
    public Decision decide(String commandRoot, @Nullable Instant lastVerified, Instant now) {
        Objects.requireNonNull(commandRoot, "commandRoot");
        Objects.requireNonNull(now, "now");
        if (!isProtected(commandRoot)) {
            return Decision.ALLOW;
        }
        if (lastVerified != null && !now.isAfter(lastVerified.plus(window))) {
            return Decision.ALLOW;
        }
        return Decision.REQUIRE_REAUTH;
    }

    /** Normalise a raw command (typed line or config entry) to its bare lower-cased root for matching. */
    private static String normalise(String raw) {
        String root = raw.strip();
        if (root.startsWith("/")) {
            root = root.substring(1);
        }
        int space = root.indexOf(' ');
        if (space >= 0) {
            root = root.substring(0, space);
        }
        int colon = root.indexOf(':');
        if (colon >= 0) {
            root = root.substring(colon + 1);
        }
        return root.toLowerCase(Locale.ROOT);
    }

    /** The verdict {@link ReauthPolicy#decide} returns: let the command run, or demand a fresh second-factor proof. */
    public enum Decision {

        /** The command is not protected, or the player verified recently enough, let it run. */
        ALLOW,

        /** The command is protected and the player has not verified inside the window, prompt a re-auth. */
        REQUIRE_REAUTH
    }
}
