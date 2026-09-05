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
 * {@code /npc glow <name> <true|false> [color]}: toggle an NPC's glowing outline (and, when turning it on, its
 * colour), save the new snapshot, and re-render so the change shows at once. The colour is the name of one of the
 * sixteen chat colours, stored verbatim; the adapter maps it to the team colour at render time and falls back to
 * the default white outline when the name is unknown. Turning glow off clears nothing else. A name no NPC exists
 * at is rejected with {@link NpcError#NOT_FOUND}. The operator-only permission is enforced at the command gate.
 */
public final class SetNpcGlowing {

    private final NpcRepository repository;
    private final NpcView view;
    private final Notifier notifier;

    public SetNpcGlowing(NpcRepository repository, NpcView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Set the NPC {@code name}'s glow to {@code glowing} (with the optional {@code color}), or reject if absent. */
    public Result<Unit, NpcError> setGlowing(PlayerRef actor, NpcName name, boolean glowing, String color) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        String trimmed = color == null || color.isBlank() ? null : color.strip();
        Npc updated = existing.get().withGlowing(glowing).withGlowColor(glowing ? trimmed : null);
        repository.save(updated);
        view.render(updated);
        feedback(actor, name, glowing, trimmed);
        return Result.ok();
    }

    private void feedback(
            PlayerRef actor, NpcName name, boolean glowing, @org.jspecify.annotations.Nullable String color) {
        if (!glowing) {
            notifier.send(actor, NpcMessageKey.NPC_GLOW_DISABLED, Map.of("name", name.value()));
        } else if (color == null) {
            notifier.send(actor, NpcMessageKey.NPC_GLOW_ENABLED, Map.of("name", name.value()));
        } else {
            notifier.send(actor, NpcMessageKey.NPC_GLOW_SET, Map.of("name", name.value(), "color", color));
        }
    }
}
