package com.uxplima.uxmessentials.economy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.event.WalletCredited;
import com.uxplima.uxmessentials.economy.domain.event.WalletDebited;
import com.uxplima.uxmessentials.economy.fakes.Currencies;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The {@code Wallet} aggregate invariants: the canonical worked DDD example. The balance is held
 * <em>per {@code Currency}</em>, a credit is additive and raises {@link WalletCredited}, a debit short of
 * funds is rejected (not clamped) and a debit raises {@link WalletDebited}, a credit past the currency's
 * maximum is rejected, currencies never mix, and crediting one currency leaves another untouched. The
 * aggregate is pure (no clock dependency beyond the supplied instant, no repository) so every rule is
 * asserted here in isolation.
 */
class WalletTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Siraco");
    private static final Instant NOW = Instant.parse("2026-05-30T12:00:00Z");

    @Test
    void creditIsAdditiveAndRaisesCredited() {
        Wallet w = Wallet.empty(OWNER);

        Wallet.Change first = w.credit(Money.of(Currencies.COINS, 100), NOW).orElseThrow();
        Wallet.Change second =
                first.result().credit(Money.of(Currencies.COINS, 50), NOW).orElseThrow();

        assertThat(second.result().balanceOf(Currencies.COINS)).isEqualTo(Money.of(Currencies.COINS, 150));
        assertThat(first.event()).isInstanceOf(WalletCredited.class);
    }

    @Test
    void debitBelowZeroIsRejectedAndNonMutating() {
        Wallet w = Wallet.empty(OWNER);

        var result = w.debit(Money.of(Currencies.COINS, 10), NOW);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow().error()).isEqualTo(EconomyError.INSUFFICIENT_FUNDS);
    }

    @Test
    void debitWithinBalanceRaisesDebited() {
        Wallet funded = Wallet.empty(OWNER)
                .credit(Money.of(Currencies.COINS, 100), NOW)
                .orElseThrow()
                .result();

        Wallet.Change change = funded.debit(Money.of(Currencies.COINS, 40), NOW).orElseThrow();

        assertThat(change.result().balanceOf(Currencies.COINS)).isEqualTo(Money.of(Currencies.COINS, 60));
        assertThat(change.event()).isInstanceOf(WalletDebited.class);
    }

    @Test
    void creditPastMaxIsRejected() {
        Currency capped = Currencies.cappedCoins(1_000);
        Wallet w = Wallet.empty(OWNER);

        var result = w.credit(Money.of(capped, 1_500), NOW);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow().error()).isEqualTo(EconomyError.BALANCE_MAX_EXCEEDED);
    }

    @Test
    void creditingOneCurrencyLeavesOthersUntouched() {
        Wallet w = Wallet.empty(OWNER)
                .credit(Money.of(Currencies.COINS, 100), NOW)
                .orElseThrow()
                .result()
                .credit(Money.of(Currencies.GEMS, 5), NOW)
                .orElseThrow()
                .result();

        assertThat(w.balanceOf(Currencies.COINS)).isEqualTo(Money.of(Currencies.COINS, 100));
        assertThat(w.balanceOf(Currencies.GEMS)).isEqualTo(Money.of(Currencies.GEMS, 5));
    }

    @Test
    void currencyMismatchThrows() {
        assertThatThrownBy(() -> Money.of(Currencies.COINS, 10).plus(Money.of(Currencies.GEMS, 5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void untouchedCurrencyProjectsToZero() {
        Wallet w = Wallet.empty(OWNER);

        assertThat(w.balanceOf(Currencies.GEMS)).isEqualTo(Money.zero(Currencies.GEMS));
    }
}
