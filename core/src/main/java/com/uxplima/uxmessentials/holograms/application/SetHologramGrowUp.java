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
 * The {@code /hologram growup <name> <true|false>} command: choose whether a hologram's lines grow upward from
 * its anchor (the anchor is the bottom) or downward as usual (the anchor is the top), save, and re-render so the
 * renderer lays the lines out in the chosen direction. A purely visual positioning choice — the stored location
 * is untouched. A name no hologram exists at is rejected with {@link HologramError#NOT_FOUND}; the operator-only
 * permission is enforced at the command gate.
 */
public final class SetHologramGrowUp {

    private final HologramRepository repository;
    private final HologramView view;
    private final Notifier notifier;

    public SetHologramGrowUp(HologramRepository repository, HologramView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Set whether hologram {@code name} grows upward, or reject when no such hologram exists. */
    public Result<Unit, HologramError> set(PlayerRef actor, HologramName name, boolean growUp) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        Hologram updated = existing.get().withGrowUp(growUp);
        repository.save(updated);
        view.render(updated);
        HologramsMessageKey key =
                growUp ? HologramsMessageKey.HOLOGRAM_GROWUP_ENABLED : HologramsMessageKey.HOLOGRAM_GROWUP_DISABLED;
        notifier.send(actor, key, Map.of("name", name.value()));
        return Result.ok();
    }
}
