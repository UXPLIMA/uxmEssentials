package com.uxplima.uxmessentials.kits.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitError;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /kit create <name>}: define a new kit from the staff member's current inventory (the adapter
 * serializes the held items into the {@link KitDefinition} before calling here). A new id is stored as a new
 * kit and persisted to its own {@code modules/kits/kits/<id>.conf} file; an id a kit already exists under is
 * refused with
 * {@link KitError#ALREADY_EXISTS} so a create never silently clobbers a curated kit (that is what
 * {@code /kit editor} is for). The operator-only permission is enforced at the command gate.
 */
public final class CreateKit {

    private final KitRepository repository;
    private final Notifier notifier;

    public CreateKit(KitRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Create the kit described by {@code definition}, or refuse when its id is already taken. */
    public Result<Unit, KitError> create(PlayerRef actor, KitDefinition definition) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(definition, "definition");
        if (repository.exists(definition.id())) {
            notifier.send(
                    actor,
                    KitError.ALREADY_EXISTS.messageKey(),
                    Map.of("kit", definition.id().value()));
            return Result.err(KitError.ALREADY_EXISTS);
        }
        repository.save(definition);
        notifier.send(
                actor, KitsMessageKey.KIT_CREATED, Map.of("kit", definition.id().value()));
        return Result.ok();
    }
}
