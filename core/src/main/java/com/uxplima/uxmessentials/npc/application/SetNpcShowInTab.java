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
 * {@code /npc showintab <name> <true|false>}: toggle whether an NPC stays a tab-list entry (the default hides it
 * after spawn). The new snapshot is saved and re-rendered so the tab visibility changes at once. A name no NPC
 * exists at is rejected with {@link NpcError#NOT_FOUND}. The operator-only permission is enforced at the command
 * gate.
 */
public final class SetNpcShowInTab {

    private final NpcRepository repository;
    private final NpcView view;
    private final Notifier notifier;

    public SetNpcShowInTab(NpcRepository repository, NpcView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Set the NPC {@code name}'s show-in-tab flag to {@code show}, or reject when no such NPC exists. */
    public Result<Unit, NpcError> setShowInTab(PlayerRef actor, NpcName name, boolean show) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Npc updated = existing.get().withShowInTab(show);
        repository.save(updated);
        view.render(updated);
        notifier.send(
                actor,
                show ? NpcMessageKey.NPC_SHOW_IN_TAB_ENABLED : NpcMessageKey.NPC_SHOW_IN_TAB_DISABLED,
                Map.of("name", name.value()));
        return Result.ok();
    }
}
