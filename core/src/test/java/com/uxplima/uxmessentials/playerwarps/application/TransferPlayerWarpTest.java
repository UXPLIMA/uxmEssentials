package com.uxplima.uxmessentials.playerwarps.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Transfer is owner-only by the capability matrix; a delegate can never move the warp out from under its owner. */
class TransferPlayerWarpTest {

    private static final PlayerWarpName HUB = PlayerWarpName.of("hub");

    private PlayerWarpTestSupport.Repo repository;
    private PlayerWarpTestSupport.Members members;
    private PlayerWarpTestSupport.Sink sink;
    private TransferPlayerWarp transfer;
    private PlayerRef owner;
    private PlayerRef coOwner;
    private PlayerRef newOwner;
    private PlayerWarp warp;

    @BeforeEach
    void setUp() {
        repository = new PlayerWarpTestSupport.Repo();
        members = new PlayerWarpTestSupport.Members();
        sink = new PlayerWarpTestSupport.Sink();
        transfer = new TransferPlayerWarp(
                repository,
                new WarpAuthorization(members),
                PlayerWarpTestSupport.notifier(sink),
                PlayerWarpTestSupport.CLOCK);
        owner = PlayerWarpTestSupport.ref("Owner");
        coOwner = PlayerWarpTestSupport.ref("CoOwner");
        newOwner = PlayerWarpTestSupport.ref("Heir");
        warp = repository.put(PlayerWarpTestSupport.warp(owner, "hub"));
        members.grant(warp.id().orElseThrow(), coOwner.uuid(), WarpRole.CO_OWNER);
    }

    @Test
    void theOwnerTransfersOwnershipInPlace() {
        Result<Unit, PlayerWarpError> result = transfer.transfer(owner, HUB, newOwner);

        assertThat(result.isOk()).isTrue();
        PlayerWarp saved = repository.stored("hub");
        assertThat(saved.owner().uuid()).isEqualTo(newOwner.uuid());
        assertThat(saved.ownerName()).isEqualTo("Heir");
        assertThat(saved.id()).isEqualTo(warp.id());
        // A transfer hands over the warp, not a clean slate: the accrued bank stays put (spec §1).
        assertThat(saved.earnings()).isEqualTo(warp.earnings());
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.transferred"));
    }

    @Test
    void aCoOwnerMayNotTransfer() {
        Result<Unit, PlayerWarpError> result = transfer.transfer(coOwner, HUB, newOwner);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NO_PERMISSION);
        assertThat(repository.stored("hub").owner().uuid()).isEqualTo(owner.uuid());
    }

    @Test
    void transferringAMissingWarpIsNotFound() {
        Result<Unit, PlayerWarpError> result = transfer.transfer(owner, PlayerWarpName.of("ghost"), newOwner);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NOT_FOUND);
    }

    @Test
    void adminSetOwnerReassignsByIdWithNoRoleGate() {
        Result<Unit, PlayerWarpError> result = transfer.adminSetOwner(warp.id().orElseThrow(), newOwner);

        assertThat(result.isOk()).isTrue();
        PlayerWarp saved = repository.stored("hub");
        assertThat(saved.owner().uuid()).isEqualTo(newOwner.uuid());
        assertThat(saved.ownerName()).isEqualTo("Heir");
        assertThat(saved.id()).isEqualTo(warp.id());
    }

    @Test
    void adminSetOwnerAgainstAStaleIdIsNotFound() {
        Result<Unit, PlayerWarpError> result =
                transfer.adminSetOwner(com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId.of(9_999L), newOwner);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NOT_FOUND);
    }

    @Test
    void aSponsoredWarpCannotBeTransferred() {
        sponsor(warp);

        Result<Unit, PlayerWarpError> result = transfer.transfer(owner, HUB, newOwner);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.SPONSORED_LOCKED);
        // The warp stays with its owner: a sponsored slot is bought against them, so it must not move mid-term.
        assertThat(repository.stored("hub").owner().uuid()).isEqualTo(owner.uuid());
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.sponsored-locked"));
    }

    @Test
    void adminSetOwnerAlsoRefusesASponsoredWarp() {
        sponsor(warp);

        Result<Unit, PlayerWarpError> result = transfer.adminSetOwner(warp.id().orElseThrow(), newOwner);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.SPONSORED_LOCKED);
        assertThat(repository.stored("hub").owner().uuid()).isEqualTo(owner.uuid());
    }

    /** Give {@code warp} a sponsorship still live at the fixed clock, so the no-transfer guard trips. */
    private void sponsor(PlayerWarp warp) {
        repository.save(warp.withSponsorship(
                java.util.Optional.of(new com.uxplima.uxmessentials.playerwarps.domain.Sponsorship(
                        PlayerWarpTestSupport.CLOCK.instant().plusSeconds(3600), 0)),
                PlayerWarpTestSupport.CLOCK.instant()));
    }
}
