package com.uxplima.uxmessentials.holograms.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /hologram unlinknpc <name>}: clear an existing hologram's NPC link so it anchors to its own stored
 * location again, save the new snapshot, and re-render the live entity back at that location. A name no hologram
 * exists at is rejected with {@link HologramError#NOT_FOUND}; a hologram that is not linked is left untouched and
 * the operator told it was not linked. The operator-only permission is enforced at the command gate.
 */
public final class UnlinkHologramFromNpc {

    private final HologramRepository repository;
    private final HologramView view;
    private final Notifier notifier;

    public UnlinkHologramFromNpc(HologramRepository repository, HologramView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Unlink the hologram {@code name} from its NPC, or reject when no such hologram exists. */
    public Result<Unit, HologramError> unlink(PlayerRef actor, HologramName name) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        Hologram hologram = existing.get();
        if (!hologram.isLinked()) {
            notifier.send(actor, HologramsMessageKey.HOLOGRAM_NOT_LINKED, Map.of("name", name.value()));
            return Result.ok();
        }
        Hologram unlinked = hologram.unlinked();
        repository.save(unlinked);
        view.render(unlinked);
        notifier.send(actor, HologramsMessageKey.HOLOGRAM_UNLINKED, Map.of("name", name.value()));
        return Result.ok();
    }
}
