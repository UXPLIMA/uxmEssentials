package com.uxplima.uxmessentials.playerwarps.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpWhitelistStore;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpCapability;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /pwarp whitelist add|remove}: manage the guest list of a {@code WHITELIST}-access warp. Allowed to the
 * owner, a co-owner, and a manager by the capability matrix ({@link WarpCapability#MANAGE_WHITELIST}), the guest
 * list is presentation-adjacent, not the money or the lifecycle, so a manager may run it. Each verb resolves the
 * warp ({@link PlayerWarpError#NOT_FOUND}), gates the actor ({@link PlayerWarpError#NO_PERMISSION}), then adds or
 * removes the whitelist row and notifies. The add is idempotent at the store, so re-whitelisting a player is a
 * confirming no-op rather than a duplicate row.
 */
public final class ManageWhitelist {

    private final PlayerWarpRepository repository;
    private final WarpAuthorization authorization;
    private final WarpWhitelistStore whitelist;
    private final Notifier notifier;

    public ManageWhitelist(
            PlayerWarpRepository repository,
            WarpAuthorization authorization,
            WarpWhitelistStore whitelist,
            Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.whitelist = Objects.requireNonNull(whitelist, "whitelist");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Whitelist {@code target} on warp {@code name}; idempotent at the store. */
    public Result<Unit, PlayerWarpError> whitelist(PlayerRef actor, PlayerWarpName name, PlayerRef target) {
        Objects.requireNonNull(target, "target");
        return gate(actor, name, warp -> {
            whitelist.add(warp.id().orElseThrow(), target.uuid());
            notifier.send(actor, PlayerwarpsMessageKey.PWARP_WHITELIST_ADDED, placeholders(name, target));
            return Result.ok();
        });
    }

    /** Remove {@code target} from warp {@code name}'s whitelist; a no-op at the store when they were not on it. */
    public Result<Unit, PlayerWarpError> unwhitelist(PlayerRef actor, PlayerWarpName name, PlayerRef target) {
        Objects.requireNonNull(target, "target");
        return gate(actor, name, warp -> {
            whitelist.remove(warp.id().orElseThrow(), target.uuid());
            notifier.send(actor, PlayerwarpsMessageKey.PWARP_WHITELIST_REMOVED, placeholders(name, target));
            return Result.ok();
        });
    }

    private static Map<String, String> placeholders(PlayerWarpName name, PlayerRef target) {
        return Map.of("warp", name.value(), "player", target.name());
    }

    /** Resolve {@code name} and gate {@code actor} on {@link WarpCapability#MANAGE_WHITELIST} before running {@code body}. */
    private Result<Unit, PlayerWarpError> gate(
            PlayerRef actor, PlayerWarpName name, Function<PlayerWarp, Result<Unit, PlayerWarpError>> body) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<PlayerWarp> found = repository.findByName(name);
        if (found.isEmpty()) {
            notifier.send(actor, PlayerWarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.NOT_FOUND);
        }
        PlayerWarp warp = found.get();
        if (!authorization.allows(warp, actor.uuid(), WarpCapability.MANAGE_WHITELIST)) {
            notifier.send(actor, PlayerWarpError.NO_PERMISSION.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.NO_PERMISSION);
        }
        return body.apply(warp);
    }
}
