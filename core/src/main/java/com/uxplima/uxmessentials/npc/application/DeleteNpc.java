package com.uxplima.uxmessentials.npc.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.NpcView;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.event.NpcDeleted;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /npc delete <name>}: remove a server-wide NPC, despawn its fake player from every viewer, and free its
 * name for reuse. A name no NPC exists at is rejected with {@link NpcError#NOT_FOUND}; a successful delete
 * removes the row, despawns the fake player, and publishes {@code NpcDeleted} attributed to the staff member who
 * ran the command. The operator-only permission is enforced at the command gate.
 */
public final class DeleteNpc {

    private final NpcRepository repository;
    private final NpcView view;
    private final Notifier notifier;
    private final DomainEventPublisher events;

    public DeleteNpc(NpcRepository repository, NpcView view, Notifier notifier, DomainEventPublisher events) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** Delete the NPC {@code name}, or reject when no such NPC exists. */
    public Result<Unit, NpcError> delete(PlayerRef actor, NpcName name) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        if (!repository.exists(name)) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        repository.delete(name);
        view.despawn(name);
        events.publish(new NpcDeleted(name, actor));
        notifier.send(actor, NpcMessageKey.NPC_DELETED, Map.of("name", name.value()));
        return Result.ok();
    }
}
