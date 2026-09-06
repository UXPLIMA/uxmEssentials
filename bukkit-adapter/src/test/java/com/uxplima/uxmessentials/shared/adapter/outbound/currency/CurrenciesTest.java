package com.uxplima.uxmessentials.shared.adapter.outbound.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.CurrencyBackendRegistry;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;

/**
 * The façade's spec grammar and caching mechanics, over an in-memory backend set: the backend head is
 * normalised while a sub-currency name keeps its case, one provider instance is cached per normalised spec, a
 * blank spec resolves the configured default, and an unknown spec is a safe no-op. The backend-resolution
 * behaviour. A configured currency over its own backend, a bare backend id as a synthetic currency, an
 * unknown spec as unavailable, lives in {@link CurrenciesBackedTest}.
 */
class CurrenciesTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private Currencies currenciesWithDefault(String defaultSpec) {
        return new Currencies(
                () -> new EconomyBackends(
                        CurrencyBackendRegistry.of(List.of(new FakeCurrencyBackend("vault"))),
                        CurrencyRegistry.single(
                                Currency.builder(CurrencyId.of("coins")).build())),
                SILENT,
                defaultSpec);
    }

    @Test
    void resolve_normalisesTheBackendHeadAndKeepsTheCurrencyName() {
        Currencies currencies = currenciesWithDefault("vault");

        assertThat(currencies.resolve("VAULT").id()).isEqualTo("vault");
        assertThat(currencies.resolve("  Vault  ").id()).isEqualTo("vault");
        // The head lower-cases, but a sub-currency name is kept verbatim: a plugin's currency lookup can be
        // case-sensitive, so coinsengine:Gold and coinsengine:gold must not collapse to the same currency.
        assertThat(currencies.resolve("CoinsEngine:Gold").id()).isEqualTo("coinsengine:Gold");
    }

    @Test
    void resolve_cachesOneProviderInstancePerNormalisedSpec() {
        Currencies currencies = currenciesWithDefault("vault");

        assertThat(currencies.resolve("vault")).isSameAs(currencies.resolve("vault"));
        assertThat(currencies.resolve("vault")).isSameAs(currencies.resolve("VAULT"));
        assertThat(currencies.resolve("nope")).isSameAs(currencies.resolve("nope"));
    }

    @Test
    void resolve_blankSpecResolvesTheConfiguredDefault() {
        Currencies currencies = currenciesWithDefault("vault");

        assertThat(currencies.defaultCurrency()).isEqualTo("vault");
        assertThat(currencies.resolve("").id()).isEqualTo("vault");
        // A blank spec and the explicit default share the one cached default provider.
        assertThat(currencies.resolve("   ")).isSameAs(currencies.resolve("vault"));
    }

    @Test
    void resolve_unknownSpecYieldsANoOpProvider() {
        Currencies currencies = currenciesWithDefault("vault");
        CurrencyProvider provider = currencies.resolve("dogecoin");

        assertThat(provider.id()).isEqualTo("dogecoin");
        assertThat(provider.available()).isFalse();
        assertThatCode(() -> {
                    assertThat(provider.balance(ALICE)).isZero();
                    assertThat(provider.has(ALICE, 1)).isFalse();
                    assertThat(provider.withdraw(ALICE, 1)).isFalse();
                    assertThat(provider.deposit(ALICE, 1)).isFalse();
                })
                .doesNotThrowAnyException();
    }

    @Test
    void blankDefaultCurrencyFallsBackToVault() {
        Currencies currencies = currenciesWithDefault("   ");

        assertThat(currencies.defaultCurrency()).isEqualTo("vault");
        assertThat(currencies.resolve("").id()).isEqualTo("vault");
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
