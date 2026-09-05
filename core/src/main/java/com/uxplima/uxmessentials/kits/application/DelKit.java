package com.uxplima.uxmessentials.kits.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitError;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /kit del <name>}: remove a kit definition, freeing its id for reuse. An id no kit exists under is
 * rejected with {@link KitError#NOT_FOUND}; a successful delete removes the entry from the config-backed
 * catalog. Per-player one-time stamps for the deleted kit are left in place — they are harmless dangling PDC
 * values keyed by a kit that no longer exists, and {@code /kit reset} can clear them if a kit is later
 * recreated under the same id. The operator-only permission is enforced at the command gate.
 */
public final class DelKit {

    private final KitRepository repository;
    private final Notifier notifier;

    public DelKit(KitRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Delete the kit {@code id}, or reject when no such kit exists. */
    public Result<Unit, KitError> delete(PlayerRef actor, KitId id) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(id, "id");
        if (!repository.exists(id)) {
            notifier.send(actor, KitError.NOT_FOUND.messageKey(), Map.of("kit", id.value()));
            return Result.err(KitError.NOT_FOUND);
        }
        repository.delete(id);
        notifier.send(actor, KitsMessageKey.KIT_DELETED, Map.of("kit", id.value()));
        return Result.ok();
    }
}
