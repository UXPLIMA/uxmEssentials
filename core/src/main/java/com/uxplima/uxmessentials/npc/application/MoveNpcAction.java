package com.uxplima.uxmessentials.npc.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /npc action move_up|move_down <name> <index>}: reorder an NPC's action chain by swapping one action with
 * its neighbour. {@code index1Based} is the 1-based position the operator sees in {@code /npc action list};
 * {@code up} moves it one earlier, otherwise one later. A name no NPC exists at is rejected with
 * {@link NpcError#NOT_FOUND}; a move past either end (the first up, the last down) or an out-of-range index with
 * {@link NpcError#ACTION_INDEX_INVALID}. The operator-only permission is enforced at the command gate.
 */
public final class MoveNpcAction {

    private final NpcRepository repository;
    private final Notifier notifier;

    public MoveNpcAction(NpcRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Move the {@code index1Based}-th action of NPC {@code name} one step {@code up} or down, or reject. */
    public Result<Unit, NpcError> move(PlayerRef actor, NpcName name, int index1Based, boolean up) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Npc npc = existing.get();
        int from = index1Based - 1;
        int to = up ? from - 1 : from + 1;
        int size = npc.actions().size();
        if (from < 0 || from >= size || to < 0 || to >= size) {
            notifier.send(
                    actor,
                    NpcError.ACTION_INDEX_INVALID.messageKey(),
                    Map.of("name", name.value(), "index", Integer.toString(index1Based)));
            return Result.err(NpcError.ACTION_INDEX_INVALID);
        }
        repository.save(npc.withActionMoved(from, to));
        notifier.send(
                actor,
                NpcMessageKey.NPC_ACTION_MOVED,
                Map.of("name", name.value(), "from", Integer.toString(index1Based), "to", Integer.toString(to + 1)));
        return Result.ok();
    }
}
