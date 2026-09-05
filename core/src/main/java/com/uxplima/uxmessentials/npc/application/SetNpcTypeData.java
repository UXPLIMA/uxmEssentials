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
import org.jspecify.annotations.Nullable;

/**
 * {@code /npc data <name> set|clear <key> [value]}: set or clear one per-entity-type appearance datum on an
 * existing NPC, save the new snapshot, and re-render so every viewer sees it at once. The key arrives already
 * validated at the adapter boundary (a known data key), and a {@code null}/blank value clears the key; this use
 * case owns only the not-found decision and the persistence. A name no NPC exists at is rejected with
 * {@link NpcError#NOT_FOUND}. The operator-only permission is enforced at the command gate.
 */
public final class SetNpcTypeData {

    private final NpcRepository repository;
    private final NpcView view;
    private final Notifier notifier;

    public SetNpcTypeData(NpcRepository repository, NpcView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Set {@code key} to {@code value} on NPC {@code name} (clearing it when {@code value} is null/blank), or reject. */
    public Result<Unit, NpcError> set(PlayerRef actor, NpcName name, String key, @Nullable String value) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(key, "key");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Npc updated = existing.get().withTypeData(key, value);
        repository.save(updated);
        view.render(updated);
        boolean cleared = value == null || value.isBlank();
        notifier.send(
                actor,
                cleared ? NpcMessageKey.NPC_DATA_CLEARED : NpcMessageKey.NPC_DATA_SET,
                cleared
                        ? Map.of("name", name.value(), "key", key)
                        : Map.of("name", name.value(), "key", key, "value", value));
        return Result.ok();
    }
}
