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
 * {@code /npc viewdistance <name> <blocks|default>} and {@code /npc turndistance <name> <blocks|default>}: set a
 * per-NPC override of the module render range (how far an NPC is shown) or look range (how far it turns to face
 * viewers), or reset it to the module default ({@code null}). Both share this one use case, branching on the
 * {@link Kind} the command supplies, since the only difference is which field is set and which message is sent. The
 * new snapshot is saved and re-rendered so the change takes effect at once. The distance arrives already validated
 * (finite, non-negative) at the adapter boundary, or {@code null} to reset. A name no NPC exists at is rejected
 * with {@link NpcError#NOT_FOUND}. The operator-only permission is enforced at the command gate.
 */
public final class SetNpcRange {

    /** Which per-NPC range the command edits — the render (view) distance or the look-at (turn) distance. */
    public enum Kind {
        VIEW,
        TURN
    }

    private final NpcRepository repository;
    private final NpcView view;
    private final Notifier notifier;

    public SetNpcRange(NpcRepository repository, NpcView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Set the NPC {@code name}'s {@code kind} distance to {@code blocks} ({@code null} = module default). */
    public Result<Unit, NpcError> setRange(PlayerRef actor, NpcName name, Kind kind, @Nullable Double blocks) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Npc base = existing.get();
        Npc updated = kind == Kind.VIEW ? base.withViewDistance(blocks) : base.withTurnDistance(blocks);
        repository.save(updated);
        view.render(updated);
        feedback(actor, name, kind, blocks);
        return Result.ok();
    }

    private void feedback(PlayerRef actor, NpcName name, Kind kind, @Nullable Double blocks) {
        boolean view = kind == Kind.VIEW;
        if (blocks == null) {
            notifier.send(
                    actor,
                    view ? NpcMessageKey.NPC_VIEW_DISTANCE_DEFAULT : NpcMessageKey.NPC_TURN_DISTANCE_DEFAULT,
                    Map.of("name", name.value()));
        } else {
            notifier.send(
                    actor,
                    view ? NpcMessageKey.NPC_VIEW_DISTANCE_SET : NpcMessageKey.NPC_TURN_DISTANCE_SET,
                    Map.of("name", name.value(), "blocks", Double.toString(blocks)));
        }
    }
}
