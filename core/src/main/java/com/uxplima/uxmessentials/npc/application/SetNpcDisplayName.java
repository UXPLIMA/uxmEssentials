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
 * {@code /npc displayname <name> <text|none>}: set the name shown above an NPC, distinct from its id. A clear word
 * ({@code -}, {@code none}, {@code clear}, {@code empty} or a blank value) hides the label entirely, and a reset word
 * ({@code default}, {@code reset}) puts the id back. The new snapshot is saved and re-rendered so the label changes at
 * once. A name no NPC exists at is rejected with {@link NpcError#NOT_FOUND}. The operator-only permission is
 * enforced at the command gate.
 */
public final class SetNpcDisplayName {

    private final NpcRepository repository;
    private final NpcView view;
    private final Notifier notifier;

    public SetNpcDisplayName(NpcRepository repository, NpcView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Set the NPC {@code name}'s shown label to {@code displayName} (or clear it when {@code null}/blank). */
    public Result<Unit, NpcError> setDisplayName(PlayerRef actor, NpcName name, @Nullable String displayName) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        // The clear words and the reset words are interpreted by the appearance itself, so every surface that sets a
        // display name (this use case, the editor GUI) resolves them the same way rather than each carrying a copy.
        Npc updated = existing.get().withDisplayName(displayName);
        repository.save(updated);
        view.render(updated);
        if (!updated.hasDisplayName()) {
            notifier.send(actor, NpcMessageKey.NPC_DISPLAY_NAME_CLEARED, Map.of("name", name.value()));
        } else {
            notifier.send(
                    actor,
                    NpcMessageKey.NPC_DISPLAY_NAME_SET,
                    Map.of("name", name.value(), "display", Objects.requireNonNull(updated.displayName())));
        }
        return Result.ok();
    }
}
