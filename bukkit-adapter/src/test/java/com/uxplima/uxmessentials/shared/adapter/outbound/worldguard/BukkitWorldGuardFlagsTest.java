package com.uxplima.uxmessentials.shared.adapter.outbound.worldguard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of the shared WorldGuard flag seam on a server with <em>no</em> WorldGuard: the present-guard
 * short-circuits, so the {@link WorldGuardSetPwarpFlagRegistrar} registers nothing without throwing and
 * {@link BukkitWorldGuardFlags} reports "not supported" / "not denied" (the fail-open default). The
 * DENY-interpretation of a resolved {@code StateFlag.State}, the one piece of the query chain that does not need a
 * live WorldGuard: is pinned directly via {@link BukkitWorldGuardFlags#isDeny}.
 */
class BukkitWorldGuardFlagsTest {

    private ServerMock server;
    private Position where;
    private BukkitWorldGuardFlags flags;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        WorldMock world = server.addSimpleWorld("world");
        where = Position.of(new WorldRef(world.getUID(), world.getName()), 10.5, 64.0, 20.5);
        flags = new BukkitWorldGuardFlags(server, new NoopLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void registrarIsSilentNoOpWhenWorldGuardIsAbsent() {
        assertThatCode(() ->
                        WorldGuardSetPwarpFlagRegistrar.register(server, java.util.logging.Logger.getLogger("test")))
                .doesNotThrowAnyException();
    }

    @Test
    void supportedIsFalseWhenWorldGuardIsAbsent() {
        assertThat(flags.supported()).isFalse();
    }

    @Test
    void deniesFlagReturnsFalseWhenWorldGuardIsAbsent() {
        assertThat(flags.deniesFlag("set-pwarp", where)).isFalse();
    }

    @Test
    void interpretsOnlyADenyStateAsDenied() {
        assertThat(BukkitWorldGuardFlags.isDeny(FakeState.DENY)).isTrue();
        assertThat(BukkitWorldGuardFlags.isDeny(FakeState.ALLOW)).isFalse();
        assertThat(BukkitWorldGuardFlags.isDeny(null)).isFalse();
        assertThat(BukkitWorldGuardFlags.isDeny("DENY")).isFalse();
    }

    /** Stand-in for WorldGuard's {@code StateFlag.State} enum, the interpretation keys off the constant name. */
    private enum FakeState {
        ALLOW,
        DENY
    }

    /** A logger that swallows everything: the absent-WorldGuard path emits nothing anyway. */
    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
