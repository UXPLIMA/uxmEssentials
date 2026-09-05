package com.uxplima.uxmessentials.warps.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpError;
import com.uxplima.uxmessentials.warps.domain.WarpName;

/**
 * {@code /movewarp <name>}: re-anchor an existing warp to the staff member's current position, keeping its
 * name, owner, creation time, and gates. Distinct from {@code /setwarp} on an existing name only in
 * intent — it requires the warp to already exist (rejecting a missing name) and never creates one. The
 * operator-only permission is enforced at the command gate.
 */
public final class MoveWarp {

    private final WarpRepository repository;
    private final Notifier notifier;

    public MoveWarp(WarpRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Move the warp {@code name} to {@code at}, or reject when no such warp exists. */
    public Result<Unit, WarpError> move(PlayerRef actor, WarpName name, Position at) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(at, "at");
        Optional<Warp> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, WarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(WarpError.NOT_FOUND);
        }
        repository.save(existing.get().movedTo(at));
        notifier.send(actor, WarpsMessageKey.WARP_MOVED, Map.of("warp", name.value()));
        return Result.ok();
    }
}
