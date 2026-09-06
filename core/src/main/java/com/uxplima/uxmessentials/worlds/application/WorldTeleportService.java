package com.uxplima.uxmessentials.worlds.application;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldEntryFee;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.application.port.WorldTeleporter;
import com.uxplima.uxmessentials.worlds.domain.AccessDecision;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldTeleportCause;
import com.uxplima.uxmessentials.worlds.domain.event.WorldEntryDenied;
import org.jspecify.annotations.NullMarked;

/**
 * The single entry point both for the self-service {@code /worlds spawn} (access-gated and fee-charged) and for
 * the staff {@code /worlds tp} override ({@link #forced}, which skips the gate and the fee).
 *
 * <p>{@link #spawn} runs the full pipeline: resolve the world (else {@link WorldError#NOT_FOUND}), load it on
 * demand, evaluate the {@link WorldAccessPolicy} gate (publishing {@link WorldEntryDenied} and refusing on a
 * denial), charge the {@link WorldProperties#ENTRY_FEE} unless the player holds {@link WorldAccessPolicy#BYPASS_NODE},
 * then hand the player off to the live spawn. The live spawn is authoritative. The stored custom spawn is pushed
 * onto the world when it loads, so the destination is always {@link WorldEngine#spawnPoint}. The fee is debited
 * only after the teleport is accepted, so a rejected hand-off never costs the player money.
 */
@NullMarked
public final class WorldTeleportService {

    private final WorldRepository repository;
    private final WorldEngine engine;
    private final WorldAccessPolicy policy;
    private final WorldTeleporter teleporter;
    private final WorldEntryFee entryFee;
    private final Permissions permissions;
    private final DomainEventPublisher events;
    private final Notifier notifier;
    private final Scheduler scheduler;

    public WorldTeleportService(
            WorldRepository repository,
            WorldEngine engine,
            WorldAccessPolicy policy,
            WorldTeleporter teleporter,
            WorldEntryFee entryFee,
            Permissions permissions,
            DomainEventPublisher events,
            Notifier notifier,
            Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.entryFee = Objects.requireNonNull(entryFee, "entryFee");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.events = Objects.requireNonNull(events, "events");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Self-service entry: resolve, load, gate on access and fee, then hand the player off to the world spawn. */
    public Result<Unit, WorldError> spawn(PlayerRef who, WorldName target) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(target, "target");
        Optional<ManagedWorld> found = repository.find(target);
        if (found.isEmpty()) {
            return fail(who, target, WorldError.NOT_FOUND, WorldsMessageKey.WORLD_NOT_FOUND);
        }
        ManagedWorld world = found.get();
        Optional<WorldError> loadFailure = ensureLoaded(world);
        if (loadFailure.isPresent()) {
            return fail(who, target, loadFailure.get(), loadFailure.get().messageKey());
        }
        Optional<WorldError> denied = gateAccess(who, world);
        if (denied.isPresent()) {
            return Result.err(denied.get());
        }
        BigDecimal fee = world.settings().get(WorldProperties.ENTRY_FEE);
        boolean charged = fee.signum() > 0 && !permissions.has(who, WorldAccessPolicy.BYPASS_NODE);
        if (charged && !entryFee.canAfford(who, fee)) {
            notify(who, WorldsMessageKey.WORLD_ENTER_FEE_INSUFFICIENT, feePlaceholders(target, fee));
            return Result.err(WorldError.ENTRY_FEE_UNAFFORDABLE);
        }
        return handOff(who, target, fee, charged);
    }

