package com.uxplima.uxmessentials.economy.adapter.outbound.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The load-safe contract of the three reflection backends (PlayerPoints, CoinsEngine, zEssentials), the
 * property their whole reflective design exists to protect. Naming one of these currencies on a server without
 * its host plugin must never classload the plugin's SDK, because a hard SDK reference would surface as a
 * {@link NoClassDefFoundError} at enable. This pins both halves: with the host plugin absent every operation is
 * a safe no-op, and no field or method signature on the backend (or its base) carries an SDK type, so
 * constructing and exercising one pulls none of that SDK in.
 */
class ReflectiveCurrencyBackendAbsentTest {

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
    void playerPointsIsALoadSafeNoOpWhenItsPluginIsAbsent() {
        assertAbsentBackendIsLoadSafe(new PlayerPointsCurrencyBackend(server, SILENT), "org.black_ixx");
    }

    @Test
    void coinsEngineIsALoadSafeNoOpWhenItsPluginIsAbsent() {
        assertAbsentBackendIsLoadSafe(new CoinsEngineCurrencyBackend("gold", server, SILENT), "su.nightexpress");
    }

    @Test
    void zEssentialsIsALoadSafeNoOpWhenItsPluginIsAbsent() {
        assertAbsentBackendIsLoadSafe(new ZEssentialsCurrencyBackend("tokens", server, SILENT), "fr.maxlego08");
    }

    private void assertAbsentBackendIsLoadSafe(CurrencyBackend backend, String sdkPackage) {
        // The host plugin is not registered in the mock server, so the present-guard short-circuits every call
        // before any reflection runs.
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
        // The structural half of the load-safe proof: no field or method signature on the backend or its base
        // names the SDK package, so loading and calling the class on a plugin-less server pulls in none of it.
        assertThat(referencesPackage(backend.getClass(), sdkPackage)).isFalse();
    }

    /** Whether {@code type} (walking up to {@code Object}) declares any field or method signature in {@code prefix}. */
    private static boolean referencesPackage(Class<?> type, String prefix) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (inPackage(method.getReturnType(), prefix)) {
                    return true;
                }
                for (Class<?> parameter : method.getParameterTypes()) {
                    if (inPackage(parameter, prefix)) {
                        return true;
                    }
                }
            }
            for (Field field : c.getDeclaredFields()) {
                if (inPackage(field.getType(), prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean inPackage(Class<?> type, String prefix) {
        return type.getName().startsWith(prefix);
    }

    /** A {@link Logger} that drops every line: this test asserts behaviour, not log output. */
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
