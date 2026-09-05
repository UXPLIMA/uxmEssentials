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
 * {@code /npc type <name> <ENTITY_TYPE>}: change which entity an existing NPC renders as, save the new snapshot,
 * and re-render so every viewer sees the new type. A name no NPC exists at is rejected with
 * {@link NpcError#NOT_FOUND}. The entity type arrives already validated at the adapter boundary (a real, living
 * Bukkit type, upper-cased); this use case owns only the not-found decision and the persistence. The skin is
 * preserved across the change, so flipping a mob back to {@code PLAYER} restores its skin. The operator-only
 * permission is enforced at the command gate.
 */
public final class SetNpcEntityType {

    private final NpcRepository repository;
    private final NpcView view;
    private final Notifier notifier;

    public SetNpcEntityType(NpcRepository repository, NpcView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Re-type the NPC {@code name} to {@code entityType}, or reject when no such NPC exists. */
    public Result<Unit, NpcError> setEntityType(PlayerRef actor, NpcName name, String entityType) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(entityType, "entityType");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Npc retyped = existing.get().withEntityType(entityType);
        repository.save(retyped);
        view.render(retyped);
        notifier.send(actor, NpcMessageKey.NPC_TYPE_SET, Map.of("name", name.value(), "type", retyped.entityType()));
        return Result.ok();
    }
}
