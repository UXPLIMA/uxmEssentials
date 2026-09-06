package com.uxplima.uxmessentials.playerwarps.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.playerwarps.domain.ChargeError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.RentState;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.junit.jupiter.api.Test;

class SettleRentTest {

    private static final Instant NOW = Instant.parse("2026-07-10T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration PERIOD = Duration.ofDays(7);
    private static final Duration GRACE = Duration.ofDays(3);

    private final PlayerWarpTestSupport.Repo repo = new PlayerWarpTestSupport.Repo();
    private final PlayerWarpTestSupport.Economy economy = new PlayerWarpTestSupport.Economy();

    private static RentConfig config(Set<String> exemptWorlds) {
        return new RentConfig(
                true,
                new BigDecimal("100"),
                "default",
                PERIOD,
                GRACE,
                List.of(Duration.ofHours(24)),
                Set.of(),
                Set.of(),
                exemptWorlds);
    }

    private SettleRent settleWith(RentConfig config) {
        return new SettleRent(repo, economy, new RentPolicy(), config, CLOCK);
    }

    private PlayerWarp active(PlayerRef owner, String name, RentState rent) {
        return repo.put(PlayerWarpTestSupport.warp(owner, name).withRent(rent, NOW));
    }

    private PlayerWarp suspended(PlayerRef owner, String name, RentState rent) {
        return repo.put(PlayerWarpTestSupport.warp(owner, name)
                .withStatus(WarpStatus.SUSPENDED, NOW)
                .withRent(rent, NOW));
    }

    @Test
    void anOverdueActiveWarpWhoseOwnerCannotPayIsSuspendedWithAnArchiveDeadline() {
        PlayerRef owner = PlayerWarpTestSupport.ref("mara");
        PlayerWarp warp =
                active(owner, "citadel", new RentState(NOW.minusSeconds(1), Optional.empty(), Optional.empty()));
        economy.collectReturns(Result.err(ChargeError.INSUFFICIENT_FUNDS));

        RentOutcome outcome = settleWith(config(Set.of())).settle(warp);

        assertThat(outcome).isEqualTo(RentOutcome.SUSPENDED);
        PlayerWarp stored = repo.stored("citadel");
        assertThat(stored.status()).isEqualTo(WarpStatus.SUSPENDED);
        assertThat(stored.rent()).isPresent();
        assertThat(stored.rent().orElseThrow().suspendedAt()).contains(NOW);
        assertThat(stored.rent().orElseThrow().archiveAfter()).contains(NOW.plus(GRACE));
        // The charge was attempted for the configured rent against the owner, and no money-losing check preceded it.
        assertThat(economy.lastCollectAmount).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(economy.lastCollectOwner).isEqualTo(owner);
    }

    @Test
    void aSuspendedWarpPastItsArchiveDeadlineIsArchivedWithoutACharge() {
        PlayerRef owner = PlayerWarpTestSupport.ref("bram");
        PlayerWarp warp = suspended(
                owner,
                "keep",
                new RentState(
                        NOW.minus(Duration.ofDays(5)),
                        Optional.of(NOW.minus(Duration.ofDays(4))),
                        Optional.of(NOW.minusSeconds(1))));

        RentOutcome outcome = settleWith(config(Set.of())).settle(warp);

        assertThat(outcome).isEqualTo(RentOutcome.ARCHIVED);
        assertThat(repo.stored("keep").status()).isEqualTo(WarpStatus.ARCHIVED);
        // Archiving never charges: the grace window is a payment window, not the archive step.
        assertThat(economy.lastCollectWarp).isNull();
    }

    @Test
    void aSuspendedWarpWhoseOwnerNowHasFundsIsRestoredAndItsTermAdvanced() {
        PlayerRef owner = PlayerWarpTestSupport.ref("iris");
        PlayerWarp warp = suspended(
                owner,
                "haven",
                new RentState(
                        NOW.minus(Duration.ofDays(2)),
                        Optional.of(NOW.minus(Duration.ofDays(1))),
                        Optional.of(NOW.plus(Duration.ofDays(2)))));
        economy.collectReturns(Result.ok());

        RentOutcome outcome = settleWith(config(Set.of())).settle(warp);

        assertThat(outcome).isEqualTo(RentOutcome.RESTORED);
        PlayerWarp stored = repo.stored("haven");
        assertThat(stored.status()).isEqualTo(WarpStatus.ACTIVE);
        RentState rent = stored.rent().orElseThrow();
        assertThat(rent.paidUntil()).isEqualTo(NOW.plus(PERIOD));
        assertThat(rent.suspendedAt()).isEmpty();
        assertThat(rent.archiveAfter()).isEmpty();
        // The reminder dedup counter is reset on payment so the next term's reminders start fresh.
        assertThat(repo.reminded.get(warp.id().orElseThrow())).isZero();
    }

    @Test
    void anOverdueActiveWarpThatPaysRenewsAndAdvancesItsTerm() {
        PlayerRef owner = PlayerWarpTestSupport.ref("tomas");
        PlayerWarp warp =
                active(owner, "spire", new RentState(NOW.minusSeconds(1), Optional.empty(), Optional.empty()));
        economy.collectReturns(Result.ok());

        RentOutcome outcome = settleWith(config(Set.of())).settle(warp);

        assertThat(outcome).isEqualTo(RentOutcome.RENEWED);
        PlayerWarp stored = repo.stored("spire");
        assertThat(stored.status()).isEqualTo(WarpStatus.ACTIVE);
        assertThat(stored.rent().orElseThrow().paidUntil()).isEqualTo(NOW.plus(PERIOD));
        assertThat(repo.reminded.get(warp.id().orElseThrow())).isZero();
    }

    @Test
    void anExemptWarpIsLeftEntirelyUntouched() {
        PlayerRef owner = PlayerWarpTestSupport.ref("nadia");
        PlayerWarp warp =
                active(owner, "outpost", new RentState(NOW.minusSeconds(1), Optional.empty(), Optional.empty()));

        // The warp lives in "world", which the config exempts, so no charge and no status change.
        RentOutcome outcome = settleWith(config(Set.of("world"))).settle(warp);

        assertThat(outcome).isEqualTo(RentOutcome.UNCHANGED);
        assertThat(repo.stored("outpost").status()).isEqualTo(WarpStatus.ACTIVE);
        assertThat(economy.lastCollectWarp).isNull();
    }

    @Test
    void aWarpNotYetEnrolledInRentIsSkipped() {
        PlayerRef owner = PlayerWarpTestSupport.ref("ori");
        PlayerWarp warp = repo.put(PlayerWarpTestSupport.warp(owner, "unrented"));

        RentOutcome outcome = settleWith(config(Set.of())).settle(warp);

        assertThat(outcome).isEqualTo(RentOutcome.UNCHANGED);
        assertThat(economy.lastCollectWarp).isNull();
    }

    @Test
    void aWarpStillPaidThroughIsUnchanged() {
        PlayerRef owner = PlayerWarpTestSupport.ref("vex");
        PlayerWarp warp =
                active(owner, "manor", new RentState(NOW.plus(Duration.ofDays(3)), Optional.empty(), Optional.empty()));

        RentOutcome outcome = settleWith(config(Set.of())).settle(warp);

        assertThat(outcome).isEqualTo(RentOutcome.UNCHANGED);
        assertThat(economy.lastCollectWarp).isNull();
    }

    @Test
    void aFailingRetryLeavesTheWarpSuspendedForTheArchivePass() {
        PlayerRef owner = PlayerWarpTestSupport.ref("cael");
        Instant archiveAfter = NOW.plus(Duration.ofDays(1));
        PlayerWarp warp = suspended(
                owner,
                "roost",
                new RentState(
                        NOW.minus(Duration.ofDays(1)),
                        Optional.of(NOW.minus(Duration.ofDays(1))),
                        Optional.of(archiveAfter)));
        economy.collectReturns(Result.err(ChargeError.INSUFFICIENT_FUNDS));

        RentOutcome outcome = settleWith(config(Set.of())).settle(warp);

        assertThat(outcome).isEqualTo(RentOutcome.UNCHANGED);
        PlayerWarp stored = repo.stored("roost");
        assertThat(stored.status()).isEqualTo(WarpStatus.SUSPENDED);
        // The archive deadline is untouched, so the warp is archived only once that lapses: never hard-deleted here.
        assertThat(stored.rent().orElseThrow().archiveAfter()).contains(archiveAfter);
    }

    @Test
    void aSuspendedWarpWithNoRentStateNeverReachesTheChargePath() {
        // A defensive guard: a suspended warp is expected to carry a rent state, but if one somehow lacks it the
        // settle skips rather than throwing, leaving the row exactly as it was.
        PlayerRef owner = PlayerWarpTestSupport.ref("sable");
        PlayerWarp warp = repo.put(PlayerWarpTestSupport.warp(owner, "cellar").withStatus(WarpStatus.SUSPENDED, NOW));
        PlayerWarpId id = warp.id().orElseThrow();

        RentOutcome outcome = settleWith(config(Set.of())).settle(warp);

        assertThat(outcome).isEqualTo(RentOutcome.UNCHANGED);
        assertThat(repo.findById(id).orElseThrow().status()).isEqualTo(WarpStatus.SUSPENDED);
        assertThat(economy.lastCollectWarp).isNull();
    }
}
