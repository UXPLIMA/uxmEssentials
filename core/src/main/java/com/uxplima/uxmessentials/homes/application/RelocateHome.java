package com.uxplima.uxmessentials.homes.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.SethomeGuard;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.homes.domain.event.HomeRelocating;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.DomainGate;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * Re-anchor the home in a slot to the player's current position, keeping its label, icon, and creation
 * time. Every registered {@link SethomeGuard} runs first. The first failure short-circuits with its
 * {@link HomeError} without touching the aggregate. The aggregate then rejects an empty slot with
 * {@link HomeError#NOT_FOUND}; once the aggregate transition succeeds, the optional economy charge is
 * applied before committing. A successful relocate saves the row, publishes {@code HomeRelocated}, and
 * notifies {@link HomesMessageKey#HOME_RELOCATED}.
 */
public final class RelocateHome {

    private final HomeRepository repository;
    private final List<SethomeGuard> guards;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final DomainGate gate;
    private final HomeCharge charge;
    private final Clock clock;

    public RelocateHome(
            HomeRepository repository,
            List<SethomeGuard> guards,
            Notifier notifier,
            DomainEventPublisher events,
            DomainGate gate,
            HomeCharge charge,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.guards = List.copyOf(Objects.requireNonNull(guards, "guards"));
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.charge = Objects.requireNonNull(charge, "charge");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Relocate {@code owner}'s home in {@code slot} to {@code at}, or reject on a guard, empty slot, or insufficient balance. */
    public Result<Unit, HomeError> relocate(PlayerRef owner, HomeSlot slot, Position at) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(at, "at");
        for (SethomeGuard guard : guards) {
            Result<Unit, HomeError> verdict = guard.check(owner, slot, at);
            if (verdict.isErr()) {
                HomeError error = verdict.errorOrThrow();
                notifier.send(owner, error.messageKey(), slotPlaceholder(slot));
                return Result.err(error);
            }
        }
        HomeSet set = repository.load(owner);
        Result<HomeSet.Change, HomeError> outcome = set.relocate(slot, at, clock.instant());
        if (outcome.isErr()) {
            HomeError error = outcome.errorOrThrow();
            notifier.send(owner, error.messageKey(), slotPlaceholder(slot));
            return Result.err(error);
        }
        // Asked once our own rules have passed and before the charge, so a refused move costs the player nothing.
        if (!gate.allows(new HomeRelocating(owner, slot, at))) {
            notifier.send(owner, HomeError.VETOED.messageKey(), slotPlaceholder(slot));
            return Result.err(HomeError.VETOED);
        }
        // Aggregate transition succeeded; apply the economy charge before committing to storage.
        Result<Unit, HomeError> charged = charge.charge(owner, HomeChargeKind.RELOCATE);
        if (charged.isErr()) {
            notifier.send(owner, HomeError.CANNOT_AFFORD.messageKey());
            return Result.err(HomeError.CANNOT_AFFORD);
        }
        HomeSet.Change change = outcome.orElseThrow();
        repository.save(change.home());
        change.event().ifPresent(events::publish);
        notifier.send(owner, HomesMessageKey.HOME_RELOCATED, slotPlaceholder(slot));
        return Result.ok();
    }

    private static Map<String, String> slotPlaceholder(HomeSlot slot) {
        return Map.of("slot", Integer.toString(slot.displayNumber()));
    }
}
