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
 * {@code /hologram action <name> add_before|add_after <index> …}: insert one action into a hologram's chain
 * relative to an existing position. {@code index1Based} is the 1-based position the operator sees in
 * {@code /hologram action <name> list}; {@code after} chooses whether the new action lands just after it
 * ({@code true}) or just before it ({@code false}). A name no hologram exists at is rejected with
 * {@link HologramError#NOT_FOUND}; an index outside the current chain with
 * {@link HologramError#ACTION_INDEX_OUT_OF_RANGE} (an empty chain has no valid position — use plain {@code add}).
 * The operator-only permission is enforced at the command gate.
 */
public final class InsertHologramAction {

    private final HologramRepository repository;
    private final Notifier notifier;

    public InsertHologramAction(HologramRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Insert {@code action} before/after the {@code index1Based}-th action of hologram {@code name}. */
    public Result<Unit, HologramError> insert(
            PlayerRef actor, HologramName name, int index1Based, boolean after, ClickAction action) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(action, "action");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        Hologram hologram = existing.get();
        int size = hologram.actions().size();
        if (index1Based < 1 || index1Based > size) {
            notifier.send(
                    actor,
                    HologramError.ACTION_INDEX_OUT_OF_RANGE.messageKey(),
                    Map.of("name", name.value(), "index", Integer.toString(index1Based)));
            return Result.err(HologramError.ACTION_INDEX_OUT_OF_RANGE);
        }
        int position = after ? index1Based : index1Based - 1;
        repository.save(hologram.withActionInsertedAt(position, action));
        notifier.send(
                actor,
                HologramsMessageKey.HOLOGRAM_ACTION_INSERTED,
                Map.of("name", name.value(), "index", Integer.toString(position + 1)));
        return Result.ok();
    }
}
