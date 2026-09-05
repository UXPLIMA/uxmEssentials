package com.uxplima.uxmessentials.holograms.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.application.port.LinkedNpcLocator;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /hologram linknpc <name> <npc>}: link an existing hologram to an NPC so it floats above that NPC and
 * follows it as the NPC moves (the link-with-NPC feature). The link is stored on the aggregate and
 * the live entity is re-rendered at once, so the hologram jumps to the NPC immediately rather than waiting for the
 * NPC's next move. A name no hologram exists at is rejected with {@link HologramError#NOT_FOUND}; a name no NPC
 * exists at is rejected with a friendly "no such NPC" reply (no link is written), checked through the
 * {@link LinkedNpcLocator} so the use case never depends on the npc context. The operator-only permission is
 * enforced at the command gate.
 */
public final class LinkHologramToNpc {

    private final HologramRepository repository;
    private final HologramView view;
    private final Notifier notifier;
    private final LinkedNpcLocator npcs;

    public LinkHologramToNpc(
            HologramRepository repository, HologramView view, Notifier notifier, LinkedNpcLocator npcs) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.npcs = Objects.requireNonNull(npcs, "npcs");
    }

    /** Link the hologram {@code name} to the NPC {@code npcName}, or reject when either does not exist. */
    public Result<Unit, HologramError> link(PlayerRef actor, HologramName name, String npcName) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(npcName, "npcName");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        if (npcs.locate(npcName).isEmpty()) {
            notifier.send(actor, HologramsMessageKey.HOLOGRAM_NPC_NOT_FOUND, Map.of("npc", npcName));
            return Result.err(HologramError.NOT_FOUND);
        }
        Hologram linked = existing.get().linkedTo(npcName);
        repository.save(linked);
        view.render(linked);
        notifier.send(actor, HologramsMessageKey.HOLOGRAM_LINKED, Map.of("name", name.value(), "npc", npcName));
        return Result.ok();
    }
}
