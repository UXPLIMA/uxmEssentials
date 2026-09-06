package com.uxplima.uxmessentials.homes.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeIcon;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * Set or clear the GUI icon of the home in a slot, keeping its location, label, and creation time. The
 * icon is cosmetic, the slot remains the home's identity, so an empty {@code icon} clears it. The
 * aggregate rejects an empty slot with {@link HomeError#NOT_FOUND}; a successful change saves the row,
 * publishes {@code HomeIconChanged}, and notifies {@link HomesMessageKey#HOME_ICON_CHANGED}.
 */
public final class SetHomeIcon {

    private final HomeRepository repository;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public SetHomeIcon(HomeRepository repository, Notifier notifier, DomainEventPublisher events, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Set {@code owner}'s slot icon to {@code icon} (empty clears it), or reject an empty slot. */
    public Result<Unit, HomeError> setIcon(PlayerRef owner, HomeSlot slot, Optional<HomeIcon> icon) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(icon, "icon");
        HomeSet set = repository.load(owner);
        Result<HomeSet.Change, HomeError> outcome = set.setIcon(slot, icon, clock.instant());
        if (outcome.isErr()) {
            HomeError error = outcome.errorOrThrow();
            notifier.send(owner, error.messageKey(), slotPlaceholder(slot));
            return Result.err(error);
        }
        HomeSet.Change change = outcome.orElseThrow();
        repository.save(change.home());
        change.event().ifPresent(events::publish);
        notifier.send(owner, HomesMessageKey.HOME_ICON_CHANGED, slotPlaceholder(slot));
        return Result.ok();
    }

    private static Map<String, String> slotPlaceholder(HomeSlot slot) {
        return Map.of("slot", Integer.toString(slot.displayNumber()));
    }
}
