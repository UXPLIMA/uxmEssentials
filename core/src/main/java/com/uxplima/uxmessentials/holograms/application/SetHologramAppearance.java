package com.uxplima.uxmessentials.holograms.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.Appearance;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * The styling commands behind {@code /hologram billboard|background|shadow|brightness|scale|linewidth|viewrange}:
 * load the hologram, apply one {@link Appearance} transition, save the new snapshot, and re-render the live
 * entity so the change shows immediately. Each command form passes a single-field mutation, so this one use
 * case backs them all rather than seven near-identical classes. A name no hologram exists at is rejected with
 * {@link HologramError#NOT_FOUND}; the operator-only permission is enforced at the command gate, and the raw
 * value is parsed and clamped at the command boundary so an invalid {@link Appearance} never reaches here.
 */
public final class SetHologramAppearance {

    private final HologramRepository repository;
    private final HologramView view;
    private final Notifier notifier;

    public SetHologramAppearance(HologramRepository repository, HologramView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Apply {@code mutation} to the hologram {@code name}'s appearance, or reject when no such hologram exists. */
    public Result<Unit, HologramError> apply(PlayerRef actor, HologramName name, UnaryOperator<Appearance> mutation) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(mutation, "mutation");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        Hologram hologram = existing.get();
        Hologram restyled = hologram.withAppearance(mutation.apply(hologram.appearance()));
        repository.save(restyled);
        view.render(restyled);
        notifier.send(actor, HologramsMessageKey.HOLOGRAM_APPEARANCE_SET, Map.of("name", name.value()));
        return Result.ok();
    }
}
