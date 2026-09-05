package com.uxplima.uxmessentials.npc.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.NpcView;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.event.NpcCreated;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /npc copy <npc> <new-name>}: duplicate an existing NPC under a new name at the operator's current
 * position, carrying every appearance and behaviour setting (skin, type, equipment, glow, pose, scale, type-data,
 * click command, look, actions, …) across unchanged. The source name must exist ({@link NpcError#NOT_FOUND}) and
 * the new name must be free ({@link NpcError#NAME_TAKEN}). The clone is a brand-new NPC created now, so it gets its
 * own creation time and is spawned + {@code NpcCreated}-published like any create. The operator-only permission is
 * enforced at the command gate.
 */
public final class CopyNpc {

    private final NpcRepository repository;
    private final NpcView view;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public CopyNpc(
            NpcRepository repository, NpcView view, Notifier notifier, DomainEventPublisher events, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Clone NPC {@code source} to {@code target} at {@code at}, or reject a missing source / a taken target. */
    public Result<Unit, NpcError> copy(PlayerRef actor, NpcName source, NpcName target, Position at) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(at, "at");
        Optional<Npc> original = repository.find(source);
        if (original.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", source.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        if (repository.exists(target)) {
            notifier.send(actor, NpcError.NAME_TAKEN.messageKey(), Map.of("name", target.value()));
            return Result.err(NpcError.NAME_TAKEN);
        }
        Npc clone = new Npc(
                target, at, original.get().appearance(), original.get().behavior(), clock.instant(), actor.uuid());
        repository.save(clone);
        view.render(clone);
        events.publish(new NpcCreated(target, actor, at));
        notifier.send(actor, NpcMessageKey.NPC_COPIED, Map.of("source", source.value(), "name", target.value()));
        return Result.ok();
    }
}
