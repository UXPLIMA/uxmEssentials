package com.uxplima.uxmessentials.holograms.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.Visibility;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * The commands behind {@code /hologram visibility|visibilitydistance}: load the hologram, apply one
 * {@link Visibility} transition, save the new snapshot, and re-render the live entity so the change takes
 * effect immediately (the renderer recomputes its allowed-viewer set and view range from the new visibility).
 * Mirrors {@link SetHologramAppearance}: both forms pass a single-field mutation, so this one use case backs the
 * mode change ({@code ALL} / {@code PERMISSION} with its operator-chosen node) and the distance change rather
 * than two near-identical classes. A name no hologram exists at is rejected with {@link HologramError#NOT_FOUND};
 * the operator-only permission is enforced at the command gate, and the raw distance is clamped at the command
 * boundary so an invalid {@link Visibility} never reaches here.
 */
public final class SetHologramVisibility {

    private final HologramRepository repository;
    private final HologramView view;
    private final Notifier notifier;

    public SetHologramVisibility(HologramRepository repository, HologramView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Set the hologram {@code name}'s visibility mode (to everyone, or gated by a node), or reject an unknown name. */
    public Result<Unit, HologramError> setMode(PlayerRef actor, HologramName name, UnaryOperator<Visibility> mutation) {
        Objects.requireNonNull(mutation, "mutation");
        return apply(actor, name, mutation, HologramsMessageKey.HOLOGRAM_VISIBILITY_SET);
    }

    /** Set the hologram {@code name}'s visibility distance in blocks (0 = unlimited), or reject an unknown name. */
    public Result<Unit, HologramError> setDistance(PlayerRef actor, HologramName name, int blocks) {
        return apply(
                actor,
                name,
                current -> current.withDistance(Visibility.clampDistance(blocks)),
                HologramsMessageKey.HOLOGRAM_VISIBILITY_DISTANCE_SET);
    }

    private Result<Unit, HologramError> apply(
            PlayerRef actor, HologramName name, UnaryOperator<Visibility> mutation, HologramsMessageKey okKey) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        Hologram hologram = existing.get();
        Hologram retargeted = hologram.withVisibility(mutation.apply(hologram.visibility()));
        repository.save(retargeted);
        view.render(retargeted);
        notifier.send(actor, okKey, placeholders(name, retargeted.visibility()));
        return Result.ok();
    }

    private static Map<String, String> placeholders(HologramName name, Visibility visibility) {
        return Map.of(
                "name",
                name.value(),
                "mode",
                visibility.mode().name(),
                "distance",
                Integer.toString(visibility.distance()));
    }
}
