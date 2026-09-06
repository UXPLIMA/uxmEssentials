package com.uxplima.uxmessentials.migration;

import java.util.Locale;
import java.util.Objects;

/**
 * The intentionally conservative, separate knob for imported balances (docs/12-migration §6), money
 * is the one thing that cannot be cleanly un-imported. The wallet write always <em>sets</em> the
 * imported balance and never <em>adds</em>, so a re-run can never double-credit; this policy only
 * decides whether a wallet that already holds a balance is touched at all.
 *
 * <ul>
 *   <li>{@link #SKIP_IF_PRESENT} (default): a wallet that already has a balance is left untouched.
 *   <li>{@link #OVERWRITE}, set the wallet to the source balance (for a clean, all-zero target).
 * </ul>
 */
public enum BalancePolicy {
    SKIP_IF_PRESENT,
    OVERWRITE;

    /** The safest default: never inflate an existing balance on a re-run. */
    public static BalancePolicy defaultPolicy() {
        return SKIP_IF_PRESENT;
    }

    /** Parse a {@code migration.conf > balance-policy} value, defaulting to {@link #SKIP_IF_PRESENT}. */
    public static BalancePolicy parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        return "overwrite".equals(raw.strip().toLowerCase(Locale.ROOT)) ? OVERWRITE : SKIP_IF_PRESENT;
    }
}
