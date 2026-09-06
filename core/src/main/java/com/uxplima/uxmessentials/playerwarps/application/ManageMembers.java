package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpMemberStore;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpCapability;
import com.uxplima.uxmessentials.playerwarps.domain.WarpMember;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /pwarp members add|remove}: grant or revoke a co-owner / manager on a warp. Owner-only by the capability
 * matrix ({@link WarpCapability#MANAGE_MEMBERS} is denied to co-owners and managers) so a delegate can never
 * promote further delegates. Each verb resolves the warp by its global name ({@link PlayerWarpError#NOT_FOUND}
 * when absent), gates the actor ({@link PlayerWarpError#NO_PERMISSION} when they may not manage members), then
 * upserts or removes the membership row and notifies.
 *
 * <p>Two grants are refused after the gate: {@link WarpRole#OWNER} is never a grantable role. A warp keeps its
 * single owner, so a member may only be a co-owner or manager ({@link PlayerWarpError#INVALID_ROLE}), and the
 * warp's own owner is never added as a member ({@link PlayerWarpError#CANNOT_TARGET_OWNER}); ownership already
 * confers every capability, and the aggregate's owner field, not a member row, is the source of truth for it.
 */
public final class ManageMembers {

    private final PlayerWarpRepository repository;
    private final WarpAuthorization authorization;
    private final WarpMemberStore members;
    private final Notifier notifier;
    private final Clock clock;

    public ManageMembers(
            PlayerWarpRepository repository,
            WarpAuthorization authorization,
            WarpMemberStore members,
            Notifier notifier,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.members = Objects.requireNonNull(members, "members");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Grant {@code target} the {@code role} on warp {@code name}, rejecting an owner role or targeting the owner. */
    public Result<Unit, PlayerWarpError> addMember(
            PlayerRef actor, PlayerWarpName name, PlayerRef target, WarpRole role) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(role, "role");
        return gate(actor, name, warp -> {
            if (role == WarpRole.OWNER) {
                return refuse(actor, name, target, PlayerWarpError.INVALID_ROLE);
            }
            if (target.uuid().equals(warp.owner().uuid())) {
                return refuse(actor, name, target, PlayerWarpError.CANNOT_TARGET_OWNER);
            }
            members.put(warp.id().orElseThrow(), new WarpMember(target.uuid(), role, clock.instant()));
            notifier.send(actor, PlayerwarpsMessageKey.PWARP_MEMBER_ADDED, placeholders(name, target));
            return Result.ok();
        });
    }

    /** Revoke {@code target}'s membership of warp {@code name}; a no-op at the store when they held none. */
    public Result<Unit, PlayerWarpError> removeMember(PlayerRef actor, PlayerWarpName name, PlayerRef target) {
        Objects.requireNonNull(target, "target");
        return gate(actor, name, warp -> {
            members.remove(warp.id().orElseThrow(), target.uuid());
            notifier.send(actor, PlayerwarpsMessageKey.PWARP_MEMBER_REMOVED, placeholders(name, target));
            return Result.ok();
        });
    }

    private Result<Unit, PlayerWarpError> refuse(
            PlayerRef actor, PlayerWarpName name, PlayerRef target, PlayerWarpError error) {
        notifier.send(actor, error.messageKey(), placeholders(name, target));
        return Result.err(error);
    }

    private static Map<String, String> placeholders(PlayerWarpName name, PlayerRef target) {
        return Map.of("warp", name.value(), "player", target.name());
    }

    /** Resolve {@code name} and gate {@code actor} on {@link WarpCapability#MANAGE_MEMBERS} before running {@code body}. */
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
        if (!authorization.allows(warp, actor.uuid(), WarpCapability.MANAGE_MEMBERS)) {
            notifier.send(actor, PlayerWarpError.NO_PERMISSION.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.NO_PERMISSION);
        }
        return body.apply(warp);
    }
}
