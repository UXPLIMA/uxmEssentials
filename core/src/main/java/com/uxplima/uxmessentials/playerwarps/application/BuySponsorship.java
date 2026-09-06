package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.Sponsorship;
import com.uxplima.uxmessentials.playerwarps.domain.WarpCapability;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /pwarp sponsor <name> [days]}: buy a paid, time-limited pinned browse slot for the warp. Owner-only by the
 * capability matrix. {@link WarpCapability#SPONSOR} is denied to co-owners and managers, so a delegate can never
 * spend the owner's money on placement. The buy runs a fixed sequence of guards before the single guarded debit:
 *
 * <ol>
 *   <li>resolve the warp ({@link PlayerWarpError#NOT_FOUND} when absent);
 *   <li>gate {@code SPONSOR} ({@link PlayerWarpError#NO_PERMISSION} for anyone but the owner);
 *   <li>clamp the requested term into the configured {@code [1, duration-days]} range;
 *   <li>refuse {@link PlayerWarpError#SPONSOR_COOLDOWN} while the warp is inside its post-expiry cooldown;
 *   <li>refuse {@link PlayerWarpError#SPONSOR_LIMIT} once the owner holds the configured concurrent maximum;
 *   <li>pick the lowest free slot, refusing {@link PlayerWarpError#SPONSOR_FULL} when every slot is taken;
 *   <li>charge the owner ({@link PlayerWarpError#CANNOT_AFFORD} when the debit cannot take).
 * </ol>
 *
 * <p>Only once the debit succeeds is the sponsorship written. {@code sponsored_until = now + days}, the chosen
 * slot, so a failed charge never marks a warp sponsored. The charge is the DB-guarded, double-spend-safe point:
 * there is no affordability probe before it (that would open a double-spend window), the debit itself decides.
 */
@NullMarked
public final class BuySponsorship {

    private final PlayerWarpRepository repository;
    private final WarpAuthorization authorization;
    private final Optional<PlayerWarpEconomy> economy;
    private final Notifier notifier;
    private final SponsorConfig config;
    private final Clock clock;

    public BuySponsorship(
            PlayerWarpRepository repository,
            WarpAuthorization authorization,
            Optional<PlayerWarpEconomy> economy,
            Notifier notifier,
            SponsorConfig config,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Buy {@code days} of sponsorship for {@code actor}'s warp {@code name}, or reject with the modelled reason. */
    public Result<Unit, PlayerWarpError> buy(PlayerRef actor, PlayerWarpName name, int days) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<PlayerWarp> found = repository.findByName(name);
        if (found.isEmpty()) {
            return refuse(actor, name, PlayerWarpError.NOT_FOUND);
        }
        PlayerWarp warp = found.get();
        if (!authorization.allows(warp, actor.uuid(), WarpCapability.SPONSOR)) {
            return refuse(actor, name, PlayerWarpError.NO_PERMISSION);
        }
        PlayerWarpId id = warp.id().orElseThrow(() -> new IllegalStateException("sponsor needs a saved warp id"));
        Instant now = clock.instant();
        if (repository.sponsorCooldownUntil(id).map(now::isBefore).orElse(false)) {
            return refuse(actor, name, PlayerWarpError.SPONSOR_COOLDOWN);
        }
        if (repository.activeSponsorCount(actor, now) >= config.maxConcurrentPerPlayer()) {
            return refuse(actor, name, PlayerWarpError.SPONSOR_LIMIT);
        }
        OptionalInt slot = freeSlot(repository.activeSponsorSlots(now));
        if (slot.isEmpty()) {
            return refuse(actor, name, PlayerWarpError.SPONSOR_FULL);
        }
        return commit(actor, warp, name, slot.getAsInt(), days, now);
    }

    /** The guarded charge, then, only on success, the sponsorship write and the confirmation notice. */
    private Result<Unit, PlayerWarpError> commit(
            PlayerRef actor, PlayerWarp warp, PlayerWarpName name, int slot, int days, Instant now) {
        // The debit is the double-spend-safe point: no affordability probe precedes it, the debit itself decides. With
        // no economy provider present there is nothing to charge against, so the purchase cannot take, a paid feature
        // cannot run for free, and it refuses exactly as an unaffordable charge would.
        boolean charged = economy.map(e -> e.chargeOwner(actor, config.price(), config.currencyId())
                        .isOk())
                .orElse(false);
        if (!charged) {
            return refuse(actor, name, PlayerWarpError.CANNOT_AFFORD);
        }
        int term = Math.max(1, Math.min(days, config.durationDays()));
        Instant until = now.plus(Duration.ofDays(term));
        repository.save(warp.withSponsorship(Optional.of(new Sponsorship(until, slot)), now));
        notifier.send(
                actor,
                PlayerwarpsMessageKey.PWARP_SPONSORED,
                Map.of("warp", name.value(), "days", Integer.toString(term)));
        return Result.ok();
    }

    /** The lowest slot in {@code [0, slots)} not already taken by a live sponsorship, or empty when every slot is used. */
    private OptionalInt freeSlot(Set<Integer> occupied) {
        for (int slot = 0; slot < config.slots(); slot++) {
            if (!occupied.contains(slot)) {
                return OptionalInt.of(slot);
            }
        }
        return OptionalInt.empty();
    }

    private Result<Unit, PlayerWarpError> refuse(PlayerRef actor, PlayerWarpName name, PlayerWarpError error) {
        notifier.send(actor, error.messageKey(), Map.of("warp", name.value()));
        return Result.err(error);
    }
}
