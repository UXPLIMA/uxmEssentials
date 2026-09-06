package com.uxplima.uxmessentials.playerwarps.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleport;
import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleportStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.playerwarps.domain.ChargeError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.playerwarps.domain.VisitSummary;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpEarnings;
import com.uxplima.uxmessentials.playerwarps.domain.WarpEffects;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.playerwarps.domain.WarpTimingOverrides;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import org.junit.jupiter.api.Test;

/**
 * The arrival half of a cross-server teleport, {@link CrossServerArrival}, against fakes for every port and a
 * synchronous scheduler so the whole off-tick → delayed → region-hop chain runs inline. Each case asserts the
 * observable outcome: whether the pending row was cleared, whether the local hop fired, whether the origin's
 * charge was refunded, and which message key was delivered. The handler's whole job is deciding, per row, which
 * of those happen. The scheduler records the delay it was handed so the settling window is proven Folia-safe
 * (the hop is deferred, not run on the join thread).
 */
class CrossServerArrivalTest {

    private static final WorldRef WORLD = new WorldRef(new UUID(3L, 3L), "world");
    private static final Position SPOT = Position.of(WORLD, 8, 64, 8);
    private static final PlayerRef PLAYER = new PlayerRef(new UUID(1L, 1L), "Visitor");
    private static final PlayerRef OWNER = new PlayerRef(new UUID(2L, 2L), "Owner");
    private static final PlayerWarpName NAME = PlayerWarpName.of("base");
    private static final String LOCAL = "survival";
    private static final Duration DELAY = Duration.ofSeconds(1L);
    private static final Duration TTL = Duration.ofSeconds(30L);
    private static final Instant NOW = Instant.parse("2026-07-10T00:00:00Z");

    private final FakePendingStore store = new FakePendingStore();
    private final PlayerWarpTestSupport.Repo repository = new PlayerWarpTestSupport.Repo();
    private final RecordingTeleporter teleporter = new RecordingTeleporter();
    private final FakeEconomy economy = new FakeEconomy();
    private final PlayerWarpTestSupport.Sink sink = new PlayerWarpTestSupport.Sink();
    private final RecordingScheduler scheduler = new RecordingScheduler();

