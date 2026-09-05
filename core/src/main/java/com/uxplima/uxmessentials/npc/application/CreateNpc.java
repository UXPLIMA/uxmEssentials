package com.uxplima.uxmessentials.npc.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.NpcView;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcLimit;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.npc.domain.event.NpcCreated;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.Nullable;

/**
 * {@code /npc create <name>}: create a server-wide fake-player NPC at the operator's current position, wearing
 * the supplied default skin (the creator's own skin, resolved at the adapter boundary, or {@code null} when it
 * is unavailable). A name an NPC already exists under is rejected with {@link NpcError#NAME_TAKEN}; a brand-new
 * name is stored, the fake player is spawned for every eligible viewer, and {@code NpcCreated} is published. The
 * operator-only permission to run this command is enforced at the command gate; this use case owns the
 * name-taken decision and the persistence.
 */
public final class CreateNpc {

    private final NpcRepository repository;
    private final NpcView view;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;
    private final NpcQuota quota;

    public CreateNpc(
            NpcRepository repository,
            NpcView view,
            Notifier notifier,
            DomainEventPublisher events,
            Clock clock,
            NpcQuota quota) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.quota = Objects.requireNonNull(quota, "quota");
    }

    /** Create the NPC {@code name} at {@code at} with {@code skin} (may be {@code null}), or reject a taken name. */
    public Result<Unit, NpcError> create(PlayerRef creator, NpcName name, Position at, @Nullable NpcSkin skin) {
        return create(creator, name, at, skin, null);
    }

    /**
     * Create the NPC {@code name} at {@code at} with {@code skin} (may be {@code null}) and, when {@code entityType}
     * is given, rendered as that type instead of the default fake player; or reject a taken name. The type word is
     * validated at the adapter boundary before this is called.
     */
    public Result<Unit, NpcError> create(
            PlayerRef creator, NpcName name, Position at, @Nullable NpcSkin skin, @Nullable String entityType) {
        Objects.requireNonNull(creator, "creator");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(at, "at");
        if (repository.exists(name)) {
            notifier.send(creator, NpcError.NAME_TAKEN.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NAME_TAKEN);
        }
        NpcLimit limit = quota.resolve(creator);
        if (limit.isReachedAt(ownedCount(creator))) {
            notifier.send(creator, NpcError.LIMIT_REACHED.messageKey(), Map.of("limit", Integer.toString(limit.cap())));
            return Result.err(NpcError.LIMIT_REACHED);
        }
        Npc npc = Npc.create(name, at, skin, clock.instant()).withOwner(creator.uuid());
        if (entityType != null && !entityType.isBlank()) {
            npc = npc.withEntityType(entityType);
        }
        repository.save(npc);
        view.render(npc);
        events.publish(new NpcCreated(name, creator, at));
        NpcMessageKey feedback = npc.hasSkin() ? NpcMessageKey.NPC_CREATED : NpcMessageKey.NPC_CREATED_NO_SKIN;
        notifier.send(creator, feedback, Map.of("name", name.value()));
        return Result.ok();
    }

    /** How many stored NPCs {@code owner} currently owns (the cached repository keeps {@code all()} in memory). */
    private int ownedCount(PlayerRef owner) {
        return (int) repository.all().stream()
                .filter(npc -> owner.uuid().equals(npc.owner()))
                .count();
    }
}
