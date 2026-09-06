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
 * {@code /npc action add_before|add_after <name> <index> …}: insert one action into an NPC's chain relative to an
 * existing position. {@code index1Based} is the 1-based position the operator sees in {@code /npc action list};
 * {@code after} chooses whether the new action lands just after it ({@code true}) or just before it
 * ({@code false}). A name no NPC exists at is rejected with {@link NpcError#NOT_FOUND}; an index outside the
 * current list with {@link NpcError#ACTION_INDEX_INVALID} (an empty chain has no valid position, use plain
 * {@code add}). The operator-only permission is enforced at the command gate.
 */
public final class InsertNpcAction {

    private final NpcRepository repository;
    private final Notifier notifier;

    public InsertNpcAction(NpcRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Insert {@code action} before/after the {@code index1Based}-th action of NPC {@code name}. */
    public Result<Unit, NpcError> insert(
            PlayerRef actor, NpcName name, int index1Based, boolean after, ClickAction action) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(action, "action");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Npc npc = existing.get();
        int size = npc.actions().size();
        if (index1Based < 1 || index1Based > size) {
            notifier.send(
                    actor,
                    NpcError.ACTION_INDEX_INVALID.messageKey(),
                    Map.of("name", name.value(), "index", Integer.toString(index1Based)));
            return Result.err(NpcError.ACTION_INDEX_INVALID);
        }
        int position = after ? index1Based : index1Based - 1;
        repository.save(npc.withActionInsertedAt(position, action));
        notifier.send(
                actor,
                NpcMessageKey.NPC_ACTION_INSERTED,
                Map.of("name", name.value(), "index", Integer.toString(position + 1)));
        return Result.ok();
    }
}
