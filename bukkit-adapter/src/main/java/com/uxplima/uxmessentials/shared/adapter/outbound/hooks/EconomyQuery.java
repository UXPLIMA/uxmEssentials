package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.NullMarked;

/**
 * The server-economy capability the menu engine reads through the Vault hook: query a balance, test
 * affordability, move money, and render an amount. It is a small typed seam over {@code UUID}/{@code double}
 *. The later money actions ({@code [givemoney]}/{@code [takemoney]}, Phase 2) and the {@code has-money}
 * requirement (Phase 3) consume it without ever touching a provider SDK.
 *
 * <p>Vault's economy API is main-thread/entity-thread only, so a caller must invoke this capability on the
 * viewer's entity thread; the menu engine's click and action chains already run there. This interface and its
 * {@link #ABSENT} default reference none of {@code net.milkbowl.vault}; the only class that does is the real
 * implementation behind {@link VaultEconomyHook#whenPresent}, so resolving an absent hook loads no SDK class.
 */
@NullMarked
public interface EconomyQuery {

    /** Whether a live Vault economy is registered and enabled, false for the absent default. */
    boolean available();

    /** {@code player}'s balance in the server economy; {@code 0} when no economy is available. */
    double balance(UUID player);

    /** Whether {@code player} can afford {@code amount}; false when no economy is available. */
    boolean has(UUID player, double amount);

    /** Withdraw {@code amount} from {@code player}; true on success, false when unavailable or refused. */
    boolean withdraw(UUID player, double amount);

    /** Deposit {@code amount} into {@code player}; true on success, false when unavailable or refused. */
    boolean deposit(UUID player, double amount);

    /** Render {@code amount} the way the economy formats currency; the bare number when unavailable. */
    String format(double amount);

    /** The safe no-op used when Vault (or its economy service) is absent: never available, every op a no-op. */
    EconomyQuery ABSENT = new EconomyQuery() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public double balance(UUID player) {
            Objects.requireNonNull(player, "player");
            return 0;
        }

        @Override
        public boolean has(UUID player, double amount) {
            Objects.requireNonNull(player, "player");
            return false;
        }

        @Override
        public boolean withdraw(UUID player, double amount) {
            Objects.requireNonNull(player, "player");
            return false;
        }

        @Override
        public boolean deposit(UUID player, double amount) {
            Objects.requireNonNull(player, "player");
            return false;
        }

        @Override
        public String format(double amount) {
            return Double.toString(amount);
        }
    };
}
