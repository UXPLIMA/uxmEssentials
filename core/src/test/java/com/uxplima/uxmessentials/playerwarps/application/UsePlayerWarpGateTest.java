package com.uxplima.uxmessentials.playerwarps.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.playerwarps.application.port.CrossServerTeleport;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpPasswordStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpBanStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpMemberStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpWhitelistStore;
import com.uxplima.uxmessentials.playerwarps.domain.BanRecord;
import com.uxplima.uxmessentials.playerwarps.domain.ChargeError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.playerwarps.domain.VisitSummary;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpEarnings;
import com.uxplima.uxmessentials.playerwarps.domain.WarpEffects;
import com.uxplima.uxmessentials.playerwarps.domain.WarpMember;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.playerwarps.domain.WarpTimingOverrides;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.application.port.WarpSafetyChecker;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

/**
 * The ordered access gate of {@code UsePlayerWarp} against configurable fakes for every port and a fixed
 * {@link Clock}. Each case asserts both the {@link Result} and the observable side effects, whether the
 * teleport fired, whether the visit was recorded, whether the economy was charged, and whether a password
 * attempt was stamped, because the gate's whole value is in what it refuses to do.
 *
 * <p>The precedence matrix under test is exactly the spec order: status(1) → ban(2) → membership(3) →
 * access(4) → safe-landing(5) → cost(6). The closing jqwik property pins the security invariant that the new
 * gate is never looser than the T5 fail-closed stub: a non-owner is admitted only via PUBLIC access, a correct
 * password, being on the whitelist, or membership.
 *
 * <p>A final section covers the post-gate <em>routing</em> decision: a warp tagged for another backend hands off
 * to the {@link CrossServerTeleport} seam (carrying the exact charge) instead of the local teleporter, and, when
 * the seam is absent because the sub-group is off: is refused with a refund rather than a broken local hop.
 */
class UsePlayerWarpGateTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position SAFE_SPOT = Position.of(WORLD, 8, 64, 8);
    private static final PlayerWarpName NAME = PlayerWarpName.of("base");
    private static final Instant NOW = Instant.parse("2026-07-10T00:00:00Z");
    private static final String RIGHT_PASSWORD = "correct-horse";
    private static final String WRONG_PASSWORD = "wrong-guess";
    private static final WarpCost PRICE = WarpCost.of(new BigDecimal("100"));

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Owner");
    private static final PlayerRef VISITOR = new PlayerRef(UUID.randomUUID(), "Visitor");

    // ---- Step 1: status refuses everyone, including the owner. ----

    @Test
    void suspendedWarpRefusesEvenTheOwner() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PUBLIC, WarpStatus.SUSPENDED, WarpCost.free()));

        Result<Unit, PlayerWarpError> result = f.gate().useFor(OWNER, NAME);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.SUSPENDED);
        assertThat(f.teleporter.hops).isZero();
    }

    @Test
    void archivedWarpRefusesANonMember() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PUBLIC, WarpStatus.ARCHIVED, WarpCost.free()));

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.SUSPENDED);
        assertThat(f.teleporter.hops).isZero();
    }

    // ---- Step 2: ban precedes role. ----

    @Test
    void bannedOwnerIsRefusedBecauseBanPrecedesRole() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PUBLIC, WarpStatus.ACTIVE, WarpCost.free()));
        f.bans.banPermanently(OWNER.uuid());

        Result<Unit, PlayerWarpError> result = f.gate().useFor(OWNER, NAME);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.BANNED);
        assertThat(f.teleporter.hops).isZero();
    }

    @Test
    void bannedOwnerWithBanBypassProceedsBecauseRoleThenAdmits() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PRIVATE, WarpStatus.ACTIVE, WarpCost.free()));
        f.bans.banPermanently(OWNER.uuid());
        f.permissions.grant(OWNER, "uxmessentials.pwarp.bypass.ban");

        Result<Unit, PlayerWarpError> result = f.gate().useFor(OWNER, NAME);

        assertThat(result.isOk()).isTrue();
        assertThat(f.teleporter.hops).isEqualTo(1);
    }

    @Test
    void bannedNonMemberIsRefusedEvenOnAPublicWarp() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PUBLIC, WarpStatus.ACTIVE, WarpCost.free()));
        f.bans.banPermanently(VISITOR.uuid());

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.BANNED);
        assertThat(f.teleporter.hops).isZero();
    }

    @Test
    void anExpiredTimedBanReadsAsNotBanned() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PUBLIC, WarpStatus.ACTIVE, WarpCost.free()));
        f.bans.banUntil(VISITOR.uuid(), NOW.minusSeconds(1));

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME);

        assertThat(result.isOk()).isTrue();
        assertThat(f.teleporter.hops).isEqualTo(1);
    }

    // ---- Step 3: membership skips access and cost, but not safety. ----

    @Test
    void coOwnerReachesAPrivateWarpWithoutBeingWhitelistedOrOwning() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PRIVATE, WarpStatus.ACTIVE, WarpCost.free()));
        f.members.grant(VISITOR.uuid(), WarpRole.CO_OWNER);

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME);

        assertThat(result.isOk()).isTrue();
        assertThat(f.teleporter.hops).isEqualTo(1);
    }

    @Test
    void managerReachesAPricedPasswordWarpWithoutPasswordOrPaying() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PASSWORD, WarpStatus.ACTIVE, PRICE));
        f.passwords.set(RIGHT_PASSWORD);
        f.members.grant(VISITOR.uuid(), WarpRole.MANAGER);
        f.economyPort = Optional.of(f.economy);

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME, Optional.empty());

        assertThat(result.isOk()).isTrue();
        assertThat(f.teleporter.hops).isEqualTo(1);
        assertThat(f.economy.charges).as("a member never pays").isZero();
        assertThat(f.cooldowns.stamps)
                .as("a member never runs the password step")
                .isZero();
    }

    @Test
    void aMemberStillFacesTheSafetyCheck() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PRIVATE, WarpStatus.ACTIVE, WarpCost.free()));
        f.members.grant(VISITOR.uuid(), WarpRole.CO_OWNER);
        f.safety.safe = false;

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.UNSAFE_LOCATION);
        assertThat(f.teleporter.hops).isZero();
    }

    // ---- Step 4: the access branches, for non-members. ----

    @Test
    void aPublicWarpAdmitsANonMember() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PUBLIC, WarpStatus.ACTIVE, WarpCost.free()));

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME);

        assertThat(result.isOk()).isTrue();
        assertThat(f.teleporter.hops).isEqualTo(1);
    }

    @Test
    void aPrivateWarpRefusesANonMember() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PRIVATE, WarpStatus.ACTIVE, WarpCost.free()));

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NOT_PUBLIC);
        assertThat(f.teleporter.hops).isZero();
    }

    @Test
    void aWrongPasswordIsRefusedAndTheAttemptIsStamped() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PASSWORD, WarpStatus.ACTIVE, WarpCost.free()));
        f.passwords.set(RIGHT_PASSWORD);

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME, Optional.of(WRONG_PASSWORD));

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.WRONG_PASSWORD);
        assertThat(f.cooldowns.stamps).isEqualTo(1);
        assertThat(f.teleporter.hops).isZero();
    }

    @Test
    void anAbsentPasswordOnAPasswordWarpIsAWrongAttempt() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PASSWORD, WarpStatus.ACTIVE, WarpCost.free()));
        f.passwords.set(RIGHT_PASSWORD);

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME, Optional.empty());

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.WRONG_PASSWORD);
        assertThat(f.cooldowns.stamps).isEqualTo(1);
    }

    @Test
    void aCorrectPasswordProceedsWithoutStampingAnAttempt() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PASSWORD, WarpStatus.ACTIVE, WarpCost.free()));
        f.passwords.set(RIGHT_PASSWORD);

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME, Optional.of(RIGHT_PASSWORD));

        assertThat(result.isOk()).isTrue();
        assertThat(f.teleporter.hops).isEqualTo(1);
        assertThat(f.cooldowns.stamps).isZero();
    }

    @Test
    void aSecondWrongPasswordWithinTheCooldownIsRateLimited() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PASSWORD, WarpStatus.ACTIVE, WarpCost.free()));
        f.passwords.set(RIGHT_PASSWORD);
        UsePlayerWarp gate = f.gate();

        gate.useFor(VISITOR, NAME, Optional.of(WRONG_PASSWORD));
        Result<Unit, PlayerWarpError> second = gate.useFor(VISITOR, NAME, Optional.of(WRONG_PASSWORD));

        assertThat(second.errorOrThrow()).isEqualTo(PlayerWarpError.RATE_LIMITED);
        assertThat(f.teleporter.hops).isZero();
    }

    @Test
    void thePasswordBypassSkipsThePasswordStep() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PASSWORD, WarpStatus.ACTIVE, WarpCost.free()));
        f.passwords.set(RIGHT_PASSWORD);
        f.permissions.grant(VISITOR, "uxmessentials.pwarp.bypass.password");

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME, Optional.empty());

        assertThat(result.isOk()).isTrue();
        assertThat(f.teleporter.hops).isEqualTo(1);
        assertThat(f.cooldowns.stamps).isZero();
    }

    @Test
    void aWhitelistWarpRefusesAPlayerNotOnTheList() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.WHITELIST, WarpStatus.ACTIVE, WarpCost.free()));

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NOT_WHITELISTED);
        assertThat(f.teleporter.hops).isZero();
    }

    @Test
    void aWhitelistWarpAdmitsAPlayerOnTheList() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.WHITELIST, WarpStatus.ACTIVE, WarpCost.free()));
        f.whitelist.add(VISITOR.uuid());

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME);

        assertThat(result.isOk()).isTrue();
        assertThat(f.teleporter.hops).isEqualTo(1);
    }

    @Test
    void theWhitelistBypassSkipsTheWhitelistCheck() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.WHITELIST, WarpStatus.ACTIVE, WarpCost.free()));
        f.permissions.grant(VISITOR, "uxmessentials.pwarp.bypass.whitelist");

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME);

        assertThat(result.isOk()).isTrue();
        assertThat(f.teleporter.hops).isEqualTo(1);
    }

    // ---- Step 5: safe landing. ----

    @Test
    void anUnsafeLandingIsRefused() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PUBLIC, WarpStatus.ACTIVE, WarpCost.free()));
        f.safety.safe = false;

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.UNSAFE_LOCATION);
        assertThat(f.teleporter.hops).isZero();
    }

    @Test
    void theSafetyBypassSkipsTheSafetyCheck() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PUBLIC, WarpStatus.ACTIVE, WarpCost.free()));
        f.safety.safe = false;
        f.permissions.grant(VISITOR, "uxmessentials.pwarp.bypass.safety");

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME);

        assertThat(result.isOk()).isTrue();
        assertThat(f.teleporter.hops).isEqualTo(1);
    }

    // ---- Step 6: the guarded charge. ----

    @Test
    void aRefusedChargeLeavesNoVisitAndNoTeleport() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PUBLIC, WarpStatus.ACTIVE, PRICE));
        f.economy.result = Result.err(ChargeError.INSUFFICIENT_FUNDS);
        f.economyPort = Optional.of(f.economy);

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.CANNOT_AFFORD);
        assertThat(f.economy.charges).isEqualTo(1);
        assertThat(f.repository.visits).isZero();
        assertThat(f.teleporter.hops).isZero();
    }

    @Test
    void aSuccessfulChargeRecordsTheVisitAndTeleportsExactlyOnce() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PUBLIC, WarpStatus.ACTIVE, PRICE));
        f.economyPort = Optional.of(f.economy);

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME);

        assertThat(result.isOk()).isTrue();
        assertThat(f.economy.charges).isEqualTo(1);
        assertThat(f.repository.visits).isEqualTo(1);
        assertThat(f.teleporter.hops).isEqualTo(1);
    }

    @Test
    void theCostBypassSkipsTheChargeEntirely() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PUBLIC, WarpStatus.ACTIVE, PRICE));
        f.economyPort = Optional.of(f.economy);
        f.permissions.grant(VISITOR, "uxmessentials.pwarp.bypass.cost");

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME);

        assertThat(result.isOk()).isTrue();
        assertThat(f.economy.charges).as("a bypassing player is never debited").isZero();
        assertThat(f.teleporter.hops).isEqualTo(1);
    }

    @Test
    void anAbsentEconomyMakesAPricedWarpFree() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PUBLIC, WarpStatus.ACTIVE, PRICE));
        // economyPort stays empty: the soft-coupling precedent: no provider means no charge.

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME);

        assertThat(result.isOk()).isTrue();
        assertThat(f.teleporter.hops).isEqualTo(1);
    }

    @Test
    void anUnsafePricedWarpIsRefusedBeforeAnyCharge() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PUBLIC, WarpStatus.ACTIVE, PRICE));
        f.safety.safe = false;
        f.economyPort = Optional.of(f.economy);

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.UNSAFE_LOCATION);
        assertThat(f.economy.charges)
                .as("the charge runs only after safety passes")
                .isZero();
        assertThat(f.teleporter.hops).isZero();
    }

    // ---- Missing warp. ----

    @Test
    void aMissingWarpIsRejected() {
        Fixture f = new Fixture();

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, PlayerWarpName.of("ghost"));

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NOT_FOUND);
        assertThat(f.teleporter.hops).isZero();
    }

    // ---- The "never looser than T5" invariant. ----

    /**
     * The T5 stub admitted a non-owner only when the warp was PUBLIC. The new gate may only ever <em>widen</em>
     * that with the legitimate additions membership brought. A correct password, being whitelisted, or being a
     * co-owner/manager. With no bypass nodes granted, a fixed-active, unbanned, safe, free warp, and a non-owner
     * actor, any admission must trace to exactly one of those four widenings.
     */
    @Property
    void aNonOwnerIsAdmittedOnlyViaPublicPasswordWhitelistOrMembership(
            @ForAll WarpAccess access,
            @ForAll boolean member,
            @ForAll boolean onWhitelist,
            @ForAll boolean passwordCorrect) {
        Fixture f = new Fixture();
        f.put(warp(access, WarpStatus.ACTIVE, WarpCost.free()));
        f.passwords.set(RIGHT_PASSWORD);
        if (member) {
            f.members.grant(VISITOR.uuid(), WarpRole.MANAGER);
        }
        if (onWhitelist) {
            f.whitelist.add(VISITOR.uuid());
        }
        Optional<String> entered = Optional.of(passwordCorrect ? RIGHT_PASSWORD : WRONG_PASSWORD);

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME, entered);

        if (result.isOk()) {
            boolean legitimate = member
                    || access == WarpAccess.PUBLIC
                    || (access == WarpAccess.PASSWORD && passwordCorrect)
                    || (access == WarpAccess.WHITELIST && onWhitelist);
            assertThat(legitimate)
                    .as(
                            "admitted a non-owner via access=%s member=%s whitelist=%s pw=%s",
                            access, member, onWhitelist, passwordCorrect)
                    .isTrue();
        }
    }

    // ---- Step 7: post-gate cross-server routing. ----

    @Test
    void aRemoteWarpRoutesToTheCrossServerSeamInsteadOfTheLocalTeleporter() {
        Fixture f = new Fixture();
        f.put(remoteWarp(WarpCost.free()));
        f.crossServerPort = Optional.of(f.crossServer);

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME);

        assertThat(result.isOk()).isTrue();
        assertThat(f.teleporter.hops)
                .as("a remote warp never takes the local hop")
                .isZero();
        assertThat(f.crossServer.sends).isEqualTo(1);
        assertThat(f.crossServer.lastWarp).isEqualTo(NAME.value());
        assertThat(f.crossServer.lastCharged).as("a free warp charges nothing").isEmpty();
    }

    @Test
    void aRemotePricedWarpChargesThenHandsTheExactChargeToTheSeam() {
        Fixture f = new Fixture();
        f.put(remoteWarp(PRICE));
        f.economyPort = Optional.of(f.economy);
        f.crossServerPort = Optional.of(f.crossServer);

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME);

        assertThat(result.isOk()).isTrue();
        assertThat(f.economy.charges).isEqualTo(1);
        assertThat(f.teleporter.hops).isZero();
        assertThat(f.crossServer.lastCharged).contains(PRICE);
        assertThat(f.repository.visits)
                .as("the visit is recorded on the target backend, not on send")
                .isZero();
    }

    @Test
    void aLocalWarpStillUsesTheNormalTeleporter() {
        Fixture f = new Fixture();
        f.put(warp(WarpAccess.PUBLIC, WarpStatus.ACTIVE, WarpCost.free())); // serverId empty == local
        f.crossServerPort = Optional.of(f.crossServer);

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME);

        assertThat(result.isOk()).isTrue();
        assertThat(f.teleporter.hops).isEqualTo(1);
        assertThat(f.crossServer.sends)
                .as("a local warp never touches the cross-server seam")
                .isZero();
    }

    @Test
    void aRemoteWarpWithTheSubGroupOffIsRefusedWithARefundNotABrokenLocalHop() {
        Fixture f = new Fixture();
        f.put(remoteWarp(PRICE));
        f.economyPort = Optional.of(f.economy);
        // crossServerPort stays empty: the sub-group is off.

        Result<Unit, PlayerWarpError> result = f.gate().useFor(VISITOR, NAME);

        assertThat(result.isOk()).isTrue();
        assertThat(f.teleporter.hops)
                .as("never a broken local hop into another server")
                .isZero();
        assertThat(f.economy.charges).isEqualTo(1);
        assertThat(f.economy.refunds)
                .as("the charge is returned since the player never moved")
                .isEqualTo(1);
        assertThat(f.economy.lastRefundAmount).isEqualByComparingTo(PRICE.amount());
    }

    // ---- Fixture and fakes. ----

    private static PlayerWarp warp(WarpAccess access, WarpStatus status, WarpCost price) {
        return warp(access, status, price, Optional.empty());
    }

    /** A PUBLIC, ACTIVE warp tagged for another backend, so the gate routes it across the proxy. */
    private static PlayerWarp remoteWarp(WarpCost price) {
        return warp(WarpAccess.PUBLIC, WarpStatus.ACTIVE, price, Optional.of("other-server"));
    }

    private static PlayerWarp warp(WarpAccess access, WarpStatus status, WarpCost price, Optional<String> serverId) {
        return new PlayerWarp(
                Optional.of(PlayerWarpId.of(1L)),
                OWNER,
                OWNER.name(),
                NAME,
                Optional.empty(),
                SAFE_SPOT,
                serverId,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                access,
                access == WarpAccess.PASSWORD,
                status,
                price,
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

    /** A fresh, independently-configurable wiring of the gate over recording/settable fakes. */
    private static final class Fixture {
        final FakeRepository repository = new FakeRepository();
        final RecordingTeleporter teleporter = new RecordingTeleporter();
        final FakeBanStore bans = new FakeBanStore();
        final FakeMemberStore members = new FakeMemberStore();
        final FakeWhitelistStore whitelist = new FakeWhitelistStore();
        final FakePasswordStore passwords = new FakePasswordStore();
        final FakeCooldowns cooldowns = new FakeCooldowns();
        final FakePermissions permissions = new FakePermissions();
        final MutableSafety safety = new MutableSafety();
        final FakeEconomy economy = new FakeEconomy();
        final RecordingCrossServer crossServer = new RecordingCrossServer();
        Optional<PlayerWarpEconomy> economyPort = Optional.empty();
        Optional<CrossServerTeleport> crossServerPort = Optional.empty();

        void put(PlayerWarp warp) {
            repository.put(warp);
        }

        UsePlayerWarp gate() {
            return new UsePlayerWarp(
                    repository,
                    teleporter,
                    new Notifier(new KeyMessages(), new NoopSink()),
                    safety,
                    permissions,
                    bans,
                    members,
                    whitelist,
                    passwords,
                    cooldowns,
                    economyPort,
                    "local",
                    crossServerPort,
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }

    /** A cross-server seam that records the warp name and the exact charge each remote route was handed. */
    private static final class RecordingCrossServer implements CrossServerTeleport {
        int sends;

        @org.jspecify.annotations.Nullable String lastWarp;

        Optional<WarpCost> lastCharged = Optional.empty();

        @Override
        public void send(PlayerRef who, PlayerWarp warp, Optional<WarpCost> charged) {
            sends++;
            lastWarp = warp.name().value();
            lastCharged = charged;
        }
    }

    private static final class FakeRepository implements PlayerWarpRepository {
        private final Map<PlayerWarpName, PlayerWarp> byName = new LinkedHashMap<>();
        int visits;

        void put(PlayerWarp warp) {
            byName.put(warp.name(), warp);
        }

        @Override
        public Optional<PlayerWarp> findByName(PlayerWarpName name) {
            return Optional.ofNullable(byName.get(name));
        }

        @Override
        public Optional<PlayerWarp> findById(PlayerWarpId id) {
            return byName.values().stream()
                    .filter(warp -> warp.id().equals(Optional.of(id)))
                    .findFirst();
        }

        @Override
        public List<PlayerWarp> ownedBy(PlayerRef owner) {
            return List.copyOf(byName.values());
        }

        @Override
        public List<PlayerWarp> publicOwnedBy(PlayerRef owner) {
            return List.of();
        }

        @Override
        public int count(PlayerRef owner) {
            return byName.size();
        }

        @Override
        public boolean existsByName(PlayerWarpName name) {
            return byName.containsKey(name);
        }

        @Override
        public PlayerWarpId save(PlayerWarp warp) {
            return warp.id().orElseThrow();
        }

        @Override
        public void deleteById(PlayerWarpId id) {
            byName.values().removeIf(warp -> warp.id().equals(Optional.of(id)));
        }

        @Override
        public void recordVisit(PlayerWarpId id) {
            visits++;
        }

        @Override
        public void updateRating(PlayerWarpId id, RatingSummary summary) {
            // The gate tests exercise access and visits, not the rating rollup, so this fake ignores it.
        }

        @Override
        public void refreshFavouriteCount(PlayerWarpId id) {
            // The gate tests exercise access and visits, not favourite counts, so this fake ignores it.
        }
    }

    private static final class RecordingTeleporter implements PlayerWarpTeleporter {
        int hops;

        @Override
        public void teleportTo(PlayerRef who, PlayerWarp warp) {
            hops++;
        }
    }

    private static final class FakeBanStore implements WarpBanStore {
        private final Map<UUID, BanRecord> byPlayer = new HashMap<>();

        void banPermanently(UUID player) {
            byPlayer.put(player, new BanRecord(player, Optional.empty(), Optional.empty(), Optional.empty(), NOW));
        }

        void banUntil(UUID player, Instant until) {
            byPlayer.put(player, new BanRecord(player, Optional.of(until), Optional.empty(), Optional.empty(), NOW));
        }

        @Override
        public void ban(PlayerWarpId warp, BanRecord record) {
            byPlayer.put(record.player(), record);
        }

        @Override
        public void unban(PlayerWarpId warp, UUID player) {
            byPlayer.remove(player);
        }

        @Override
        public Optional<BanRecord> find(PlayerWarpId warp, UUID player) {
            return Optional.ofNullable(byPlayer.get(player));
        }

        @Override
        public List<BanRecord> list(PlayerWarpId warp) {
            return List.copyOf(byPlayer.values());
        }
    }

    private static final class FakeMemberStore implements WarpMemberStore {
        private final Map<UUID, WarpRole> roles = new HashMap<>();

        void grant(UUID player, WarpRole role) {
            roles.put(player, role);
        }

        @Override
        public void put(PlayerWarpId warp, WarpMember member) {
            roles.put(member.player(), member.role());
        }

        @Override
        public void remove(PlayerWarpId warp, UUID player) {
            roles.remove(player);
        }

        @Override
        public Optional<WarpRole> roleOf(PlayerWarpId warp, UUID player) {
            return Optional.ofNullable(roles.get(player));
        }

        @Override
        public List<WarpMember> list(PlayerWarpId warp) {
            List<WarpMember> members = new ArrayList<>();
            roles.forEach((player, role) -> members.add(new WarpMember(player, role, NOW)));
            return members;
        }
    }

    private static final class FakeWhitelistStore implements WarpWhitelistStore {
        private final Set<UUID> members = new HashSet<>();

        @Override
        public void add(PlayerWarpId warp, UUID player) {
            members.add(player);
        }

        void add(UUID player) {
            members.add(player);
        }

        @Override
        public void remove(PlayerWarpId warp, UUID player) {
            members.remove(player);
        }

        @Override
        public boolean contains(PlayerWarpId warp, UUID player) {
            return members.contains(player);
        }

        @Override
        public List<UUID> list(PlayerWarpId warp) {
            return List.copyOf(members);
        }
    }

    private static final class FakePasswordStore implements PlayerWarpPasswordStore {
        private @org.jspecify.annotations.Nullable String password;

        void set(String plaintext) {
            this.password = plaintext;
        }

        @Override
        public void set(PlayerWarpId warp, String plaintext) {
            this.password = plaintext;
        }

        @Override
        public void clear(PlayerWarpId warp) {
            this.password = null;
        }

        @Override
        public boolean matches(PlayerWarpId warp, String plaintext) {
            return password != null && password.equals(plaintext);
        }
    }

    /**
     * A cooldown fake modelling the per-warp attempt limiter: a stamped label is immediately active, so a stamp
     * followed by another check reads as rate-limited: exactly the "second wrong attempt is throttled" behaviour.
     */
    private static final class FakeCooldowns implements Cooldowns {
        private final Set<String> active = new HashSet<>();
        int stamps;

        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {
            // tiered cooldowns are not exercised by the gate
        }

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return active.contains(label) ? Result.err(Duration.ofSeconds(30)) : Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {
            stamps++;
            active.add(label);
        }
    }

    private static final class FakePermissions implements Permissions {
        private final Map<UUID, Set<String>> grants = new HashMap<>();

        void grant(PlayerRef who, String node) {
            grants.computeIfAbsent(who.uuid(), key -> new HashSet<>()).add(node);
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return grants.getOrDefault(who.uuid(), Set.of()).contains(node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @org.jspecify.annotations.Nullable WorldRef world, long fallback) {
            return QuotaResult.limited(fallback);
        }
    }

    private static final class MutableSafety implements WarpSafetyChecker {
        boolean safe = true;

        @Override
        public boolean isSafe(Position position) {
            return safe;
        }
    }

    private static final class FakeEconomy implements PlayerWarpEconomy {
        Result<Unit, ChargeError> result = Result.ok();
        int charges;
        int refunds;

        @org.jspecify.annotations.Nullable BigDecimal lastRefundAmount;

        @Override
        public Result<Unit, ChargeError> chargeAndAccrue(
                PlayerRef payer, PlayerWarpId warp, BigDecimal price, String currencyId) {
            charges++;
            return result;
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
            refunds++;
            lastRefundAmount = amount;
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

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // feedback delivery is not under test here
        }
    }
}
