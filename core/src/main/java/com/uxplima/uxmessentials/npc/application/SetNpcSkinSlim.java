package com.uxplima.uxmessentials.npc.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.NpcView;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /npc skinslim <name> <true|false>}: set the body model of an NPC's skin to slim (Alex, three-pixel arms)
 * or classic (Steve, four-pixel arms). The variant is a property of the skin, so an NPC with no skin set is
 * rejected with {@link NpcError#NOT_FOUND}-style feedback via {@link NpcMessageKey#NPC_SKIN_SLIM_NO_SKIN}; a
 * non-player NPC is rejected with {@link NpcError#SKIN_ONLY_PLAYER}. The new snapshot is saved and re-rendered so
 * the arms switch at once. A name no NPC exists at is rejected with {@link NpcError#NOT_FOUND}. The operator-only
 * permission is enforced at the command gate.
 */
public final class SetNpcSkinSlim {

    private final NpcRepository repository;
    private final NpcView view;
    private final Notifier notifier;

    public SetNpcSkinSlim(NpcRepository repository, NpcView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Set the NPC {@code name}'s skin model to slim ({@code true}) or classic ({@code false}), or reject if unable. */
    public Result<Unit, NpcError> setSlim(PlayerRef actor, NpcName name, boolean slim) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Npc base = existing.get();
        if (!base.isPlayerType()) {
            notifier.send(actor, NpcError.SKIN_ONLY_PLAYER.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.SKIN_ONLY_PLAYER);
        }
        NpcSkin skin = base.skin();
        if (skin == null) {
            notifier.send(actor, NpcMessageKey.NPC_SKIN_SLIM_NO_SKIN, Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Npc updated = base.withSkin(skin.withSlim(slim));
        repository.save(updated);
        view.render(updated);
        notifier.send(
                actor,
                slim ? NpcMessageKey.NPC_SKIN_SLIM_SET : NpcMessageKey.NPC_SKIN_SLIM_CLASSIC,
                Map.of("name", name.value()));
        return Result.ok();
    }
}
