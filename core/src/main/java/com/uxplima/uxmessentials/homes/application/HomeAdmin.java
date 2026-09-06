package com.uxplima.uxmessentials.homes.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.homes.application.port.HomeInviteRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeLimit;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /homeadmin <player> [del|list|tp|set|clearall|info] [slot]}: full admin management over
 * another player's homes as an explicit verb, audit-logged by the adapter. It reuses the same
 * aggregate transitions the player-facing use cases do. There is no second code path for an admin
 * delete, so the slot/limit invariants hold identically. The acting staff member is the
 * {@code actor} who receives feedback; the {@code target} is the owner whose set is read or changed.
 */
public final class HomeAdmin {

    /** Upper bound on the slot index for admin-forced homes; well beyond any player quota. */
    private static final int MAX_ADMIN_SLOTS = 1000;

    private final HomeRepository repository;
    private final HomeInviteRepository invites;
    private final HomeTeleporter teleporter;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public HomeAdmin(
            HomeRepository repository,
            HomeInviteRepository invites,
            HomeTeleporter teleporter,
            Notifier notifier,
            DomainEventPublisher events,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.invites = Objects.requireNonNull(invites, "invites");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** List {@code target}'s homes to {@code actor}, returning them for the adapter to render. */
    public List<Home> list(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        List<Home> homes = repository.load(target).all();
        notifier.send(
                actor,
                HomesMessageKey.HOME_ADMIN_LIST_HEADER,
                Map.of("player", target.name(), "count", Integer.toString(homes.size())));
        return homes;
    }

    /** Delete {@code target}'s home in {@code slot}, reporting the result to {@code actor}. */
    public Result<Unit, HomeError> delete(PlayerRef actor, PlayerRef target, HomeSlot slot) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(slot, "slot");
        HomeSet set = repository.load(target);
        Result<HomeSet.Change, HomeError> outcome = set.delete(slot);
        if (outcome.isErr()) {
            notifier.send(actor, outcome.errorOrThrow().messageKey(), slotPlaceholder(slot));
            return Result.err(outcome.errorOrThrow());
        }
        repository.deleteSlot(target, slot);
        invites.removeAll(target, slot);
        outcome.orElseThrow().event().ifPresent(events::publish);
        notifier.send(actor, HomesMessageKey.HOME_ADMIN_DELETED, attribution(target, slot));
        return Result.ok();
    }

    /** Teleport {@code actor} to {@code target}'s home in {@code slot}, delegating the hop to teleport. */
    public Result<Unit, HomeError> teleport(PlayerRef actor, PlayerRef target, HomeSlot slot) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(slot, "slot");
        Optional<Home> home = repository.findSlot(target, slot);
        if (home.isEmpty()) {
            notifier.send(actor, HomeError.NOT_FOUND.messageKey(), slotPlaceholder(slot));
            return Result.err(HomeError.NOT_FOUND);
        }
        teleporter.teleportTo(actor, home.get());
        return Result.ok();
    }

    /**
     * Staff override, place or re-anchor {@code target}'s home in {@code slot} at {@code at},
     * bypassing quota and economy. If the slot is already occupied it is re-anchored in place
     * (keeping the label and icon); otherwise the home is created with {@link HomeLimit#noLimit()}
     * so the count invariant is never a barrier. Slots ≥ {@value MAX_ADMIN_SLOTS} are still
     * rejected by the aggregate's range guard.
     */
    public Result<Unit, HomeError> setHome(PlayerRef actor, PlayerRef target, HomeSlot slot, Position at) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(at, "at");
        HomeSet set = repository.load(target);
        Result<HomeSet.Change, HomeError> outcome;
        if (set.find(slot).isPresent()) {
            // Slot occupied: re-anchor, keeping label and icon.
            outcome = set.relocate(slot, at, clock.instant());
        } else {
            // Slot free: create, bypassing the owner's quota.
            outcome = set.createAt(slot, at, HomeLimit.noLimit(), MAX_ADMIN_SLOTS, clock.instant());
        }
        if (outcome.isErr()) {
            notifier.send(actor, outcome.errorOrThrow().messageKey(), slotPlaceholder(slot));
            return Result.err(outcome.errorOrThrow());
        }
        HomeSet.Change change = outcome.orElseThrow();
        repository.save(change.home());
        change.event().ifPresent(events::publish);
        notifier.send(actor, HomesMessageKey.HOME_ADMIN_SET, attribution(target, slot));
        return Result.ok();
    }

    /**
     * Remove every home the target holds, reporting the count cleared to {@code actor}. Returns
     * ok unconditionally: there is nothing to fail after the count is read.
     */
    public Result<Unit, HomeError> clearAll(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        int n = repository.load(target).count();
        repository.deleteAll(target);
        invites.removeAllForOwner(target);
        notifier.send(
                actor,
                HomesMessageKey.HOME_ADMIN_CLEARED,
                Map.of("player", target.name(), "count", Integer.toString(n)));
        return Result.ok();
    }

    /**
     * Look up a single home in {@code target}'s set and notify {@code actor} with its details.
     * Returns the home so the adapter can render timestamps; empty when no home exists in the slot.
     */
    public Optional<Home> info(PlayerRef actor, PlayerRef target, HomeSlot slot) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(slot, "slot");
        Optional<Home> found = repository.findSlot(target, slot);
        if (found.isEmpty()) {
            notifier.send(actor, HomesMessageKey.HOME_NOT_FOUND, slotPlaceholder(slot));
            return Optional.empty();
        }
        Home home = found.get();
        notifier.send(actor, HomesMessageKey.HOME_ADMIN_INFO, infoPlaceholders(target, home));
        return Optional.of(home);
    }

    // --- helpers ---

    private static Map<String, String> slotPlaceholder(HomeSlot slot) {
        return Map.of("slot", Integer.toString(slot.displayNumber()));
    }

    private static Map<String, String> attribution(PlayerRef target, HomeSlot slot) {
        return Map.of("player", target.name(), "slot", Integer.toString(slot.displayNumber()));
    }

    private static Map<String, String> infoPlaceholders(PlayerRef target, Home home) {
        Position loc = home.location();
        // Icon: pass the material name when present, empty string when absent, the catalog template
        // formats the {icon} placeholder and never contains a raw fallback literal.
        String iconValue = home.icon().map(i -> i.materialName()).orElse("");
        String labelValue = home.label().map(l -> l.value()).orElse("");
        return Map.of(
                "player", target.name(),
                "slot", Integer.toString(home.slot().displayNumber()),
                "label", labelValue,
                "world", loc.world().name(),
                "x", Integer.toString(loc.blockX()),
                "y", Integer.toString(loc.blockY()),
                "z", Integer.toString(loc.blockZ()),
                "icon", iconValue);
    }
}
