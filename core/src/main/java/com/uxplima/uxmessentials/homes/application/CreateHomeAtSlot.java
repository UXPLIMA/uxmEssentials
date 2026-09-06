package com.uxplima.uxmessentials.homes.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.port.HomeInviteRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.SethomeGuard;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeLimit;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.homes.domain.event.HomeCreating;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.DomainGate;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /sethome}: create a home in a given slot at the player's current position. The owner's limit is
 * resolved through {@link HomeQuota} scoped to the home's world and folded into a maximum-slot count, then
 * every registered {@link SethomeGuard} runs in order. The first failure short-circuits with its
 * {@link HomeError} and no aggregate change. If the guards pass, the aggregate gates the slot against the
 * range/occupancy/limit invariants and creates the home; if the transition succeeds, the optional economy
 * charge is applied before the result is committed. Hitting the cap publishes {@code HomeLimitReached} and
 * returns {@link HomeError#LIMIT_REACHED} so the command renders the limit message, never an inline literal.
 */
public final class CreateHomeAtSlot {

    private final HomeRepository repository;
    private final HomeInviteRepository invites;
    private final HomeQuota quota;
    private final List<SethomeGuard> guards;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final DomainGate gate;
    private final HomeCharge charge;
    private final int unlimitedMaxSlots;
    private final Clock clock;

    public CreateHomeAtSlot(
            HomeRepository repository,
            HomeInviteRepository invites,
            HomeQuota quota,
            List<SethomeGuard> guards,
            Notifier notifier,
            DomainEventPublisher events,
            DomainGate gate,
            HomeCharge charge,
            int unlimitedMaxSlots,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.invites = Objects.requireNonNull(invites, "invites");
        this.quota = Objects.requireNonNull(quota, "quota");
        this.guards = List.copyOf(Objects.requireNonNull(guards, "guards"));
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.charge = Objects.requireNonNull(charge, "charge");
        if (unlimitedMaxSlots < 0) {
            throw new IllegalArgumentException("unlimitedMaxSlots must not be negative: " + unlimitedMaxSlots);
        }
        this.unlimitedMaxSlots = unlimitedMaxSlots;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Create {@code owner}'s home in {@code slot} at {@code at}, gating on the guards, the aggregate, and the economy charge. */
    public Result<Unit, HomeError> create(PlayerRef owner, HomeSlot slot, Position at) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(at, "at");
        HomeSet set = repository.load(owner);
        HomeLimit limit = quota.resolve(owner, at.world());
        int max = limit.maxSlots(unlimitedMaxSlots);
        Result<Unit, HomeError> guarded = runGuards(owner, slot, at);
        if (guarded.isErr()) {
            return guarded;
        }
        // Validate via the aggregate first: free checks before the paid charge gate.
        Result<HomeSet.Change, HomeError> outcome = set.createAt(slot, at, limit, max, clock.instant());
        if (outcome.isErr()) {
            return reject(set, limit, outcome.errorOrThrow());
        }
        // Everything uxmEssentials itself would refuse has now passed, so this is the point to ask whether anybody
        // outside the plugin refuses it. Before the charge, so a vetoed create never takes the player's money.
        if (!gate.allows(new HomeCreating(owner, slot, at))) {
            notifier.send(owner, HomeError.VETOED.messageKey(), placeholders(slot));
            return Result.err(HomeError.VETOED);
        }
        // Aggregate transition succeeded; apply the economy charge before committing to storage.
        Result<Unit, HomeError> charged = charge.charge(owner, HomeChargeKind.CREATE);
        if (charged.isErr()) {
            notifier.send(owner, HomeError.CANNOT_AFFORD.messageKey());
            return Result.err(HomeError.CANNOT_AFFORD);
        }
        return commit(outcome.orElseThrow());
    }

    private Result<Unit, HomeError> runGuards(PlayerRef owner, HomeSlot slot, Position at) {
        for (SethomeGuard guard : guards) {
            Result<Unit, HomeError> verdict = guard.check(owner, slot, at);
            if (verdict.isErr()) {
                HomeError error = verdict.errorOrThrow();
                notifier.send(owner, error.messageKey(), placeholders(slot));
                return Result.err(error);
            }
        }
        return Result.ok();
    }

    private Result<Unit, HomeError> commit(HomeSet.Change change) {
        repository.save(change.home());
        // Clear any orphan invite rows that a prior crash may have left for this slot; a freshly created
        // home always starts with an empty guest list regardless of DB history.
        invites.removeAll(change.home().owner(), change.home().slot());
        change.event().ifPresent(events::publish);
        notifier.send(
                change.home().owner(),
                HomesMessageKey.HOME_CREATED,
                placeholders(change.home().slot()));
        return Result.ok();
    }

    private Result<Unit, HomeError> reject(HomeSet set, HomeLimit limit, HomeError error) {
        if (error == HomeError.LIMIT_REACHED) {
            events.publish(set.limitReached(limit));
            notifier.send(set.owner(), error.messageKey(), Map.of("limit", Integer.toString(limit.cap())));
        } else {
            notifier.send(set.owner(), error.messageKey());
        }
        return Result.err(error);
    }

    private static Map<String, String> placeholders(HomeSlot slot) {
        return Map.of("slot", Integer.toString(slot.displayNumber()));
    }
}
