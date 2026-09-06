package com.uxplima.uxmessentials.homes.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /home <slot>}: teleport the player to the home in one of their slots. Execution is
 * <em>delegated</em> to the teleport context through {@link HomeTeleporter}. This use case never moves the
 * player itself, so the shared cooldown, the move-cancellable warmup, and the region-aware async hop are all
 * the teleport context's concern. An empty slot is rejected with {@link HomeError#NOT_FOUND}; an optional
 * economy charge is applied after the home is found but before the hop is handed to the teleport context.
 */
public final class TeleportHome {

    private final HomeRepository repository;
    private final HomeTeleporter teleporter;
    private final Notifier notifier;
    private final HomeCharge charge;

    public TeleportHome(HomeRepository repository, HomeTeleporter teleporter, Notifier notifier, HomeCharge charge) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.charge = Objects.requireNonNull(charge, "charge");
    }

    /** Teleport {@code who} to the home in {@code slot}, or reject when the slot is empty or the player cannot afford the charge. */
    public Result<Unit, HomeError> toSlot(PlayerRef who, HomeSlot slot) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(slot, "slot");
        Optional<Home> home = repository.findSlot(who, slot);
        if (home.isEmpty()) {
            notifier.send(who, HomeError.NOT_FOUND.messageKey(), slotPlaceholder(slot));
            return Result.err(HomeError.NOT_FOUND);
        }
        Result<Unit, HomeError> charged = charge.charge(who, HomeChargeKind.TELEPORT);
        if (charged.isErr()) {
            notifier.send(who, HomeError.CANNOT_AFFORD.messageKey());
            return Result.err(HomeError.CANNOT_AFFORD);
        }
        notifier.send(who, HomesMessageKey.HOME_TELEPORTING, slotPlaceholder(slot));
        teleporter.teleportTo(who, home.get());
        return Result.ok();
    }

    private static Map<String, String> slotPlaceholder(HomeSlot slot) {
        return Map.of("slot", Integer.toString(slot.displayNumber()));
    }
}
