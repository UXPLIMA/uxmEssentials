package com.uxplima.uxmessentials.economy.adapter.outbound.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The experience backend. Against MockBukkit online players it is the one shipped backend that cannot be written
 * offline, its debit is guarded against overdraw, and a credit followed by a balance read agrees with the vanilla
 * experience curve: the round trip {@code applyTotal}/{@code readTotal} promises. Behind {@code EconomyProvider}
 * every call is off-tick, so the backend must hop onto the owner's entity thread before touching the live player;
 * the off-tick tests drive that hop through a fake {@link Scheduler} and prove the player is reached only inside it.
 */
class ExpCurrencyBackendTest {

    private static final Currency XP =
            Currency.builder(CurrencyId.of("xp")).precision(0).build();

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
    void anOfflineOwnerCannotBeCredited() {
        ExpCurrencyBackend backend = new ExpCurrencyBackend(server, new RecordingScheduler(), new RecordingLogger());
        PlayerRef ghost = new PlayerRef(UUID.randomUUID(), "Ghost");

        assertThat(backend.worksOffline()).isFalse();
        assertThat(backend.credit(ghost, Money.of(XP, BigDecimal.TEN)).errorOrThrow())
                .isEqualTo(TransferError.PLAYER_OFFLINE);
    }

    @Test
    void debitingMoreThanTheOwnerHasIsRejectedWithoutMutating() {
        PlayerMock player = server.addPlayer();
        player.setLevel(0);
        player.setExp(0);
        ExpCurrencyBackend backend = new ExpCurrencyBackend(server, new RecordingScheduler(), new RecordingLogger());
        PlayerRef ref = new PlayerRef(player.getUniqueId(), player.getName());
        backend.credit(ref, Money.of(XP, BigDecimal.valueOf(10)));

        assertThat(backend.debit(ref, Money.of(XP, BigDecimal.valueOf(11))).errorOrThrow())
                .isEqualTo(TransferError.INSUFFICIENT_FUNDS);
        assertThat(backend.balance(ref, XP).amount()).isEqualByComparingTo("10");
    }

    @Test
    void aCreditThenBalanceRoundTripsThroughTheVanillaCurve() {
        PlayerMock player = server.addPlayer();
        player.setLevel(0);
        player.setExp(0);
        ExpCurrencyBackend backend = new ExpCurrencyBackend(server, new RecordingScheduler(), new RecordingLogger());
        PlayerRef ref = new PlayerRef(player.getUniqueId(), player.getName());

        assertThat(backend.credit(ref, Money.of(XP, BigDecimal.valueOf(100))).isOk())
                .isTrue();
        assertThat(backend.balance(ref, XP).amount()).isEqualByComparingTo("100");
    }

    @Test
    void aCallerThatAlreadyOwnsTheEntityDoesTheWorkInlineWithoutScheduling() {
        PlayerMock player = server.addPlayer();
        player.setLevel(0);
        player.setExp(0);
        RecordingScheduler onOwnersThread = new RecordingScheduler();
        onOwnersThread.owns = true;
        ExpCurrencyBackend backend = new ExpCurrencyBackend(server, onOwnersThread, new RecordingLogger());
        PlayerRef ref = new PlayerRef(player.getUniqueId(), player.getName());

        assertThat(backend.credit(ref, Money.of(XP, BigDecimal.valueOf(30))).isOk())
                .isTrue();
        assertThat(backend.balance(ref, XP).amount()).isEqualByComparingTo("30");

        // Scheduling from the owning thread and then blocking on the result would deadlock, so the hop must be skipped.
        assertThat(onOwnersThread.entityCalls).isZero();
    }

    @Test
    void offTheTickThreadEachOperationReachesThePlayerThroughOneEntityHop() {
        UUID id = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getLevel()).thenReturn(1);
        when(player.getExp()).thenReturn(0f);
        Server offTick = offTickServer(id, player);
        RecordingScheduler scheduler = new RecordingScheduler();
        ExpCurrencyBackend backend = new ExpCurrencyBackend(offTick, scheduler, new RecordingLogger());
        PlayerRef ref = new PlayerRef(id, "Hopper");

        assertThat(backend.balance(ref, XP).amount()).isEqualByComparingTo("7");
        assertThat(backend.credit(ref, Money.of(XP, BigDecimal.valueOf(5))).isOk())
                .isTrue();
        assertThat(backend.debit(ref, Money.of(XP, BigDecimal.ONE)).isOk()).isTrue();

        assertThat(scheduler.entityCalls).isEqualTo(3);
        verify(player, atLeastOnce()).setLevel(anyInt());
    }

    @Test
    void anOwnerThatLeavesMidHopYieldsOfflineWithoutTouchingThePlayer() {
        UUID id = UUID.randomUUID();
        Player player = mock(Player.class);
        Server offTick = offTickServer(id, player);
        RecordingScheduler scheduler = new RecordingScheduler();
        scheduler.retire = true;
        RecordingLogger logger = new RecordingLogger();
        ExpCurrencyBackend backend = new ExpCurrencyBackend(offTick, scheduler, logger);
        PlayerRef ref = new PlayerRef(id, "Leaver");

        assertThat(backend.credit(ref, Money.of(XP, BigDecimal.TEN)).errorOrThrow())
                .isEqualTo(TransferError.PLAYER_OFFLINE);
        assertThat(backend.debit(ref, Money.of(XP, BigDecimal.TEN)).errorOrThrow())
                .isEqualTo(TransferError.PLAYER_OFFLINE);
        assertThat(backend.balance(ref, XP)).isEqualTo(Money.zero(XP));

        assertThat(scheduler.entityCalls).isEqualTo(3);
        verify(offTick, never()).getPlayer(any(UUID.class));
        verifyNoInteractions(player);
        assertThat(logger.warnings).isZero();
    }

    /** A server the backend can only reach the player through, so every operation is forced through the entity hop. */
    private static Server offTickServer(UUID id, Player player) {
        Server offTick = mock(Server.class);
        when(offTick.getPlayer(id)).thenReturn(player);
        return offTick;
    }

    /**
     * Runs the scheduled task the moment it is handed over, the way a real entity thread eventually would, so the
     * backend's future completes instead of timing out. {@code owns} drives the deadlock guard and {@code retire}
     * the player-left branch.
     */
    private static final class RecordingScheduler implements Scheduler {
        private int entityCalls;
        private boolean retire;
        private boolean owns;

        @Override
        public boolean ownsEntity(PlayerRef player) {
            return owns;
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task, Runnable retired) {
            entityCalls++;
            (retire ? retired : task).run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            throw new UnsupportedOperationException("the backend must use the retirement-aware onEntity");
        }

        @Override
        public void onGlobal(Runnable task) {
            throw new UnsupportedOperationException("unused");
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            throw new UnsupportedOperationException("unused");
        }

        @Override
        public void async(Runnable task) {
            throw new UnsupportedOperationException("unused");
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            throw new UnsupportedOperationException("unused");
        }
    }

    /** Counts warnings so a routine offline owner is provably not logged; the other levels are unused here. */
    private static final class RecordingLogger implements Logger {
        private int warnings;

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings++;
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
