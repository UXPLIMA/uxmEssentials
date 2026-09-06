package com.uxplima.uxmessentials.moderation.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One issued warning: a row in the append-only warning history ({@code /warn}, {@code /tempwarn}). The history
 * is never updated or overwritten, so a target's full record of warnings is preserved and {@code /warns
 * <player>} lists it newest-first.
 *
 * <p>A {@code /warn} produces a warning with no {@link #expiresAt()} (it stands until {@code /unwarn} clears
 * it); a {@code /tempwarn} carries a wall-clock {@code expiresAt} after which the warning no longer counts
 * the review and the count drop it once {@link #isExpiredAt(Instant)} holds, so a timed warning lapses on its
 * own without an operator clearing it.
 *
 * @param issuer who issued the warning
 * @param reason the optional free-text reason
 * @param issuedAt when the warning was issued
 * @param expiresAt the wall-clock expiry for a timed warning, or empty for one that stands until cleared
 */
public record Warn(Issuer issuer, Optional<String> reason, Instant issuedAt, Optional<Instant> expiresAt) {

    public Warn {
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /** A warning that stands until cleared, by {@code issuer} with {@code reason} at {@code issuedAt}. */
    public static Warn standing(Issuer issuer, Optional<String> reason, Instant issuedAt) {
        return new Warn(issuer, reason, issuedAt, Optional.empty());
    }

    /** A timed warning by {@code issuer} with {@code reason} at {@code issuedAt}, lapsing at {@code expiresAt}. */
    public static Warn timed(Issuer issuer, Optional<String> reason, Instant issuedAt, Instant expiresAt) {
        Objects.requireNonNull(expiresAt, "expiresAt");
        return new Warn(issuer, reason, issuedAt, Optional.of(expiresAt));
    }

    /** A warning by {@code issuer} with {@code reason} (nullable) at {@code issuedAt} that stands until cleared. */
    public static Warn of(Issuer issuer, @org.jspecify.annotations.Nullable String reason, Instant issuedAt) {
        return standing(issuer, Optional.ofNullable(reason).filter(r -> !r.isBlank()), issuedAt);
    }

    /** True once a timed warning has lapsed at {@code now}; a standing warning never expires. */
    public boolean isExpiredAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return expiresAt.map(now::isAfter).orElse(false);
    }
}
