package com.uxplima.uxmessentials.kits.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitError;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /kit reset <player> [kit]}: clear a player's one-time-claim stamps so they may claim again
 * ({@code uxmessentials.kit.reset}). With a kit id, only that kit's stamp is cleared; without one, every
 * one-time stamp the player holds is cleared. A named kit that does not exist is refused so a typo does not
 * silently no-op. The operator-only permission is enforced at the command gate.
 *
 * <p>This clears the persisted one-time mark held in PDC; it does not touch the cooldown clock, which expires
 * on its own — resetting the one-time state is the operator's tool to let a player re-take a consumed
 * starter or donor kit. The target is addressed by {@link PlayerRef}; an offline target has no PDC to clear,
 * so the reset no-ops at the store while still reporting success to the operator.
 */
public final class KitReset {

    private final KitRepository repository;
    private final KitClaimStore claims;
    private final Notifier notifier;

    public KitReset(KitRepository repository, KitClaimStore claims, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Clear every one-time stamp {@code target} holds. */
    public Result<Unit, KitError> resetAll(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        claims.resetAll(target);
        notifier.send(actor, KitsMessageKey.KIT_RESET_ALL, Map.of("player", target.name()));
        return Result.ok();
    }

    /** Clear {@code target}'s one-time stamp for the kit {@code id}, or reject when no such kit exists. */
    public Result<Unit, KitError> reset(PlayerRef actor, PlayerRef target, KitId id) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(id, "id");
        Optional<?> kit = repository.find(id);
        if (kit.isEmpty()) {
            notifier.send(actor, KitError.NOT_FOUND.messageKey(), Map.of("kit", id.value()));
            return Result.err(KitError.NOT_FOUND);
        }
        claims.reset(target, id);
        notifier.send(actor, KitsMessageKey.KIT_RESET, Map.of("player", target.name(), "kit", id.value()));
        return Result.ok();
    }
}
