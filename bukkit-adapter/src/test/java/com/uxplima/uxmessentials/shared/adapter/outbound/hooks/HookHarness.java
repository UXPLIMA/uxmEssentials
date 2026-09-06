package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import java.util.List;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Reusable present/absent test harness for {@link PluginHook}s, the seam every later hook feature (Vault,
 * HeadDatabase, the item providers) reuses for its absent-path test. A hook test mocks the server in its own
 * {@code @BeforeEach}/{@code @AfterEach}, then drives the hook through one of two entry points:
 *
 * <ul>
 *   <li>{@link #absent} resolves the hook with its target plugin not installed. The returned capability is
 *       the hook's no-op default, and every call on it must be a safe no-op: no exception, and crucially no
 *       {@link NoClassDefFoundError}, since the real implementation is never constructed.
 *   <li>{@link #present} registers a fake plugin named after the hook's target into the current mock, then
 *       resolves: the returned capability is the real implementation.
 * </ul>
 *
 * <p>The harness owns only the plugin-presence dimension; a hook whose real implementation needs further
 * server state (a ServicesManager-registered provider, say) layers that setup around {@link #present}.
 */
final class HookHarness {

    /** A {@link Logger} that drops every line: a hook test asserts behaviour, not bootstrap log output. */
    static final Logger SILENT = new Logger() {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    };

    private HookHarness() {}

    /** Resolve {@code hook} with its target plugin ABSENT; returns the no-op capability. */
    static <T> T absent(PluginHook<T> hook) {
        Hooks hooks = Hooks.resolve(MockBukkit.getMock(), SILENT, List.of(hook));
        return hooks.capability(hook.capability());
    }

    /**
     * Register a fake plugin named {@code hook.pluginName()} into the current mock, then resolve to the real
     * capability. The mock must already be active (the caller's {@code @BeforeEach} ran {@code MockBukkit.mock()}).
     */
    static <T> T present(PluginHook<T> hook) {
        MockBukkit.createMockPlugin(hook.pluginName());
        Hooks hooks = Hooks.resolve(MockBukkit.getMock(), SILENT, List.of(hook));
        return hooks.capability(hook.capability());
    }
}
