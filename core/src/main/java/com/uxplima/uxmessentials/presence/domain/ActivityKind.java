package com.uxplima.uxmessentials.presence.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The kinds of player activity the presence context recognises when deciding whether to reset the AFK idle
 * clock. The Bukkit event → kind mapping lives adapter-side; the rule for <em>which</em> kinds count as activity
 * is pure and lives in {@link ActivityPolicy}, so an operator can make an AFK-farm machine still go idle by
 * telling the policy to ignore the machine-driven kinds (fishing, sub-threshold rotation/movement).
 *
 * <p>Each kind carries the lower-case token an operator names it by in {@code presence.conf}
 * ({@code MOVE} ↔ {@code move}). The token is the config handle, the enum constant the compile-time one; an
 * unknown token resolves to empty so a typo in the ignore list never silently disables a real activity source.
 */
public enum ActivityKind {

    /** A real position change: a block hop, the canonical "the player is doing something" signal. */
    MOVE("move"),

    /** A look-around with no position change (yaw/pitch only): cheap to treat as non-activity for AFK farms. */
    ROTATE("rotate"),

    /** A chat line the player sent. */
    CHAT("chat"),

    /** A command the player ran. */
    COMMAND("command"),

    /** A block break, place, or interact. */
    INTERACT("interact"),

    /** A reel of the fishing rod ({@code PlayerFishEvent}), the classic AFK-fish-farm activity source. */
    FISH("fish");

    private final String token;

    ActivityKind(String token) {
        this.token = token;
    }

    /** The lower-case config token an operator lists this kind under in the ignore set. */
    public String token() {
        return token;
    }

    /** The kind named by {@code token} (case-insensitive), or empty when no kind matches. */
    public static Optional<ActivityKind> fromToken(String token) {
        Objects.requireNonNull(token, "token");
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        for (ActivityKind kind : values()) {
            if (kind.token.equals(normalized)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
