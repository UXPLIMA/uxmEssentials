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
import org.jspecify.annotations.Nullable;

/**
 * {@code /npc command <name> <command…>}: bind (or, with a blank command, clear) the command an NPC runs when a
 * player clicks it, and save the new snapshot. A name no NPC exists at is rejected with
 * {@link NpcError#NOT_FOUND}. Binding does not touch the rendering, the fake player looks the same, so no
 * re-render is needed; the interaction listener reads the bound command from the repository when the NPC is
 * clicked. The operator-only permission is enforced at the command gate.
 */
public final class SetNpcClickCommand {

    private final NpcRepository repository;
    private final Notifier notifier;

    public SetNpcClickCommand(NpcRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Bind the click command of NPC {@code name} to {@code command} (blank clears it), or reject if absent. */
    public Result<Unit, NpcError> setCommand(PlayerRef actor, NpcName name, @Nullable String command) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        String trimmed = command == null ? null : command.strip();
        boolean cleared = trimmed == null || trimmed.isEmpty();
        Npc rebound = existing.get().withClickCommand(cleared ? null : trimmed);
        repository.save(rebound);
        NpcMessageKey feedback = cleared ? NpcMessageKey.NPC_COMMAND_CLEARED : NpcMessageKey.NPC_COMMAND_SET;
        notifier.send(actor, feedback, Map.of("name", name.value()));
        return Result.ok();
    }
}
