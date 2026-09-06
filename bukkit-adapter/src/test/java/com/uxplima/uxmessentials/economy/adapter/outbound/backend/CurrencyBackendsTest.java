package com.uxplima.uxmessentials.economy.adapter.outbound.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import com.uxplima.uxmessentials.economy.application.port.CurrencyBackendRegistry;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.Hooks;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Discovery of the currency backends the server has. The native ledger and Paper experience are always registered;
 * a backend whose host plugin is absent is not; and a foreign backend that does register comes back wrapped for
 * per-owner debit serialisation, observable as its {@code atomicDebit()} reporting false where the native ledger
 * reports true.
 */
class CurrencyBackendsTest {

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
    void alwaysRegistersTheNativeLedgerAndExperience() {
        CurrencyBackendRegistry registry = discover();

        assertThat(registry.ids()).contains("native", "exp");
    }

    @Test
    void doesNotRegisterABackendWhoseHostPluginIsAbsent() {
        CurrencyBackendRegistry registry = discover();

        assertThat(registry.ids()).doesNotContain("playerpoints");
    }

    @Test
    void aRegisteredForeignBackendIsWrappedWhileTheNativeLedgerIsNot() {
        MockBukkit.createMockPlugin("PlayerPoints");

        CurrencyBackendRegistry registry = discover();

        assertThat(registry.ids()).contains("playerpoints");
        assertThat(registry.find("playerpoints").orElseThrow().atomicDebit()).isFalse();
        assertThat(registry.find("native").orElseThrow().atomicDebit()).isTrue();
    }

    @Test
    void picksUpAConfiguredPlaceholderCurrency() {
        CurrencyBackendRegistry registry = discover(PLACEHOLDER_CONFIG);

        assertThat(registry.ids()).contains("placeholder:crowns");
    }

    @Test
    void registersNoPlaceholderBackendWhenTheMapIsAbsent() {
        CurrencyBackendRegistry registry = discover();

        assertThat(registry.ids()).noneMatch(id -> id.startsWith("placeholder:"));
    }

    private CurrencyBackendRegistry discover() {
        return discover(EMPTY_CONFIG);
    }

    private CurrencyBackendRegistry discover(ConfigStore config) {
        return CurrencyBackends.discover(
                server, emptyHooks(), SILENT, mock(Scheduler.class), mock(WalletRepository.class), config);
    }

    private Hooks emptyHooks() {
        return Hooks.resolve(server, SILENT, List.of());
    }

    /** A config that serves no backend maps, so no CoinsEngine/zEssentials currencies are enumerated. */
    private static final ConfigStore EMPTY_CONFIG = new ConfigStore() {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    };

    /** A config with one {@code backends.placeholder} entry, {@code crowns}, and the command templates it needs. */
    private static final ConfigStore PLACEHOLDER_CONFIG = new ConfigStore() {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return switch (path) {
                case "backends.placeholder.crowns.balance-placeholder" -> "%myeconomy_balance%";
                case "backends.placeholder.crowns.give-command" -> "myeco give %player% %amount%";
                case "backends.placeholder.crowns.take-command" -> "myeco take %player% %amount%";
                default -> fallback;
            };
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }

        @Override
        public List<String> getKeys(String path) {
            return "backends.placeholder".equals(path) ? List.of("crowns") : List.of();
        }
    };

    /** A {@link Logger} that drops every line: these tests assert the registry, not log output. */
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
