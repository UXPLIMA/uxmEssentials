package com.uxplima.uxmessentials.holograms.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;

/**
 * {@code /hologram action <name> add <trigger> <type> <value…>}: append one typed {@link ClickAction} to the end
 * of a hologram's action chain and save the new snapshot. A name no hologram exists at is rejected with
 * {@link HologramError#NOT_FOUND}. Appending does not change the rendered text. The click listener reads the
 * action chain from the repository when the hologram is clicked, so there is no re-render. The operator-only
 * permission is enforced at the command gate.
 */
public final class AddHologramAction {

    private final HologramRepository repository;
    private final Notifier notifier;

    public AddHologramAction(HologramRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Append {@code action} to the hologram {@code name}'s action chain, or reject if no such hologram exists. */
    public Result<Unit, HologramError> add(PlayerRef actor, HologramName name, ClickAction action) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(action, "action");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        Hologram updated = existing.get().withActionAdded(action);
        repository.save(updated);
        notifier.send(
                actor,
                HologramsMessageKey.HOLOGRAM_ACTION_ADDED,
                Map.of(
                        "name",
                        name.value(),
                        "trigger",
                        action.trigger().name(),
                        "type",
                        action.type().name(),
                        "index",
                        Integer.toString(updated.actions().size())));
        return Result.ok();
    }
}
