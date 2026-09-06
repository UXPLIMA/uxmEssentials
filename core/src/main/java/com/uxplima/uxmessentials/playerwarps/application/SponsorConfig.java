package com.uxplima.uxmessentials.playerwarps.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

/**
 * The sponsorship sub-group's tunables, loaded once on enable and swapped atomically on reload (the config-record
 * idiom, mirroring {@link RentConfig}). An owner buys a paid, time-limited pinned browse slot: {@link #slots} bounds
 * how many sponsors can be live at once, {@link #durationDays} is the longest term a purchase may run,
 * {@link #price} of {@link #currencyId} is charged per purchase, {@link #maxConcurrentPerPlayer} caps how many
 * sponsorships one owner may hold at once, and {@link #cooldown} is the post-expiry cooldown a warp must wait out
 * before it can be sponsored again. A {@link #disabled()} config is the shipped default, the sub-group off, so no
 * slot is sold, no sweep runs, and no pinned tile renders.
 *
 * @param enabled whether the sponsorship sub-group runs at all
 * @param slots how many sponsor slots exist (a purchase takes the lowest free one; none free is a refusal)
 * @param durationDays the longest term a single purchase may run, in days (a request is clamped into {@code [1, this]})
 * @param price the fee charged per purchase, as a {@link BigDecimal} (never a {@code double})
 * @param currencyId the currency the fee is charged in ({@code "default"} for the server default)
 * @param maxConcurrentPerPlayer how many live sponsorships one owner may hold at once
 * @param cooldown how long a warp must wait after a sponsorship expires before it can be sponsored again
 */
public record SponsorConfig(
        boolean enabled,
        int slots,
        int durationDays,
        BigDecimal price,
        String currencyId,
        int maxConcurrentPerPlayer,
        Duration cooldown) {

    public SponsorConfig {
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(currencyId, "currencyId");
        Objects.requireNonNull(cooldown, "cooldown");
        if (slots < 0) {
            throw new IllegalArgumentException("sponsor slots must not be negative: " + slots);
        }
        if (durationDays < 1) {
            throw new IllegalArgumentException("sponsor duration-days must be at least 1: " + durationDays);
        }
        if (price.signum() < 0) {
            throw new IllegalArgumentException("sponsor price must not be negative: " + price);
        }
        if (maxConcurrentPerPlayer < 1) {
            throw new IllegalArgumentException(
                    "sponsor max-concurrent-per-player must be at least 1: " + maxConcurrentPerPlayer);
        }
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("sponsor cooldown must not be negative: " + cooldown);
        }
    }

    /** The shipped default: the sponsor sub-group off, so nothing is sold, swept, or pinned. */
    public static SponsorConfig disabled() {
        return new SponsorConfig(false, 5, 7, BigDecimal.valueOf(1000), "default", 1, Duration.ofDays(3));
    }
}
