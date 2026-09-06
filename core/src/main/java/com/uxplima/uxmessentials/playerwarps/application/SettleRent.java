package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.ChargeError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.RentDecision;
import com.uxplima.uxmessentials.playerwarps.domain.RentState;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * Settles one warp's rent for the current pass: the {@link RentPolicy} decides what to do, this use case moves the
 * money through {@link PlayerWarpEconomy#collectRent} and writes the resulting lifecycle transition. It is the one
 * place the suspend-then-archive rule lives, and it never hard-deletes. A warp only ever moves
 * ACTIVE→SUSPENDED→ARCHIVED, every step recoverable.
 *
 * <ul>
 *   <li>{@link RentDecision#DUE}/{@link RentDecision#RETRY}: collect the rent (bank-first, then owner wallet). On
 *       success the term advances, the warp is (re)activated, the suspend/archive marks are cleared, and the
 *       reminder counter resets to 0 so the next term starts fresh. On failure a due warp is suspended with a grace
 *       deadline; a failing retry is left suspended to be archived when that deadline lapses.
 *   <li>{@link RentDecision#ARCHIVE}: the grace window lapsed, archive the warp (recoverable via admin restore).
 *   <li>{@link RentDecision#NONE}: nothing to do.
 * </ul>
 *
 * <p>Exempt warps (owner / category / world in config) and warps not enrolled in rent (no {@link RentState}) are
 * skipped entirely: never charged, never suspended.
 */
@NullMarked
public final class SettleRent {

    private final PlayerWarpRepository repository;
    private final PlayerWarpEconomy economy;
    private final RentPolicy policy;
    private final RentConfig config;
    private final Clock clock;

    public SettleRent(
            PlayerWarpRepository repository,
            PlayerWarpEconomy economy,
            RentPolicy policy,
            RentConfig config,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Settle {@code warp}'s rent for this pass and report what happened. */
    public RentOutcome settle(PlayerWarp warp) {
        Objects.requireNonNull(warp, "warp");
        if (!config.enabled() || config.isExempt(warp)) {
            return RentOutcome.UNCHANGED;
        }
        Optional<RentState> maybeRent = warp.rent();
        if (maybeRent.isEmpty()) {
            return RentOutcome.UNCHANGED;
        }
        RentState rent = maybeRent.get();
        Instant now = clock.instant();
        return switch (policy.decide(warp.status(), rent, now, config)) {
            case DUE -> charge(warp, rent, now, true);
            case RETRY -> charge(warp, rent, now, false);
            case ARCHIVE -> archive(warp, now);
            case NONE -> RentOutcome.UNCHANGED;
        };
    }

    private RentOutcome charge(PlayerWarp warp, RentState rent, Instant now, boolean due) {
        PlayerWarpId id = warp.id().orElseThrow(() -> new IllegalStateException("rent settle needs a saved warp id"));
        Result<Unit, ChargeError> result = economy.collectRent(id, warp.owner(), config.amount(), config.currencyId());
        if (result.isOk()) {
            return renew(warp, rent, now, due);
        }
        // A due warp that cannot pay is suspended now; a still-failing retry is left suspended until its grace
        // deadline lapses and the archive pass retires it: either way no money moved and nothing is deleted.
        return due ? suspend(warp, rent, now) : RentOutcome.UNCHANGED;
    }

    private RentOutcome renew(PlayerWarp warp, RentState rent, Instant now, boolean due) {
        Instant base = rent.paidUntil().isAfter(now) ? rent.paidUntil() : now;
        RentState renewed = new RentState(base.plus(config.period()), Optional.empty(), Optional.empty());
        PlayerWarp saved = warp.withStatus(WarpStatus.ACTIVE, now).withRent(renewed, now);
        repository.save(saved);
        saved.id().ifPresent(id -> repository.markRentReminded(id, 0));
        return due ? RentOutcome.RENEWED : RentOutcome.RESTORED;
    }

    private RentOutcome suspend(PlayerWarp warp, RentState rent, Instant now) {
        RentState suspended = new RentState(rent.paidUntil(), Optional.of(now), Optional.of(now.plus(config.grace())));
        repository.save(warp.withStatus(WarpStatus.SUSPENDED, now).withRent(suspended, now));
        return RentOutcome.SUSPENDED;
    }

    private RentOutcome archive(PlayerWarp warp, Instant now) {
        repository.save(warp.withStatus(WarpStatus.ARCHIVED, now));
        return RentOutcome.ARCHIVED;
    }
}
