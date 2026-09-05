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
 * {@code /hologram action <name> set <index> …}: replace the action at an existing position with a new one,
 * keeping the rest of the chain. {@code index1Based} is the 1-based position the operator sees in
 * {@code /hologram action <name> list}. A name no hologram exists at is rejected with
 * {@link HologramError#NOT_FOUND}; an index outside the current chain with
 * {@link HologramError#ACTION_INDEX_OUT_OF_RANGE}. The operator-only permission is enforced at the command gate.
 */
public final class SetHologramAction {

    private final HologramRepository repository;
    private final Notifier notifier;

    public SetHologramAction(HologramRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Replace the {@code index1Based}-th action of hologram {@code name} with {@code action}, or reject. */
    public Result<Unit, HologramError> set(PlayerRef actor, HologramName name, int index1Based, ClickAction action) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(action, "action");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        Hologram hologram = existing.get();
        int zeroBased = index1Based - 1;
        if (zeroBased < 0 || zeroBased >= hologram.actions().size()) {
            notifier.send(
                    actor,
                    HologramError.ACTION_INDEX_OUT_OF_RANGE.messageKey(),
                    Map.of("name", name.value(), "index", Integer.toString(index1Based)));
            return Result.err(HologramError.ACTION_INDEX_OUT_OF_RANGE);
        }
        repository.save(hologram.withActionSetAt(zeroBased, action));
        notifier.send(
                actor,
                HologramsMessageKey.HOLOGRAM_ACTION_SET,
                Map.of("name", name.value(), "index", Integer.toString(index1Based)));
        return Result.ok();
    }
}
