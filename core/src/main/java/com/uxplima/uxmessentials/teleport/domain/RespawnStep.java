package com.uxplima.uxmessentials.teleport.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * One ordered entry in a {@link RespawnChain}: a {@link RespawnStepKind} and, for the kinds that need
 * it, an argument (a {@code WARP} step carries the warp name). Parsed from the configured chain token
 * {@code spawn}, {@code warp:hub}, {@code home}: into a structured step the chain resolves in order.
 *
 * @param kind which kind of respawn target this step names
 * @param argument the step's argument (the warp name for {@link RespawnStepKind#WARP}), or {@code null}
 */
public record RespawnStep(RespawnStepKind kind, @Nullable String argument) {

    public RespawnStep {
        Objects.requireNonNull(kind, "kind");
        if (kind == RespawnStepKind.WARP && (argument == null || argument.isBlank())) {
            throw new IllegalArgumentException("a warp respawn step requires a warp name");
        }
    }

    /** A step with no argument (everything but {@code warp:<name>}). */
    public static RespawnStep of(RespawnStepKind kind) {
        return new RespawnStep(kind, null);
    }

    /** A {@code warp:<name>} step. */
    public static RespawnStep warp(String warpName) {
        return new RespawnStep(RespawnStepKind.WARP, warpName);
    }

    /**
     * Parse one configured token ({@code spawn}, {@code tpr}, {@code warp:hub}) into a step. Unknown
     * tokens yield an empty optional so a typo in {@code teleport.conf} is skipped, not fatal.
     */
    public static Optional<RespawnStep> parse(String token) {
        Objects.requireNonNull(token, "token");
        String trimmed = token.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        int colon = trimmed.indexOf(':');
        String head = colon < 0 ? trimmed : trimmed.substring(0, colon);
        String tail = colon < 0 ? null : trimmed.substring(colon + 1);
        return parseHead(head, tail);
    }

    private static Optional<RespawnStep> parseHead(String head, @Nullable String tail) {
        return switch (head) {
            case "tpr", "rtp" -> Optional.of(of(RespawnStepKind.RANDOM));
            case "spawn" -> Optional.of(of(RespawnStepKind.SPAWN));
            case "home" -> Optional.of(of(RespawnStepKind.HOME));
            case "bed" -> Optional.of(of(RespawnStepKind.BED));
            case "anchor" -> Optional.of(of(RespawnStepKind.ANCHOR));
            case "warp" -> tail == null || tail.isBlank() ? Optional.empty() : Optional.of(warp(tail));
            default -> Optional.empty();
        };
    }

    /** The step's argument when present (the warp name for a {@link RespawnStepKind#WARP} step). */
    public Optional<String> argumentValue() {
        return Optional.ofNullable(argument);
    }
}
