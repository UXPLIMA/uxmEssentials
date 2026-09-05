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
 * {@code /npc pose <name> <pose>}: freeze an existing NPC in a body pose, save the new snapshot, and re-render so
 * every viewer sees the new pose at once. The pose arrives already validated at the adapter boundary (a known
 * pose name, upper-cased); this use case owns only the not-found decision and the persistence. A name no NPC
 * exists at is rejected with {@link NpcError#NOT_FOUND}. The operator-only permission is enforced at the command
 * gate.
 */
public final class SetNpcPose {

    private final NpcRepository repository;
    private final NpcView view;
    private final Notifier notifier;

    public SetNpcPose(NpcRepository repository, NpcView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Freeze the NPC {@code name} in {@code pose}, or reject when no such NPC exists. */
    public Result<Unit, NpcError> setPose(PlayerRef actor, NpcName name, String pose) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(pose, "pose");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Npc posed = existing.get().withPose(pose);
        repository.save(posed);
        view.render(posed);
        notifier.send(actor, NpcMessageKey.NPC_POSE_SET, Map.of("name", name.value(), "pose", posed.pose()));
        return Result.ok();
    }
}
