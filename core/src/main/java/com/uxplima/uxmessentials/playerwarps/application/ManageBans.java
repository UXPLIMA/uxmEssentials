package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpBanStore;
import com.uxplima.uxmessentials.playerwarps.domain.BanRecord;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpCapability;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /pwarp ban|unban}: bar a player from a warp or lift that bar. Allowed to the owner, a co-owner, and a
 * manager by the capability matrix ({@link WarpCapability#MANAGE_BANS}). Each verb resolves the warp
 * ({@link PlayerWarpError#NOT_FOUND}), gates the actor ({@link PlayerWarpError#NO_PERMISSION}), then upserts or
 * lifts the ban row and notifies with a key distinct from the access gate's {@link PlayerWarpError#BANNED} refusal
 *: this is the manager's confirmation, not the visitor's rejection.
 *
 * <p>Banning the warp's own owner is refused ({@link PlayerWarpError#CANNOT_TARGET_OWNER}) so no delegate can lock
 * the owner out of their own warp. An empty {@code duration} is a permanent ban (an empty {@code until}); a present
 * duration is stored as the absolute instant {@code now + duration}, so a slow later read can never silently extend
 * it. The imposing actor is recorded as {@code bannedBy}, and the {@link Clock}, never the domain, mints both the
 * {@code bannedAt} stamp and the expiry base.
 */
public final class ManageBans {

    private final PlayerWarpRepository repository;
    private final WarpAuthorization authorization;
    private final WarpBanStore bans;
    private final Notifier notifier;
    private final Clock clock;

    public ManageBans(
            PlayerWarpRepository repository,
            WarpAuthorization authorization,
            WarpBanStore bans,
            Notifier notifier,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.bans = Objects.requireNonNull(bans, "bans");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Ban {@code target} from warp {@code name}, permanent when {@code duration} is empty, unless they own it. */
    public Result<Unit, PlayerWarpError> ban(
            PlayerRef actor,
            PlayerWarpName name,
            PlayerRef target,
            Optional<Duration> duration,
            Optional<String> reason) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(reason, "reason");
        return gate(actor, name, warp -> {
            if (target.uuid().equals(warp.owner().uuid())) {
                notifier.send(actor, PlayerWarpError.CANNOT_TARGET_OWNER.messageKey(), placeholders(name, target));
                return Result.err(PlayerWarpError.CANNOT_TARGET_OWNER);
            }
            Instant now = clock.instant();
            BanRecord record =
                    new BanRecord(target.uuid(), duration.map(now::plus), reason, Optional.of(actor.uuid()), now);
            bans.ban(warp.id().orElseThrow(), record);
            notifier.send(actor, PlayerwarpsMessageKey.PWARP_BAN_SET, placeholders(name, target));
            return Result.ok();
        });
    }

    /** Lift {@code target}'s ban from warp {@code name}; a no-op at the store when they were not banned. */
    public Result<Unit, PlayerWarpError> unban(PlayerRef actor, PlayerWarpName name, PlayerRef target) {
        Objects.requireNonNull(target, "target");
        return gate(actor, name, warp -> {
            bans.unban(warp.id().orElseThrow(), target.uuid());
            notifier.send(actor, PlayerwarpsMessageKey.PWARP_BAN_LIFTED, placeholders(name, target));
            return Result.ok();
        });
    }

    private static Map<String, String> placeholders(PlayerWarpName name, PlayerRef target) {
        return Map.of("warp", name.value(), "player", target.name());
    }

    /** Resolve {@code name} and gate {@code actor} on {@link WarpCapability#MANAGE_BANS} before running {@code body}. */
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
        if (!authorization.allows(warp, actor.uuid(), WarpCapability.MANAGE_BANS)) {
            notifier.send(actor, PlayerWarpError.NO_PERMISSION.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.NO_PERMISSION);
        }
        return body.apply(warp);
    }
}
