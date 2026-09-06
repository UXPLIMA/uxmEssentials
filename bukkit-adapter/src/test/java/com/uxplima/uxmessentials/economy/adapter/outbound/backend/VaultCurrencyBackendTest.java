package com.uxplima.uxmessentials.economy.adapter.outbound.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Server;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.EconomyQuery;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.Hooks;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.PluginHook;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The Vault-backed currency backend over its {@link EconomyQuery} hook. A live hook is read and its balance,
 * credit and debit outcomes are translated into {@link Money} and {@link TransferError}; a debit the owner
 * cannot cover is refused before any withdraw runs; and when the economy reports unavailable every call is a
 * safe no-op. A zero balance and a rejected {@link TransferError#CURRENCY_UNSUPPORTED}, never a throw and
 * never a mutation.
 */
class VaultCurrencyBackendTest {

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();
    private static final PlayerRef ALICE =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000aa"), "Alice");

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void readsBalanceAndMovesMoneyThroughTheEconomyQuery() {
        FakeEconomy economy = new FakeEconomy(true);
        economy.balances.put(ALICE.uuid(), 100.0);
        VaultCurrencyBackend backend = new VaultCurrencyBackend(hooksFor(economy));

        assertThat(backend.available()).isTrue();
        assertThat(backend.balance(ALICE, COINS).amount()).isEqualByComparingTo("100");
        assertThat(backend.debit(ALICE, Money.of(COINS, BigDecimal.valueOf(40))).isOk())
                .isTrue();
        assertThat(economy.balances.get(ALICE.uuid())).isEqualTo(60.0);
        assertThat(backend.credit(ALICE, Money.of(COINS, BigDecimal.valueOf(15)))
                        .isOk())
                .isTrue();
        assertThat(economy.balances.get(ALICE.uuid())).isEqualTo(75.0);
    }

    @Test
    void refusesADebitTheOwnerCannotCoverWithoutWithdrawing() {
        FakeEconomy economy = new FakeEconomy(true);
        economy.balances.put(ALICE.uuid(), 20.0);
        VaultCurrencyBackend backend = new VaultCurrencyBackend(hooksFor(economy));

        assertThat(backend.debit(ALICE, Money.of(COINS, BigDecimal.valueOf(50))).errorOrThrow())
                .isEqualTo(TransferError.INSUFFICIENT_FUNDS);
        assertThat(economy.balances.get(ALICE.uuid())).isEqualTo(20.0);
    }

    @Test
    void noOpsWhenTheEconomyIsUnavailable() {
        FakeEconomy economy = new FakeEconomy(false);
        economy.balances.put(ALICE.uuid(), 100.0);
        VaultCurrencyBackend backend = new VaultCurrencyBackend(hooksFor(economy));

        assertThat(backend.available()).isFalse();
        assertThatCode(() -> {
                    assertThat(backend.balance(ALICE, COINS).isZero()).isTrue();
                    assertThat(backend.credit(ALICE, Money.of(COINS, BigDecimal.ONE))
                                    .errorOrThrow())
                            .isEqualTo(TransferError.CURRENCY_UNSUPPORTED);
                    assertThat(backend.debit(ALICE, Money.of(COINS, BigDecimal.ONE))
                                    .errorOrThrow())
                            .isEqualTo(TransferError.CURRENCY_UNSUPPORTED);
                })
                .doesNotThrowAnyException();
        assertThat(economy.balances.get(ALICE.uuid())).isEqualTo(100.0);
    }

    private Hooks hooksFor(EconomyQuery economy) {
        return Hooks.resolve(server, SILENT, List.of(new StubEconomyHook(economy)));
    }

    /** Binds a supplied {@link EconomyQuery} as the resolved capability, touching no provider SDK type. */
    private record StubEconomyHook(EconomyQuery economy) implements PluginHook<EconomyQuery> {

        @Override
        public String pluginName() {
            return "FakeEconomy";
        }

        @Override
        public Class<EconomyQuery> capability() {
            return EconomyQuery.class;
        }

        @Override
        public EconomyQuery whenAbsent() {
            return EconomyQuery.ABSENT;
        }

        @Override
        public EconomyQuery whenPresent(Server server) {
            return economy;
        }

        @Override
        public boolean isPresent(Server server) {
            return true;
        }
    }

    /** An in-memory {@link EconomyQuery} the backend delegates to, with a toggleable availability. */
    private static final class FakeEconomy implements EconomyQuery {

        private final boolean available;
        private final Map<UUID, Double> balances = new HashMap<>();

        FakeEconomy(boolean available) {
            this.available = available;
        }

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public double balance(UUID player) {
            return balances.getOrDefault(player, 0.0);
        }

        @Override
        public boolean has(UUID player, double amount) {
            return balance(player) >= amount;
        }

        @Override
        public boolean withdraw(UUID player, double amount) {
            balances.merge(player, -amount, Double::sum);
            return true;
        }

        @Override
        public boolean deposit(UUID player, double amount) {
            balances.merge(player, amount, Double::sum);
            return true;
        }

        @Override
        public String format(double amount) {
            return "$" + amount;
        }
    }

    /** A {@link Logger} that drops every line: these tests assert behaviour, not log output. */
    private static final Logger SILENT = new Logger() {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    };
}
