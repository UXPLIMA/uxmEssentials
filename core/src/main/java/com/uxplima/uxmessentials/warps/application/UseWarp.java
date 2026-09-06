package com.uxplima.uxmessentials.warps.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpSafetyChecker;
import com.uxplima.uxmessentials.warps.application.port.WarpTeleporter;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpError;
import com.uxplima.uxmessentials.warps.domain.WarpName;

/**
 * {@code /warp <name>}: teleport a player to a server warp. The warp is resolved by name, the player is run
 * through the {@link WarpAccess} gate (per-warp permission, the warp's optional extra permission, and, only
 * when an economy provider is present. The per-warp cost), and only then is execution <em>delegated</em> to
 * the teleport context through {@link WarpTeleporter}. This use case never moves the player itself, so the
 * shared cooldown, the move-cancellable warmup, and the region-aware async hop are all the teleport
 * context's concern.
 *
 * <p>The charge (if any) is taken inside the access gate before the hop is queued, so a warp the player
 * cannot afford never teleports them. With no economy provider wired, a priced warp's cost is ignored and
 * the warp is usable for free: the soft coupling to the economy context.
 */
public final class UseWarp {

    private final WarpRepository repository;
    private final WarpAccess access;
    private final WarpTeleporter teleporter;
    private final Notifier notifier;
    private final WarpSafetyChecker safetyChecker;
    private final Permissions permissions;
    private final Scheduler scheduler;

    public UseWarp(
            WarpRepository repository,
            WarpAccess access,
            WarpTeleporter teleporter,
            Notifier notifier,
            WarpSafetyChecker safetyChecker,
            Permissions permissions,
            Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.access = Objects.requireNonNull(access, "access");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.safetyChecker = Objects.requireNonNull(safetyChecker, "safetyChecker");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Teleport {@code who} to the warp {@code name} themselves, gating access and cost first. */
    public Result<Unit, WarpError> use(PlayerRef who, WarpName name) {
        return useFor(who, who, name, Optional.empty());
    }

    public Result<Unit, WarpError> use(PlayerRef who, WarpName name, String password) {
        return useFor(who, who, name, Optional.of(password));
    }

    /**
     * Send {@code recipient} to the warp {@code name}, triggered by {@code actor} (a staff send when the two
     * differ). The access gate and the optional cost apply to the <em>recipient</em>, staff send another
     * player only to a warp that player may use, and the recipient is the one teleported. On a cross-player
     * success the actor is told the send went through; a self-send (actor == recipient) takes the usual
     * teleporting path with no extra notice.
     */
    public Result<Unit, WarpError> useFor(PlayerRef actor, PlayerRef recipient, WarpName name) {
        return useFor(actor, recipient, name, Optional.empty());
    }

    public Result<Unit, WarpError> useFor(
            PlayerRef actor, PlayerRef recipient, WarpName name, Optional<String> password) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(name, "name");
        Optional<Warp> warp = repository.find(name);
        if (warp.isEmpty()) {
            notifier.send(actor, WarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(WarpError.NOT_FOUND);
        }
        return admitAndGo(actor, recipient, warp.get(), password);
    }

    private Result<Unit, WarpError> admitAndGo(
            PlayerRef actor, PlayerRef recipient, Warp warp, Optional<String> enteredPassword) {
        Result<Unit, WarpError> admitted = access.admit(recipient, warp);
        if (admitted.isErr()) {
            WarpError error = admitted.errorOrThrow();
            notifier.send(actor, error.messageKey(), Map.of("warp", warp.name().value()));
            return admitted;
        }

        // Lock check
        if (warp.isLocked() && !permissions.has(recipient, "uxmessentials.warp.bypass.lock")) {
            notifier.send(
                    actor,
                    WarpError.LOCKED.messageKey(),
                    Map.of("warp", warp.name().value()));
            return Result.err(WarpError.LOCKED);
        }

        // Password check
        if (warp.password().isPresent() && !permissions.has(recipient, "uxmessentials.warp.bypass.password")) {
            String required = warp.password().get();
            if (enteredPassword.isEmpty() || !enteredPassword.get().equals(required)) {
                notifier.send(
                        actor,
                        WarpError.WRONG_PASSWORD.messageKey(),
                        Map.of("warp", warp.name().value()));
                return Result.err(WarpError.WRONG_PASSWORD);
            }
        }

        // Safety check
        if (!safetyChecker.isSafe(warp.location()) && !permissions.has(recipient, "uxmessentials.warp.bypass.safety")) {
            notifier.send(
                    actor,
                    WarpError.UNSAFE_LOCATION.messageKey(),
                    Map.of("warp", warp.name().value()));
            return Result.err(WarpError.UNSAFE_LOCATION);
        }

        Map<String, String> placeholders = Map.of("warp", warp.name().value());
        notifier.send(recipient, WarpsMessageKey.WARP_TELEPORTING, placeholders);
        if (!actor.uuid().equals(recipient.uuid())) {
            notifier.send(
                    actor, WarpsMessageKey.WARP_SENT, Map.of("warp", warp.name().value(), "player", recipient.name()));
        }

        // Increment the visitor counter. The write persists to the DB (a single-writer SQLite by default),
        // so it is handed to the async scheduler rather than run on the caller's region thread, the counter
        // is a display statistic the teleport does not depend on, and the in-memory {@code updated} warp
        // already carries the new count for the hop below.
        Warp updated = warp.incrementedVisitors();
        scheduler.async(() -> repository.save(updated));

        teleporter.teleportTo(recipient, updated);
        return Result.ok();
    }
}
