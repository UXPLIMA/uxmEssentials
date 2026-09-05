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
 * {@code /hologram action <name> move_up|move_down <index>}: reorder a hologram's action chain by swapping one
 * action with its neighbour. {@code index1Based} is the 1-based position the operator sees in
 * {@code /hologram action <name> list}; {@code up} moves it one earlier, otherwise one later. A name no hologram
 * exists at is rejected with {@link HologramError#NOT_FOUND}; a move past either end (the first up, the last down)
 * or an out-of-range index with {@link HologramError#ACTION_INDEX_OUT_OF_RANGE}. The operator-only permission is
 * enforced at the command gate.
 */
public final class MoveHologramAction {

    private final HologramRepository repository;
    private final Notifier notifier;

    public MoveHologramAction(HologramRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Move the {@code index1Based}-th action of hologram {@code name} one step {@code up} or down, or reject. */
    public Result<Unit, HologramError> move(PlayerRef actor, HologramName name, int index1Based, boolean up) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        Hologram hologram = existing.get();
        int from = index1Based - 1;
        int to = up ? from - 1 : from + 1;
        int size = hologram.actions().size();
        if (from < 0 || from >= size || to < 0 || to >= size) {
            notifier.send(
                    actor,
                    HologramError.ACTION_INDEX_OUT_OF_RANGE.messageKey(),
                    Map.of("name", name.value(), "index", Integer.toString(index1Based)));
            return Result.err(HologramError.ACTION_INDEX_OUT_OF_RANGE);
        }
        repository.save(hologram.withActionMoved(from, to));
        notifier.send(
                actor,
                HologramsMessageKey.HOLOGRAM_ACTION_MOVED,
                Map.of("name", name.value(), "from", Integer.toString(index1Based), "to", Integer.toString(to + 1)));
        return Result.ok();
    }
}
