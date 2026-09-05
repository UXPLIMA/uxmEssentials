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
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;

/**
 * {@code /npc action set <name> <index> …}: replace the action at an existing position with a new one, keeping
 * the rest of the chain. {@code index1Based} is the 1-based position the operator sees in {@code /npc action
 * list}. A name no NPC exists at is rejected with {@link NpcError#NOT_FOUND}; an index outside the current list
 * with {@link NpcError#ACTION_INDEX_INVALID}. The operator-only permission is enforced at the command gate.
 */
public final class SetNpcAction {

    private final NpcRepository repository;
    private final Notifier notifier;

    public SetNpcAction(NpcRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Replace the {@code index1Based}-th action of NPC {@code name} with {@code action}, or reject out of range. */
    public Result<Unit, NpcError> set(PlayerRef actor, NpcName name, int index1Based, ClickAction action) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(action, "action");
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
        repository.save(npc.withActionSetAt(zeroBased, action));
        notifier.send(
                actor,
                NpcMessageKey.NPC_ACTION_SET,
                Map.of("name", name.value(), "index", Integer.toString(index1Based)));
        return Result.ok();
    }
}
