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

/**
 * {@code /hologram action <name> remove <index>}: drop one action from a hologram's action chain and save. The
 * index is 1-based as the operator sees it in {@code /hologram action <name> list}; it is converted to the 0-based
 * chain position here. A name no hologram exists at is rejected with {@link HologramError#NOT_FOUND}; an index
 * outside the current chain with {@link HologramError#ACTION_INDEX_OUT_OF_RANGE}. The operator-only permission is
 * enforced at the command gate.
 */
public final class RemoveHologramAction {

    private final HologramRepository repository;
    private final Notifier notifier;

    public RemoveHologramAction(HologramRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Remove the {@code index1Based}-th action of hologram {@code name}, or reject if absent / out of range. */
    public Result<Unit, HologramError> remove(PlayerRef actor, HologramName name, int index1Based) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
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
        repository.save(hologram.withActionRemovedAt(zeroBased));
        notifier.send(
                actor,
                HologramsMessageKey.HOLOGRAM_ACTION_REMOVED,
                Map.of("name", name.value(), "index", Integer.toString(index1Based)));
        return Result.ok();
    }
}
