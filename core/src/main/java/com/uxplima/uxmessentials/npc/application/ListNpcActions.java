package com.uxplima.uxmessentials.npc.application;

import java.util.List;
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
 * {@code /npc action list <name>}: show an NPC's action chain in run order, 1-based, with each action's trigger,
 * type and value. No new query is needed: the NPC is loaded and its {@code actions()} read. A name no NPC exists
 * at is rejected with {@link NpcError#NOT_FOUND}; an NPC with no actions gets the empty notice. The header /
 * per-entry / none feedback is pushed through the notifier so all text resolves from {@link NpcMessageKey}.
 */
public final class ListNpcActions {

    private final NpcRepository repository;
    private final Notifier notifier;

    public ListNpcActions(NpcRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Push the action chain of NPC {@code name} (header + entries, or the empty notice), or reject if absent. */
    public Result<Unit, NpcError> list(PlayerRef viewer, NpcName name) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(name, "name");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(viewer, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        List<ClickAction> actions = existing.get().actions();
        if (actions.isEmpty()) {
            notifier.send(viewer, NpcMessageKey.NPC_ACTION_NONE, Map.of("name", name.value()));
            return Result.ok();
        }
        notifier.send(
                viewer,
                NpcMessageKey.NPC_ACTION_LIST_HEADER,
                Map.of("name", name.value(), "count", Integer.toString(actions.size())));
        for (int index = 0; index < actions.size(); index++) {
            ClickAction action = actions.get(index);
            notifier.send(
                    viewer,
                    NpcMessageKey.NPC_ACTION_LIST_ENTRY,
                    Map.of(
                            "index",
                            Integer.toString(index + 1),
                            "trigger",
                            action.trigger().name(),
                            "type",
                            action.type().name(),
                            "value",
                            action.value()));
        }
        return Result.ok();
    }
}
