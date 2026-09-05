package com.uxplima.uxmessentials.npc.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.NpcView;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /npc fix <npc>}: force a clean re-spawn of an existing NPC for every viewer — despawn it, then render it
 * again — to recover from a client-side desync (a ghost or a missing fake entity) without editing the stored
 * model. A name no NPC exists at is rejected with {@link NpcError#NOT_FOUND}. The operator-only permission is
 * enforced at the command gate.
 */
public final class FixNpc {

    private final NpcRepository repository;
    private final NpcView view;
    private final Notifier notifier;

    public FixNpc(NpcRepository repository, NpcView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Re-spawn the NPC {@code name} for every viewer, or reject when no such NPC exists. */
    public Result<Unit, NpcError> fix(PlayerRef actor, NpcName name) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        view.despawn(name);
        view.render(existing.get());
        notifier.send(actor, NpcMessageKey.NPC_FIXED, Map.of("name", name.value()));
        return Result.ok();
    }
}
