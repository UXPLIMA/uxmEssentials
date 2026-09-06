package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpCapability;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.playerwarps.domain.event.PlayerWarpDeleted;
import com.uxplima.uxmessentials.playerwarps.domain.event.PlayerWarpDeleting;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.DomainGate;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * Warp removal, split into the recoverable default and the irreversible admin path (spec invariant 5). The
 * everyday {@code /pwarp del} {@link #archive(PlayerRef, PlayerWarpName) archives}: it flips the warp to
 * {@link WarpStatus#ARCHIVED} and saves the row, so the name is retired from listings but the warp, and its
 * whitelist, bans, earnings, and history: survive a world rollback and can be {@link #restore restored}. Only the
 * admin-gated, confirm-guarded {@link #hardDelete hardDelete} actually drops the row and frees the name for reuse,
 * publishing {@code PlayerWarpDeleted} so downstream (cross-server invalidation, cascade cleanup) can react.
 *
 * <p>All three verbs gate on {@link WarpCapability#DELETE} through {@link WarpAuthorization}, which only the owner
 * holds. Co-owners, managers, and strangers are refused, since removing a warp out from under its owner is not a
 * delegable act. Resolving the warp first
 * means a name no warp exists under is {@link PlayerWarpError#NOT_FOUND}, distinct from a warp the actor may not
 * touch ({@link PlayerWarpError#NO_PERMISSION}). The command layer, not this use case, decides which verb a given
 * button or subcommand runs and gates the admin node behind {@code hardDelete}.
 */
public final class ArchivePlayerWarp {

    private final PlayerWarpRepository repository;
    private final WarpAuthorization authorization;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final DomainGate gate;
    private final Clock clock;

    /** The use case with nothing outside the plugin able to refuse a delete. The form the pure tests use. */
    public ArchivePlayerWarp(
            PlayerWarpRepository repository,
            WarpAuthorization authorization,
            Notifier notifier,
            DomainEventPublisher events,
            Clock clock) {
        this(repository, authorization, notifier, events, DomainGate.allowAll(), clock);
    }

    public ArchivePlayerWarp(
            PlayerWarpRepository repository,
            WarpAuthorization authorization,
            Notifier notifier,
            DomainEventPublisher events,
            DomainGate gate,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Retire {@code name} to {@link WarpStatus#ARCHIVED}: recoverable, the row is kept. Gate DELETE. */
    public Result<Unit, PlayerWarpError> archive(PlayerRef actor, PlayerWarpName name) {
        return gated(actor, name, warp -> {
            repository.save(warp.withStatus(WarpStatus.ARCHIVED, clock.instant()));
            notifier.send(actor, PlayerwarpsMessageKey.PWARP_ARCHIVED, Map.of("warp", name.value()));
            return Result.ok();
        });
    }

    /** Bring an archived {@code name} back to {@link WarpStatus#ACTIVE}. Gate DELETE. */
    public Result<Unit, PlayerWarpError> restore(PlayerRef actor, PlayerWarpName name) {
        return gated(actor, name, warp -> {
            repository.save(warp.withStatus(WarpStatus.ACTIVE, clock.instant()));
            notifier.send(actor, PlayerwarpsMessageKey.PWARP_RESTORED, Map.of("warp", name.value()));
            return Result.ok();
        });
    }

    /**
     * Irreversibly delete {@code name}: drop the row by its surrogate id and publish {@code PlayerWarpDeleted}. Gate
     * DELETE. This is the admin, confirmed path; the command layer places it behind the admin node and a confirm.
     */
    public Result<Unit, PlayerWarpError> hardDelete(PlayerRef actor, PlayerWarpName name) {
        return gated(actor, name, warp -> {
            // Asked only on the irreversible path. Archiving is undoable, so refusing it would be refusing something
            // the owner can put back themselves.
            if (!gate.allows(new PlayerWarpDeleting(warp.owner(), name))) {
                notifier.send(actor, PlayerWarpError.VETOED.messageKey(), Map.of("warp", name.value()));
                return Result.err(PlayerWarpError.VETOED);
            }
            repository.deleteById(warp.id().orElseThrow());
            events.publish(new PlayerWarpDeleted(warp.owner(), name));
            notifier.send(actor, PlayerwarpsMessageKey.PWARP_DELETED, Map.of("warp", name.value()));
            return Result.ok();
        });
    }

    /**
     * Operator restore of the warp with surrogate id {@code id} to {@link WarpStatus#ACTIVE}, skipping the per-warp
     * role gate. This is the by-id admin path {@code /pwarp admin restore} runs; the command's {@code admin} node is
     * the authorization, so this method resolves the warp and settles its status without re-checking a role, a
     * missing id is {@link PlayerWarpError#NOT_FOUND}. The command renders the operator-facing result itself.
     */
    public Result<Unit, PlayerWarpError> adminRestore(PlayerWarpId id) {
        Objects.requireNonNull(id, "id");
        Optional<PlayerWarp> found = repository.findById(id);
        if (found.isEmpty()) {
            return Result.err(PlayerWarpError.NOT_FOUND);
        }
        repository.save(found.get().withStatus(WarpStatus.ACTIVE, clock.instant()));
        return Result.ok();
    }

    /**
     * Operator hard-delete of the warp with surrogate id {@code id}: drop the row and publish
     * {@code PlayerWarpDeleted}, skipping the per-warp role gate (spec invariant 5, the irreversible path). The
     * command places this behind the {@code admin} node and a confirm step; a missing id is
     * {@link PlayerWarpError#NOT_FOUND}.
     *
     * <p>No third-party plugin can refuse this one. Staff clearing an abusive warp must not be blockable by whatever
     * else is installed, and the operator already confirmed it; the owner path is where the veto question belongs.
     */
    public Result<Unit, PlayerWarpError> adminHardDelete(PlayerWarpId id) {
        Objects.requireNonNull(id, "id");
        Optional<PlayerWarp> found = repository.findById(id);
        if (found.isEmpty()) {
            return Result.err(PlayerWarpError.NOT_FOUND);
        }
        PlayerWarp warp = found.get();
        repository.deleteById(warp.id().orElseThrow());
        events.publish(new PlayerWarpDeleted(warp.owner(), warp.name()));
        return Result.ok();
    }

    /** Resolve {@code name}, gate {@code actor} on DELETE, then run {@code body}; otherwise NOT_FOUND / NO_PERMISSION. */
    private Result<Unit, PlayerWarpError> gated(
            PlayerRef actor,
            PlayerWarpName name,
            java.util.function.Function<PlayerWarp, Result<Unit, PlayerWarpError>> body) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<PlayerWarp> found = repository.findByName(name);
        if (found.isEmpty()) {
            notifier.send(actor, PlayerWarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.NOT_FOUND);
        }
        PlayerWarp warp = found.get();
        if (!authorization.allows(warp, actor.uuid(), WarpCapability.DELETE)) {
            notifier.send(actor, PlayerWarpError.NO_PERMISSION.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.NO_PERMISSION);
        }
        return body.apply(warp);
    }
}
