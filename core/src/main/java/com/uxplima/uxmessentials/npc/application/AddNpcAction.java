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
 * {@code /npc action add <name> <trigger> <type> <value…>}: append one typed {@link ClickAction} to the end of an
 * NPC's action list and save the new snapshot. A name no NPC exists at is rejected with {@link
 * NpcError#NOT_FOUND}. Appending does not touch the rendering, the fake player looks the same, so no re-render
 * is needed; the interaction listener reads the action list from the repository when the NPC is clicked. The
 * operator-only permission is enforced at the command gate.
 */
public final class AddNpcAction {

    private final NpcRepository repository;
    private final Notifier notifier;

    public AddNpcAction(NpcRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Append {@code action} to the NPC {@code name}'s action list, or reject if no such NPC exists. */
    public Result<Unit, NpcError> add(PlayerRef actor, NpcName name, ClickAction action) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(action, "action");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Npc updated = existing.get().withActionAdded(action);
        repository.save(updated);
        notifier.send(
                actor,
                NpcMessageKey.NPC_ACTION_ADDED,
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
