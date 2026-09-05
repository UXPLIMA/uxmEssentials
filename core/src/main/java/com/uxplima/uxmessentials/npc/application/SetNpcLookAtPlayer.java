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
 * {@code /npc lookatplayer <name> <true|false>}: toggle whether an NPC rotates to face each nearby viewer, save
 * the new snapshot, and re-render so the change takes effect at once — turning it off snaps the fake player back
 * to the fixed facing it was placed with. A name no NPC exists at is rejected with {@link NpcError#NOT_FOUND}.
 * The operator-only permission is enforced at the command gate; this use case owns the not-found decision and the
 * persistence.
 */
public final class SetNpcLookAtPlayer {

    private final NpcRepository repository;
    private final NpcView view;
    private final Notifier notifier;

    public SetNpcLookAtPlayer(NpcRepository repository, NpcView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Set whether the NPC {@code name} looks at nearby players to {@code lookAtPlayer}, or reject if absent. */
    public Result<Unit, NpcError> setLookAtPlayer(PlayerRef actor, NpcName name, boolean lookAtPlayer) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Npc updated = existing.get().withLookAtPlayer(lookAtPlayer);
        repository.save(updated);
        view.render(updated);
        NpcMessageKey feedback = lookAtPlayer ? NpcMessageKey.NPC_LOOK_ENABLED : NpcMessageKey.NPC_LOOK_DISABLED;
        notifier.send(actor, feedback, Map.of("name", name.value()));
        return Result.ok();
    }
}
