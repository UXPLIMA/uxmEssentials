package com.uxplima.uxmessentials.homes.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * GUI visibility toggle from the home action view: flip one of the owner's homes between public (any player
 * may visit it) and private (only the owner and invited players may). There is no standalone command for this
 *: the toggle is exposed exclusively through the home action GUI. A slot the owner has no home in is
 * rejected with {@link HomeError#NOT_FOUND}; a successful flip saves the home with its new visibility,
 * publishes {@code HomeVisibilityChanged}, and renders the matching feedback. An owner only ever toggles
 * their own homes.
 */
public final class SetHomeVisibility {

    private final HomeRepository repository;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public SetHomeVisibility(HomeRepository repository, Notifier notifier, DomainEventPublisher events, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Flip {@code owner}'s home in {@code slot} to {@code makePublic}, or reject when the slot is empty. */
    public Result<Unit, HomeError> setVisibility(PlayerRef owner, HomeSlot slot, boolean makePublic) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        HomeSet set = repository.load(owner);
        Result<HomeSet.Change, HomeError> outcome = set.setVisibility(slot, makePublic, clock.instant());
        if (outcome.isErr()) {
            HomeError error = outcome.errorOrThrow();
            notifier.send(owner, error.messageKey(), slotPlaceholder(slot));
            return Result.err(error);
        }
        HomeSet.Change change = outcome.orElseThrow();
        repository.save(change.home());
        change.event().ifPresent(events::publish);
        notifier.send(
                owner,
                makePublic ? HomesMessageKey.HOME_VISIBILITY_PUBLIC : HomesMessageKey.HOME_VISIBILITY_PRIVATE,
                slotPlaceholder(slot));
        return Result.ok();
    }

    private static Map<String, String> slotPlaceholder(HomeSlot slot) {
        return Map.of("slot", Integer.toString(slot.displayNumber()));
    }
}
