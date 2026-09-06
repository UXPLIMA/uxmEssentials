package com.uxplima.uxmessentials.npc.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.NpcView;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.event.NpcMoved;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /npc moveto <name> <x> <y> <z> [yaw] [pitch]}: re-anchor an existing NPC to explicit coordinates within
 * its current world (the sibling of {@code /npc movehere}, which uses the operator's position). The world is kept
 * (coordinates do not change which world an NPC lives in) and the new snapshot is saved and re-rendered at the
 * new spot. A name no NPC exists at is rejected with {@link NpcError#NOT_FOUND}. {@code NpcMoved} is published so
 * anything pinned to the NPC (a hologram linked to it) re-anchors. The operator-only permission is enforced at
 * the adapter gate.
 */
public final class MoveNpcTo {

    private final NpcRepository repository;
    private final NpcView view;
    private final Notifier notifier;
    private final DomainEventPublisher events;

    public MoveNpcTo(NpcRepository repository, NpcView view, Notifier notifier, DomainEventPublisher events) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** Move the NPC {@code name} to {@code x,y,z} facing {@code yaw}/{@code pitch} in its world, or reject if absent. */
    public Result<Unit, NpcError> moveTo(
            PlayerRef actor, NpcName name, double x, double y, double z, float yaw, float pitch) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Npc base = existing.get();
        Position to = new Position(base.location().world(), x, y, z, yaw, pitch);
        Npc moved = base.movedTo(to);
        repository.save(moved);
        view.render(moved);
        events.publish(new NpcMoved(name, to));
        notifier.send(actor, NpcMessageKey.NPC_MOVED_TO, Map.of("name", name.value()));
        return Result.ok();
    }
}
