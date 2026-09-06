package com.uxplima.uxmessentials.holograms.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DirectTeleporter;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /hologram teleport <name>}: send the operator to a hologram's location, the inverse of {@code
 * movehere}. The hologram is resolved here and its location handed to the {@link DirectTeleporter} port, which
 * performs the region-aware async hop. A name no hologram exists at is rejected with
 * {@link HologramError#NOT_FOUND}. The operator-only permission is enforced at the command gate.
 */
public final class TeleportToHologram {

    private final HologramRepository repository;
    private final DirectTeleporter teleporter;
    private final Notifier notifier;

    public TeleportToHologram(HologramRepository repository, DirectTeleporter teleporter, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Send {@code actor} to the hologram {@code name}, or reject when no such hologram exists. */
    public Result<Unit, HologramError> teleport(PlayerRef actor, HologramName name) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        teleporter.teleport(actor, existing.get().location());
        notifier.send(actor, HologramsMessageKey.HOLOGRAM_TELEPORTED, Map.of("name", name.value()));
        return Result.ok();
    }
}
