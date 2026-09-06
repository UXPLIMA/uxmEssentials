package com.uxplima.uxmessentials.vaults.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage of {@link ProviderVaultEconomy}: it bridges the vaults {@code VaultEconomy} seam onto the
 * resolved {@link EconomyProvider}, denominating the bare amount in the configured currency. {@code canAfford}
 * is a balance read against the fee, {@code withdraw} is the guarded debit and {@code deposit} is a credit
 * each delegated to the provider with the right currency, which a recording fake provider verifies.
 * {@code withdraw}/{@code deposit} report the provider's {@code Result}: {@code true} on {@code isOk()},
 * {@code false} when the guarded debit/credit was rejected.
 */
class ProviderVaultEconomyTest {

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();
    private static final PlayerRef WHO = new PlayerRef(java.util.UUID.randomUUID(), "Alice");

    @Test
    void canAffordReadsTheBalanceAgainstTheFee() {
        FakeProvider provider = new FakeProvider(new BigDecimal("100"));
        ProviderVaultEconomy economy = new ProviderVaultEconomy(provider, COINS, Optional.empty());

        assertThat(economy.canAfford(WHO, new BigDecimal("40"))).isTrue();
        assertThat(economy.canAfford(WHO, new BigDecimal("100"))).isTrue();
        assertThat(economy.canAfford(WHO, new BigDecimal("101"))).isFalse();
    }

    @Test
    void withdrawDebitsTheFeeInTheConfiguredCurrencyAndReportsSuccess() {
        FakeProvider provider = new FakeProvider(new BigDecimal("100"));
        ProviderVaultEconomy economy = new ProviderVaultEconomy(provider, COINS, Optional.empty());

        boolean ok = economy.withdraw(WHO, new BigDecimal("25"));

        assertThat(ok).isTrue();
        assertThat(provider.debited).isEqualTo(Money.of(COINS, new BigDecimal("25")));
        assertThat(provider.credited).isNull();
    }

    @Test
    void withdrawReportsFalseWhenTheGuardedDebitIsRejected() {
        FakeProvider provider = new FakeProvider(new BigDecimal("100"));
        provider.debitResult = Result.err(TransferError.INSUFFICIENT_FUNDS);
        ProviderVaultEconomy economy = new ProviderVaultEconomy(provider, COINS, Optional.empty());

        boolean ok = economy.withdraw(WHO, new BigDecimal("25"));

        assertThat(ok).isFalse();
        assertThat(provider.debited).isEqualTo(Money.of(COINS, new BigDecimal("25"))); // the debit was attempted
    }

    @Test
    void depositCreditsTheRefundInTheConfiguredCurrencyAndReportsSuccess() {
        FakeProvider provider = new FakeProvider(new BigDecimal("100"));
        ProviderVaultEconomy economy = new ProviderVaultEconomy(provider, COINS, Optional.empty());

        boolean ok = economy.deposit(WHO, new BigDecimal("10"));

        assertThat(ok).isTrue();
        assertThat(provider.credited).isEqualTo(Money.of(COINS, new BigDecimal("10")));
        assertThat(provider.debited).isNull();
    }

    @Test
    void depositReportsFalseWhenTheCreditIsRejected() {
        FakeProvider provider = new FakeProvider(new BigDecimal("100"));
        provider.creditResult = Result.err(TransferError.BALANCE_MAX_EXCEEDED);
        ProviderVaultEconomy economy = new ProviderVaultEconomy(provider, COINS, Optional.empty());

        boolean ok = economy.deposit(WHO, new BigDecimal("10"));

        assertThat(ok).isFalse();
        assertThat(provider.credited).isEqualTo(Money.of(COINS, new BigDecimal("10"))); // the credit was attempted
    }

    /** A minimal recording {@link EconomyProvider}: answers {@code balance} from a fixed amount, records moves. */
    private static final class FakeProvider implements EconomyProvider {
        private final BigDecimal balance;
        private @Nullable Money debited;
        private @Nullable Money credited;
        private Result<Unit, TransferError> debitResult = Result.ok();
        private Result<Unit, TransferError> creditResult = Result.ok();

        private FakeProvider(BigDecimal balance) {
            this.balance = balance;
        }

        @Override
        public boolean hasAccount(PlayerRef owner, Currency currency) {
            return true;
        }

        @Override
        public void ensureAccount(PlayerRef owner, Currency currency) {}

        @Override
        public Money balance(PlayerRef owner, Currency currency) {
            return Money.of(currency, balance);
        }

        @Override
        public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
            this.credited = amount;
            return creditResult;
        }

        @Override
        public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
            this.debited = amount;
            return debitResult;
        }

        @Override
        public TransferResult transfer(PlayerRef from, PlayerRef to, Money amount) {
            throw new UnsupportedOperationException("transfer is not exercised by these tests");
        }

        @Override
        public List<BaltopRow> top(Currency currency, int limit) {
            return List.of();
        }

        @Override
        public Set<Currency> currencies() {
            return Set.of(COINS);
        }
    }
}