    /** Staff override: resolve, load, then teleport the subject to the world spawn ignoring restriction and fee. */
    public Result<Unit, WorldError> forced(PlayerRef actor, PlayerRef subject, WorldName target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(target, "target");
        Optional<ManagedWorld> found = repository.find(target);
        if (found.isEmpty()) {
            return fail(actor, target, WorldError.NOT_FOUND, WorldsMessageKey.WORLD_NOT_FOUND);
        }
        Optional<WorldError> loadFailure = ensureLoaded(found.get());
        if (loadFailure.isPresent()) {
            return fail(actor, target, loadFailure.get(), loadFailure.get().messageKey());
        }
        Optional<Position> pos = engine.spawnPoint(target);
        if (pos.isEmpty()) {
            return fail(
                    actor, target, WorldError.DESTINATION_UNRESOLVED, WorldsMessageKey.WORLD_TP_DESTINATION_UNRESOLVED);
        }
        teleporter.teleport(subject, pos.get(), WorldTeleportCause.ADMIN);
        if (!actor.equals(subject)) {
            notify(actor, WorldsMessageKey.WORLD_TP_OTHER, Map.of("player", subject.name(), "world", target.value()));
        }
        return Result.ok();
    }

    private Result<Unit, WorldError> handOff(PlayerRef who, WorldName target, BigDecimal fee, boolean charged) {
        Optional<Position> pos = engine.spawnPoint(target);
        if (pos.isEmpty()) {
            return fail(
                    who, target, WorldError.DESTINATION_UNRESOLVED, WorldsMessageKey.WORLD_TP_DESTINATION_UNRESOLVED);
        }
        boolean accepted = teleporter.teleport(who, pos.get(), WorldTeleportCause.SPAWN);
        if (accepted && charged) {
            entryFee.charge(who, fee);
            notify(who, WorldsMessageKey.WORLD_ENTER_FEE_CHARGED, feePlaceholders(target, fee));
        }
        return Result.ok();
    }

    /** Load the world if the engine has not yet, surfacing the engine's error when the load fails. */
    private Optional<WorldError> ensureLoaded(ManagedWorld world) {
        if (engine.isLoaded(world.name())) {
            return Optional.empty();
        }
        Result<Unit, WorldError> result = engine.load(world);
        return result.isErr() ? Optional.of(result.errorOrThrow()) : Optional.empty();
    }

    /** Run the access gate, publishing the denial event and notifying the precise reason key when refused. */
    private Optional<WorldError> gateAccess(PlayerRef who, ManagedWorld world) {
        AccessDecision decision = policy.decide(who, world);
        if (decision.allowed()) {
            return Optional.empty();
        }
        WorldName target = world.name();
        events.publish(new WorldEntryDenied(target, who, decision));
        notify(who, denialKey(decision), denialPlaceholders(decision, world));
        return Optional.of(WorldError.ACCESS_DENIED);
    }

    private static WorldsMessageKey denialKey(AccessDecision decision) {
        return switch (decision) {
            case DENIED_FULL -> WorldsMessageKey.WORLD_ENTER_DENIED_FULL;
            case DENIED_PERMISSION, ALLOWED -> WorldsMessageKey.WORLD_ENTER_DENIED_PERMISSION;
        };
    }

    private static Map<String, String> denialPlaceholders(AccessDecision decision, ManagedWorld world) {
        String name = world.name().value();
        if (decision == AccessDecision.DENIED_FULL) {
            return Map.of(
                    "world", name, "limit", String.valueOf(world.settings().get(WorldProperties.PLAYER_LIMIT)));
        }
        return Map.of("world", name);
    }

    private static Map<String, String> feePlaceholders(WorldName target, BigDecimal fee) {
        return Map.of("world", target.value(), "amount", fee.toPlainString());
    }

    private Result<Unit, WorldError> fail(PlayerRef who, WorldName target, WorldError error, MessageKey key) {
        notify(who, key, Map.of("world", target.value()));
        return Result.err(error);
    }

    /** Folia-safe notify: bounce the delivery back onto the recipient's region thread. */
    private void notify(PlayerRef who, MessageKey key, Map<String, String> placeholders) {
        scheduler.onEntity(who, () -> notifier.send(who, key, placeholders));
    }
}
