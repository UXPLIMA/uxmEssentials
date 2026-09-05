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
 * {@code /npc action remove <name> <index>}: drop one action from an NPC's action list and save. The index is
 * 1-based as the operator sees it in {@code /npc action list}; it is converted to the 0-based list position here.
 * A name no NPC exists at is rejected with {@link NpcError#NOT_FOUND}; an index outside the current list with
 * {@link NpcError#ACTION_INDEX_INVALID}. The operator-only permission is enforced at the command gate.
 */
public final class RemoveNpcAction {

    private final NpcRepository repository;
    private final Notifier notifier;

    public RemoveNpcAction(NpcRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Remove the {@code index1Based}-th action of NPC {@code name}, or reject if absent / out of range. */
    public Result<Unit, NpcError> remove(PlayerRef actor, NpcName name, int index1Based) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Npc npc = existing.get();
        int zeroBased = index1Based - 1;
        if (zeroBased < 0 || zeroBased >= npc.actions().size()) {
            notifier.send(
                    actor,
                    NpcError.ACTION_INDEX_INVALID.messageKey(),
                    Map.of("name", name.value(), "index", Integer.toString(index1Based)));
            return Result.err(NpcError.ACTION_INDEX_INVALID);
        }
        repository.save(npc.withActionRemovedAt(zeroBased));
        notifier.send(
                actor,
                NpcMessageKey.NPC_ACTION_REMOVED,
                Map.of("name", name.value(), "index", Integer.toString(index1Based)));
        return Result.ok();
    }
}
