package com.uxplima.uxmessentials.shared.adapter.outbound.currency;

import java.util.Objects;
import java.util.UUID;

/**
 * One economy back-end, bound to a single currency, behind a uniform {@code UUID}/{@code double} surface. A
 * provider is reached by spec id through {@link Currencies#resolve(String)}; the later money actions
 * ({@code [givemoney]}/{@code [takemoney]}/{@code [setmoney]}, Phase 2) and the {@code has-money} requirement
 * (Phase 3) consume it without ever naming a provider SDK type.
 *
 * <p>Every method is total: an unavailable back-end (its plugin absent, its hook off, the player offline for an
 * online-only back-end) never throws. It answers {@code 0}/{@code false} and {@link #deposit}/{@link #withdraw}
 * report no change. Callers therefore use a provider unconditionally, the same way the {@code EconomyQuery} hook
 * is used. A multi-currency back-end binds one provider instance per currency name; {@link #id()} encodes which.
 *
 * <p>Threading: a provider touches Bukkit (the experience API, Vault, a reflected economy) on the calling thread.
 * Call it on the viewer's entity thread (where the menu engine's click and action chains already run) never off
 * it.
 */
public interface CurrencyProvider {

    /** The spec id this provider answers to, e.g. {@code vault}, {@code exp}, {@code coinsengine:gold}. */
    String id();

    /** Whether this back-end is live (its plugin present and enabled, its hook available). */
    boolean available();

    /** {@code player}'s balance in this currency; {@code 0} when the back-end is unavailable. */
    double balance(UUID player);

    /** Whether {@code player} holds at least {@code amount}; false when the back-end is unavailable. */
    boolean has(UUID player, double amount);

    /** Remove {@code amount} from {@code player}; true on success, false when unavailable or refused. */
    boolean withdraw(UUID player, double amount);

    /** Add {@code amount} to {@code player}; true on success, false when unavailable or refused. */
    boolean deposit(UUID player, double amount);

    /** Render {@code amount} the way this currency reads; a plain number when the back-end has no formatter. */
    String format(double amount);

    /**
     * A provider bound to {@code id} that is never available and no-ops every operation: what
     * {@link Currencies#resolve(String)} returns for a spec no back-end claims. References no SDK type, so
     * resolving an unknown spec loads nothing.
     */
    static CurrencyProvider unavailable(String id) {
        return new UnavailableCurrencyProvider(id);
    }

    /** The no-op provider behind {@link #unavailable(String)}: never available, every call a safe no-op. */
    record UnavailableCurrencyProvider(String id) implements CurrencyProvider {

        public UnavailableCurrencyProvider {
            Objects.requireNonNull(id, "id");
        }

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
            return CurrencyAmounts.plain(amount);
        }
    }
}