    private CrossServerArrival arrival() {
        return new CrossServerArrival(
                store,
                repository,
                teleporter,
                Optional.of(economy),
                PlayerWarpTestSupport.notifier(sink),
                scheduler,
                LOCAL,
                DELAY,
                TTL,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aMatchingPendingRowTeleportsAfterTheDelayAndClearsTheRow() {
        repository.put(warp(1L, LOCAL, WarpStatus.ACTIVE));
        store.record(pending(1L, LOCAL, Optional.empty(), NOW));

        arrival().onArrival(PLAYER);

        assertThat(scheduler.lastAfterDelay).isEqualTo(DELAY);
        assertThat(teleporter.hops).isEqualTo(1);
        assertThat(teleporter.lastWarp).isEqualTo(NAME);
        assertThat(store.find(PLAYER.uuid()))
                .as("the row is cleared once honoured")
                .isEmpty();
        assertThat(delivered()).anyMatch(key("pwarp.cross-server.arrived"));
    }

    @Test
    void aVanishedWarpClearsRefundsTheChargeAndNotifiesFailure() {
        // No warp is put, so findById misses: the warp was deleted between send and arrival.
        store.record(pending(1L, LOCAL, Optional.of(WarpCost.of(new BigDecimal("100"), "coins")), NOW));

        arrival().onArrival(PLAYER);

        assertThat(teleporter.hops).isZero();
        assertThat(store.find(PLAYER.uuid())).isEmpty();
        assertThat(economy.refunds).containsExactly(new BigDecimal("100"));
        assertThat(delivered()).anyMatch(key("pwarp.cross-server.failed"));
    }

    @Test
    void aSuspendedWarpFailsAndRefunds() {
        repository.put(warp(1L, LOCAL, WarpStatus.SUSPENDED));
        store.record(pending(1L, LOCAL, Optional.of(WarpCost.of(new BigDecimal("40"), "default")), NOW));

        arrival().onArrival(PLAYER);

        assertThat(teleporter.hops).isZero();
        assertThat(economy.refunds).containsExactly(new BigDecimal("40"));
        assertThat(delivered()).anyMatch(key("pwarp.cross-server.failed"));
    }

    @Test
    void aWarpThatHasMovedToAnotherBackendFailsAndRefunds() {
        repository.put(warp(1L, "creative", WarpStatus.ACTIVE)); // moved off this backend since the request
        store.record(pending(1L, LOCAL, Optional.of(WarpCost.of(new BigDecimal("25"), "default")), NOW));

        arrival().onArrival(PLAYER);

        assertThat(teleporter.hops).isZero();
        assertThat(economy.refunds).containsExactly(new BigDecimal("25"));
        assertThat(delivered()).anyMatch(key("pwarp.cross-server.failed"));
    }

    @Test
    void aRowAimedAtAnotherServerIsClearedWithoutTeleportingOrRefunding() {
        repository.put(warp(1L, LOCAL, WarpStatus.ACTIVE));
        store.record(pending(1L, "creative", Optional.of(WarpCost.of(new BigDecimal("100"), "coins")), NOW));

        arrival().onArrival(PLAYER);

        assertThat(teleporter.hops).isZero();
        assertThat(store.find(PLAYER.uuid())).as("a misrouted row is dropped").isEmpty();
        assertThat(economy.refunds).as("only expiry refunds a non-arriving row").isEmpty();
        assertThat(scheduler.lastAfterDelay)
                .as("no delayed completion is scheduled")
                .isNull();
    }

    @Test
    void anExpiredRowIsRefundedAndClearedWithoutTeleporting() {
        repository.put(warp(1L, LOCAL, WarpStatus.ACTIVE));
        Instant stale = NOW.minus(TTL).minusSeconds(1);
        store.record(pending(1L, LOCAL, Optional.of(WarpCost.of(new BigDecimal("70"), "coins")), stale));

        arrival().onArrival(PLAYER);

        assertThat(teleporter.hops).isZero();
        assertThat(store.find(PLAYER.uuid())).isEmpty();
        assertThat(economy.refunds).containsExactly(new BigDecimal("70"));
        assertThat(scheduler.lastAfterDelay).isNull();
    }

    @Test
    void aPlayerWithNoPendingRowIsLeftAlone() {
        arrival().onArrival(PLAYER);

        assertThat(teleporter.hops).isZero();
        assertThat(economy.refunds).isEmpty();
        assertThat(delivered()).isEmpty();
    }

    private List<String> delivered() {
        return sink.delivered;
    }

    private static java.util.function.Predicate<String> key(String catalogKey) {
        return text -> text.startsWith(catalogKey);
    }

    private PendingTeleport pending(long warp, String target, Optional<WarpCost> paid, Instant requestedAt) {
        return new PendingTeleport(PLAYER.uuid(), PlayerWarpId.of(warp), target, "lobby", requestedAt, paid);
    }

    private static PlayerWarp warp(long id, String serverId, WarpStatus status) {
        return new PlayerWarp(
                Optional.of(PlayerWarpId.of(id)),
                OWNER,
                OWNER.name(),
                NAME,
                Optional.empty(),
                SPOT,
                Optional.of(serverId),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                WarpAccess.PUBLIC,
                false,
                status,
                WarpCost.free(),
                WarpEarnings.zero("default"),
                RatingSummary.empty(),
                VisitSummary.empty(),
                0,
                Optional.empty(),
                Optional.empty(),
                WarpEffects.none(),
                WarpTimingOverrides.none(),
                NOW,
                NOW);
    }

    /** A pending store keyed by player, recording clears so a test can prove the row was dropped. */
    private static final class FakePendingStore implements PendingTeleportStore {
        private final Map<UUID, PendingTeleport> rows = new HashMap<>();

        @Override
        public void record(PendingTeleport pending) {
            rows.put(pending.player(), pending);
        }

        @Override
        public Optional<PendingTeleport> find(UUID player) {
            return Optional.ofNullable(rows.get(player));
        }

        @Override
        public void clear(UUID player) {
            rows.remove(player);
        }
    }

    private static final class RecordingTeleporter implements PlayerWarpTeleporter {
        int hops;

        @org.jspecify.annotations.Nullable PlayerWarpName lastWarp;

        @Override
        public void teleportTo(PlayerRef who, PlayerWarp warp) {
            hops++;
            lastWarp = warp.name();
        }
    }

    /** An economy fake recording the amount of each refund, so a test can prove the exact charge was returned. */
    private static final class FakeEconomy implements PlayerWarpEconomy {
        final List<BigDecimal> refunds = new ArrayList<>();

        @Override
        public Result<Unit, ChargeError> chargeAndAccrue(
                PlayerRef payer, PlayerWarpId warp, BigDecimal price, String currencyId) {
            return Result.ok();
        }

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            return true;
        }

        @Override
        public Result<Unit, ChargeError> withdraw(PlayerWarpId warp, PlayerRef to) {
            return Result.ok();
        }

        @Override
        public Result<Unit, ChargeError> refund(PlayerRef to, BigDecimal amount, String currencyId) {
            refunds.add(amount);
            return Result.ok();
        }

        @Override
        public Result<Unit, ChargeError> collectRent(
                PlayerWarpId warp, PlayerRef owner, BigDecimal amount, String currencyId) {
            return Result.ok();
        }

        @Override
        public Result<Unit, ChargeError> chargeOwner(PlayerRef owner, BigDecimal amount, String currencyId) {
            return Result.ok();
        }
    }

    /** A scheduler that runs every task inline and records the last {@code asyncAfter} delay it was handed. */
    private static final class RecordingScheduler implements Scheduler {
        @org.jspecify.annotations.Nullable Duration lastAfterDelay;

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            lastAfterDelay = delay;
            task.run();
        }
    }
}
