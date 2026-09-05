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
 * {@code /npc cooldown <name> <millis|default>}: set an NPC's per-click interaction cooldown in milliseconds, or
 * reset it to the module-wide default ({@code 0}). This changes only the click-debounce; nothing visual changes,
 * so it saves the snapshot without re-rendering. The millis arrive already validated (non-negative) at the adapter
 * boundary. A name no NPC exists at is rejected with {@link NpcError#NOT_FOUND}. The operator-only permission is
 * enforced at the command gate.
 */
public final class SetNpcInteractionCooldown {

    private final NpcRepository repository;
    private final Notifier notifier;

    public SetNpcInteractionCooldown(NpcRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Set the NPC {@code name}'s click cooldown to {@code millis} ({@code 0} = module default), or reject if absent. */
    public Result<Unit, NpcError> setCooldown(PlayerRef actor, NpcName name, long millis) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Npc updated = existing.get().withInteractionCooldownMillis(Math.max(0L, millis));
        repository.save(updated);
        if (updated.interactionCooldownMillis() == 0L) {
            notifier.send(actor, NpcMessageKey.NPC_COOLDOWN_DEFAULT, Map.of("name", name.value()));
        } else {
            notifier.send(
                    actor,
                    NpcMessageKey.NPC_COOLDOWN_SET,
                    Map.of("name", name.value(), "millis", Long.toString(updated.interactionCooldownMillis())));
        }
        return Result.ok();
    }
}
