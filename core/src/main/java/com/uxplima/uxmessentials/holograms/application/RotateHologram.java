package com.uxplima.uxmessentials.holograms.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.Rotation;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /hologram rotate <name> <yaw> [pitch]}: store a display rotation (yaw + pitch in degrees), save the
 * new snapshot, and re-render the live entity so the spin takes effect. A name no hologram exists at is rejected
 * with {@link HologramError#NOT_FOUND}. A stored rotation is only visually meaningful with a {@code FIXED}
 * billboard (a self-facing billboard overrides any transform); the operator sets the billboard separately. The
 * operator-only permission is enforced at the command gate.
 */
public final class RotateHologram {

    private final HologramRepository repository;
    private final HologramView view;
    private final Notifier notifier;

    public RotateHologram(HologramRepository repository, HologramView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Set the hologram {@code name}'s display rotation to {@code rotation}, or reject when no such hologram. */
    public Result<Unit, HologramError> rotate(PlayerRef actor, HologramName name, Rotation rotation) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(rotation, "rotation");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        Hologram rotated = existing.get().withRotation(rotation);
        repository.save(rotated);
        view.render(rotated);
        notifier.send(
                actor,
                HologramsMessageKey.HOLOGRAM_ROTATED,
                Map.of(
                        "name", name.value(),
                        "yaw", trim(rotation.yaw()),
                        "pitch", trim(rotation.pitch())));
        return Result.ok();
    }

    /** Render an angle without a trailing {@code .0} so a whole-degree rotation reads cleanly in feedback. */
    private static String trim(float degrees) {
        if (degrees == Math.rint(degrees)) {
            return Integer.toString((int) degrees);
        }
        return Float.toString(degrees);
    }
}
