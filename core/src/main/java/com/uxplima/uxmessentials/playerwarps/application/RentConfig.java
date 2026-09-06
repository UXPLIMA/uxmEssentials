package com.uxplima.uxmessentials.playerwarps.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;

/**
 * The rent sub-group's tunables, loaded once on enable and swapped atomically on reload (the config-record idiom).
 * A warp pays {@link #amount} of {@link #currencyId} every {@link #period} to stay listed; miss a payment and it is
 * suspended for {@link #grace}, then archived. {@link #reminderWindows} are the "rent due in N" heads-up windows,
 * normalised widest-first and de-duplicated, so the reminder pass can index each into a monotonic stage.
 *
 * <p>Exemption is by owner, category, or world (matched case-insensitively): an exempt warp is skipped by the
 * sweep entirely, never charged and never suspended. Owners may be listed by name or by uuid string; whichever an
 * operator has to hand matches. A {@link #disabled()} config is the shipped default. The sub-group off, nothing
 * charged.
 *
 * @param enabled whether the rent lifecycle runs at all
 * @param amount the rent charged each period, as a {@link BigDecimal} (never a {@code double})
 * @param currencyId the currency the rent is charged in ({@code "default"} for the server default)
 * @param period how long one paid term lasts
 * @param grace how long a suspended warp waits before it is archived
 * @param reminderWindows the "rent due soon" heads-up windows, widest-first and distinct
 * @param exemptPlayers owner names or uuid strings that are never charged rent (lower-cased)
 * @param exemptCategories category ids that are never charged rent (lower-cased)
 * @param exemptWorlds world names that are never charged rent (lower-cased)
 */
public record RentConfig(
        boolean enabled,
        BigDecimal amount,
        String currencyId,
        Duration period,
        Duration grace,
        List<Duration> reminderWindows,
        Set<String> exemptPlayers,
        Set<String> exemptCategories,
        Set<String> exemptWorlds) {

    public RentConfig {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currencyId, "currencyId");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(grace, "grace");
        Objects.requireNonNull(reminderWindows, "reminderWindows");
        Objects.requireNonNull(exemptPlayers, "exemptPlayers");
        Objects.requireNonNull(exemptCategories, "exemptCategories");
        Objects.requireNonNull(exemptWorlds, "exemptWorlds");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("rent amount must not be negative: " + amount);
        }
        reminderWindows = normalizeWindows(reminderWindows);
        exemptPlayers = lowerCased(exemptPlayers);
        exemptCategories = lowerCased(exemptCategories);
        exemptWorlds = lowerCased(exemptWorlds);
    }

    /** The shipped default: the rent sub-group off, so nothing is charged and no warp is ever suspended. */
    public static RentConfig disabled() {
        return new RentConfig(
                false,
                BigDecimal.ZERO,
                "default",
                Duration.ofDays(7),
                Duration.ofDays(3),
                List.of(),
                Set.of(),
                Set.of(),
                Set.of());
    }

    /** The number of reminder windows: the highest reminder stage a warp can reach before its term lapses. */
    public int maxReminderStage() {
        return reminderWindows.size();
    }

    /** The widest reminder window (the earliest heads-up), or {@link Duration#ZERO} when no windows are set. */
    public Duration widestReminderWindow() {
        return reminderWindows.isEmpty() ? Duration.ZERO : reminderWindows.get(0);
    }

    /**
     * True when {@code warp} is exempt from rent by its owner (name or uuid), its category, or its world, an
     * exempt warp is left entirely untouched by the sweep. Matching is case-insensitive on every axis.
     */
    public boolean isExempt(PlayerWarp warp) {
        Objects.requireNonNull(warp, "warp");
        if (exemptPlayers.contains(lower(warp.owner().uuid().toString()))
                || exemptPlayers.contains(lower(warp.ownerName()))) {
            return true;
        }
        if (warp.categoryId().map(id -> exemptCategories.contains(lower(id))).orElse(false)) {
            return true;
        }
        return exemptWorlds.contains(lower(warp.location().world().name()));
    }

    private static List<Duration> normalizeWindows(List<Duration> windows) {
        return windows.stream()
                .filter(w -> w != null && !w.isZero() && !w.isNegative())
                .distinct()
                .sorted((a, b) -> b.compareTo(a))
                .collect(Collectors.collectingAndThen(Collectors.toCollection(ArrayList::new), List::copyOf));
    }

    private static Set<String> lowerCased(Set<String> values) {
        return values.stream().map(RentConfig::lower).collect(Collectors.toUnmodifiableSet());
    }

    private static String lower(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
