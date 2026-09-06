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
 * {@code /npc data <name> list}: show an NPC's per-entity-type appearance metadata as key/value rows. No new
 * query is needed: the NPC is loaded and its {@code typeData()} read. A name no NPC exists at is rejected with
 * {@link NpcError#NOT_FOUND}; an NPC with no metadata gets the empty notice. The header / per-entry / none
 * feedback is pushed through the notifier so all text resolves from {@link NpcMessageKey}.
 */
public final class ListNpcTypeData {

    private final NpcRepository repository;
    private final Notifier notifier;

    public ListNpcTypeData(NpcRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Push the metadata of NPC {@code name} (header + entries, or the empty notice), or reject if absent. */
    public Result<Unit, NpcError> list(PlayerRef viewer, NpcName name) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(name, "name");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(viewer, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Map<String, String> data = existing.get().typeData();
        if (data.isEmpty()) {
            notifier.send(viewer, NpcMessageKey.NPC_DATA_NONE, Map.of("name", name.value()));
            return Result.ok();
        }
        notifier.send(
                viewer,
                NpcMessageKey.NPC_DATA_LIST_HEADER,
                Map.of("name", name.value(), "count", Integer.toString(data.size())));
        for (Map.Entry<String, String> entry : data.entrySet()) {
            notifier.send(
                    viewer,
                    NpcMessageKey.NPC_DATA_LIST_ENTRY,
                    Map.of("key", entry.getKey(), "value", entry.getValue()));
        }
        return Result.ok();
    }
}
