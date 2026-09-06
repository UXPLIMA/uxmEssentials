package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /pwarp public <name>} / {@code /pwarp private <name>}: flip one of the owner's player-warps between the
 * {@link WarpAccess#PUBLIC} access (any player may use it) and {@link WarpAccess#PRIVATE} (only the owner may).
 * The warp is resolved by its global name and guarded by ownership. A name no warp of the owner's exists under
 * is rejected with {@link PlayerWarpError#NOT_FOUND}; a flip saves the warp with the new access, stamping the
 * edit time from the injected {@link Clock}, and renders the matching feedback. An owner only ever toggles their
 * own warps.
 */
public final class SetPlayerWarpVisibility {

    private final PlayerWarpRepository repository;
    private final Notifier notifier;
    private final Clock clock;

    public SetPlayerWarpVisibility(PlayerWarpRepository repository, Notifier notifier, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Make {@code owner}'s warp {@code name} public. */
    public Result<Unit, PlayerWarpError> setPublic(PlayerRef owner, PlayerWarpName name) {
        return apply(owner, name, WarpAccess.PUBLIC, PlayerwarpsMessageKey.PWARP_PUBLIC);
    }

    /** Make {@code owner}'s warp {@code name} private. */
    public Result<Unit, PlayerWarpError> setPrivate(PlayerRef owner, PlayerWarpName name) {
        return apply(owner, name, WarpAccess.PRIVATE, PlayerwarpsMessageKey.PWARP_PRIVATE);
    }

    private Result<Unit, PlayerWarpError> apply(
            PlayerRef owner, PlayerWarpName name, WarpAccess access, PlayerwarpsMessageKey feedback) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Optional<PlayerWarp> warp = repository.findByName(name);
        if (warp.isEmpty() || !warp.get().owner().uuid().equals(owner.uuid())) {
            notifier.send(owner, PlayerWarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.NOT_FOUND);
        }
        repository.save(warp.get().withAccess(access, clock.instant()));
        notifier.send(owner, feedback, Map.of("warp", name.value()));
        return Result.ok();
    }
}
