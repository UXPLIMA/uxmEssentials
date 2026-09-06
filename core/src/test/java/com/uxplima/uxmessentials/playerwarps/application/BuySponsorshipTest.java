package com.uxplima.uxmessentials.playerwarps.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.Sponsorship;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The sponsorship purchase gate: owner-only, run through the ordered checks (cooldown → concurrent limit → free slot →
 * charge), and only writing the sponsorship once the guarded debit takes. A delegate is refused SPONSOR outright, and
 * every refusal leaves the warp unsponsored and, for the pre-charge refusals, spends nothing.
 */
class BuySponsorshipTest {

    private static final PlayerWarpName HUB = PlayerWarpName.of("hub");
    private static final BigDecimal PRICE = new BigDecimal("1000");

    private PlayerWarpTestSupport.Repo repository;
    private PlayerWarpTestSupport.Members members;
    private PlayerWarpTestSupport.Sink sink;
    private PlayerWarpTestSupport.Economy economy;
    private PlayerRef owner;
    private PlayerWarp warp;

    @BeforeEach
    void setUp() {
        repository = new PlayerWarpTestSupport.Repo();
        members = new PlayerWarpTestSupport.Members();
        sink = new PlayerWarpTestSupport.Sink();
        economy = new PlayerWarpTestSupport.Economy();
        owner = PlayerWarpTestSupport.ref("Owner");
        warp = repository.put(PlayerWarpTestSupport.warp(owner, "hub"));
    }

    @Test
    void chargesTheOwnerAndSetsTheSponsorship() {
        Result<Unit, PlayerWarpError> result = buy(config(5, 1)).buy(owner, HUB, 7);

        assertThat(result.isOk()).isTrue();
        assertThat(economy.lastChargeOwner).isEqualTo(owner);
        assertThat(economy.lastChargeAmount).isEqualByComparingTo(PRICE);
        Sponsorship sponsorship = repository.stored("hub").sponsorship().orElseThrow();
        assertThat(sponsorship.slot()).isZero();
        assertThat(sponsorship.activeUntil())
                .isEqualTo(PlayerWarpTestSupport.CLOCK.instant().plus(Duration.ofDays(7)));
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.sponsored"));
    }

    @Test
    void clampsTheTermToTheConfiguredMaximum() {
        buy(config(5, 1)).buy(owner, HUB, 999);

        assertThat(repository.stored("hub").sponsorship().orElseThrow().activeUntil())
                .isEqualTo(PlayerWarpTestSupport.CLOCK.instant().plus(Duration.ofDays(7)));
    }

    @Test
    void picksTheLowestFreeSlot() {
        // Another owner already holds slot 0, so this purchase takes slot 1.
        occupySlot(0, PlayerWarpTestSupport.ref("Rival"), "rival-hub");

        buy(config(5, 5)).buy(owner, HUB, 7);

        assertThat(repository.stored("hub").sponsorship().orElseThrow().slot()).isEqualTo(1);
    }

    @Test
    void refusesWhileOnCooldown() {
        repository.putSponsorCooldown(
                warp.id().orElseThrow(), PlayerWarpTestSupport.CLOCK.instant().plusSeconds(3600));

        Result<Unit, PlayerWarpError> result = buy(config(5, 1)).buy(owner, HUB, 7);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.SPONSOR_COOLDOWN);
        assertThat(economy.lastChargeOwner).isNull(); // refused before the charge: nothing spent
        assertThat(repository.stored("hub").sponsorship()).isEmpty();
    }

    @Test
    void refusesAtTheConcurrentLimit() {
        // The owner already sponsors another of their warps, and the limit is one.
        occupySlot(0, owner, "other-hub");

        Result<Unit, PlayerWarpError> result = buy(config(5, 1)).buy(owner, HUB, 7);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.SPONSOR_LIMIT);
        assertThat(economy.lastChargeOwner).isNull();
        assertThat(repository.stored("hub").sponsorship()).isEmpty();
    }

    @Test
    void refusesWhenEverySlotIsTaken() {
        // One slot, already taken by another owner, so no slot is free for this purchase.
        occupySlot(0, PlayerWarpTestSupport.ref("Rival"), "rival-hub");

        Result<Unit, PlayerWarpError> result = buy(config(1, 5)).buy(owner, HUB, 7);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.SPONSOR_FULL);
        assertThat(economy.lastChargeOwner).isNull();
        assertThat(repository.stored("hub").sponsorship()).isEmpty();
    }

    @Test
    void refusesWhenTheChargeCannotTake() {
        economy.chargeOwnerReturns(
                Result.err(com.uxplima.uxmessentials.playerwarps.domain.ChargeError.INSUFFICIENT_FUNDS));

        Result<Unit, PlayerWarpError> result = buy(config(5, 1)).buy(owner, HUB, 7);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.CANNOT_AFFORD);
        // The charge was attempted (the debit is the double-spend-safe point), but it did not take, so no slot is set.
        assertThat(economy.lastChargeOwner).isEqualTo(owner);
        assertThat(repository.stored("hub").sponsorship()).isEmpty();
    }

    @Test
    void aManagerCannotSponsor() {
        PlayerRef manager = grant(WarpRole.MANAGER, "Manager");

        Result<Unit, PlayerWarpError> result = buy(config(5, 1)).buy(manager, HUB, 7);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NO_PERMISSION);
        assertThat(economy.lastChargeOwner).isNull();
    }

    @Test
    void aCoOwnerCannotSponsor() {
        PlayerRef coOwner = grant(WarpRole.CO_OWNER, "CoOwner");

        Result<Unit, PlayerWarpError> result = buy(config(5, 1)).buy(coOwner, HUB, 7);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NO_PERMISSION);
    }

    @Test
    void aMissingWarpIsNotFound() {
        Result<Unit, PlayerWarpError> result = buy(config(5, 1)).buy(owner, PlayerWarpName.of("ghost"), 7);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NOT_FOUND);
    }

    private BuySponsorship buy(SponsorConfig config) {
        return new BuySponsorship(
                repository,
                new WarpAuthorization(members),
                Optional.of(economy),
                PlayerWarpTestSupport.notifier(sink),
                config,
                PlayerWarpTestSupport.CLOCK);
    }

    private static SponsorConfig config(int slots, int maxConcurrent) {
        return new SponsorConfig(true, slots, 7, PRICE, "default", maxConcurrent, Duration.ofDays(3));
    }

    /** Store a warp owned by {@code who} already holding a live sponsorship in {@code slot}. */
    private void occupySlot(int slot, PlayerRef who, String name) {
        PlayerWarp other = repository.put(PlayerWarpTestSupport.warp(who, name));
        repository.save(other.withSponsorship(
                Optional.of(
                        new Sponsorship(PlayerWarpTestSupport.CLOCK.instant().plusSeconds(3600), slot)),
                PlayerWarpTestSupport.CLOCK.instant()));
    }

    /** Grant {@code role} to a fresh player on the warp and return their ref. */
    private PlayerRef grant(WarpRole role, String name) {
        PlayerRef delegate = PlayerWarpTestSupport.ref(name);
        PlayerWarpId id = warp.id().orElseThrow();
        members.grant(id, delegate.uuid(), role);
        return delegate;
    }
}
