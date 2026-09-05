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
 * {@code /npc center <npc>}: snap an existing NPC to the centre of its current block on the horizontal plane
 * ({@code x+0.5}, {@code z+0.5}), keeping its Y and facing, so a hand-placed NPC sits flush in its cell rather than
 * against an edge. Backed by {@link Position#atBlockCenter()}. A name no NPC exists at is rejected with
 * {@link NpcError#NOT_FOUND}; {@code NpcMoved} is published so a linked hologram re-anchors. The operator-only
 * permission is enforced at the command gate.
 */
public final class CenterNpc {

    private final NpcRepository repository;
    private final NpcView view;
    private final Notifier notifier;
    private final DomainEventPublisher events;

    public CenterNpc(NpcRepository repository, NpcView view, Notifier notifier, DomainEventPublisher events) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** Centre the NPC {@code name} in its block, or reject when no such NPC exists. */
    public Result<Unit, NpcError> center(PlayerRef actor, NpcName name) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Position centered = existing.get().location().atBlockCenter();
        Npc moved = existing.get().movedTo(centered);
        repository.save(moved);
        view.render(moved);
        events.publish(new NpcMoved(name, centered));
        notifier.send(actor, NpcMessageKey.NPC_CENTERED, Map.of("name", name.value()));
        return Result.ok();
    }
}
