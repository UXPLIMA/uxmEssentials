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
 * {@code /npc action clear <name>}: remove every action from an NPC's action list and save. A name no NPC exists
 * at is rejected with {@link NpcError#NOT_FOUND}. Clearing leaves the single {@code clickCommand} untouched (it
 * is the separate, simpler mechanism). The operator-only permission is enforced at the command gate.
 */
public final class ClearNpcActions {

    private final NpcRepository repository;
    private final Notifier notifier;

    public ClearNpcActions(NpcRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Clear every action of NPC {@code name}, or reject if no such NPC exists. */
    public Result<Unit, NpcError> clear(PlayerRef actor, NpcName name) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        repository.save(existing.get().withActionsCleared());
        notifier.send(actor, NpcMessageKey.NPC_ACTION_CLEARED, Map.of("name", name.value()));
        return Result.ok();
    }
}
