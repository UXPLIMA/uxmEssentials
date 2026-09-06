package com.uxplima.uxmessentials.kits.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitError;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * {@code /kit show <name>}: preview a kit's contents without claiming it ({@code uxmessentials.kit.preview}).
 * The kit is resolved by id and its item list is pushed to the viewer as a header plus one entry per stack,
 * so a player can see what a kit grants before spending its cooldown (or its cost). An id no kit exists under
 * is refused with {@link KitError#NOT_FOUND}.
 *
 * <p>This never grants anything and never touches the claim/cooldown state: it is a pure read. On a
 * missing kit the not-found feedback is pushed through the notifier so it resolves from
 * {@link KitError}. On a hit the resolved definition is returned so the adapter can render the header and
 * one entry per stack: the per-item line needs the stack's material/display name, which lives in the
 * opaque {@code KitItem} payload the kernel must not parse, so only the adapter, holding the item codec,
 * can render those lines.
 */
public final class ShowKit {

    private final KitRepository repository;
    private final Notifier notifier;

    public ShowKit(KitRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Preview the kit {@code id} for {@code viewer}, or reject when no such kit exists. */
    public Result<KitDefinition, KitError> show(PlayerRef viewer, KitId id) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(id, "id");
        Optional<KitDefinition> kit = repository.find(id);
        if (kit.isEmpty()) {
            notifier.send(viewer, KitError.NOT_FOUND.messageKey(), Map.of("kit", id.value()));
            return Result.err(KitError.NOT_FOUND);
        }
        return Result.ok(kit.get());
    }
}
