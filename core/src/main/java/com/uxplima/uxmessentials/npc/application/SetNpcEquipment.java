package com.uxplima.uxmessentials.npc.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.NpcView;
import com.uxplima.uxmessentials.npc.domain.EquipmentSlot;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /npc equip <name> <slot> <material|hand|air>}: set (or clear) the item an NPC wears in one slot, save the
 * new snapshot, and re-render so the change shows at once. The material name is stored verbatim — the adapter
 * resolves and validates it against the live registry at render time, so an unknown name simply renders an empty
 * slot. A {@code null} material clears the slot. A name no NPC exists at is rejected with {@link
 * NpcError#NOT_FOUND}. The operator-only permission is enforced at the command gate.
 */
public final class SetNpcEquipment {

    private final NpcRepository repository;
    private final NpcView view;
    private final Notifier notifier;

    public SetNpcEquipment(NpcRepository repository, NpcView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Set the NPC {@code name}'s {@code slot} to {@code materialName} (or clear it when null), or reject if absent. */
    public Result<Unit, NpcError> setEquipment(PlayerRef actor, NpcName name, EquipmentSlot slot, String materialName) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(slot, "slot");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        boolean cleared = materialName == null || materialName.isBlank();
        Npc updated = existing.get().withEquipment(slot, cleared ? null : materialName);
        repository.save(updated);
        view.render(updated);
        feedback(actor, name, slot, cleared, materialName);
        return Result.ok();
    }

    /** Clear every equipment slot of the NPC {@code name}, or reject when no such NPC exists. */
    public Result<Unit, NpcError> clearAll(PlayerRef actor, NpcName name) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Npc updated = existing.get().withEquipmentCleared();
        repository.save(updated);
        view.render(updated);
        notifier.send(actor, NpcMessageKey.NPC_EQUIP_CLEARED_ALL, Map.of("name", name.value()));
        return Result.ok();
    }

    private void feedback(PlayerRef actor, NpcName name, EquipmentSlot slot, boolean cleared, String materialName) {
        String slotLabel = slot.name().toLowerCase(java.util.Locale.ROOT);
        if (cleared) {
            notifier.send(actor, NpcMessageKey.NPC_EQUIP_CLEARED, Map.of("name", name.value(), "slot", slotLabel));
        } else {
            notifier.send(
                    actor,
                    NpcMessageKey.NPC_EQUIP_SET,
                    Map.of("name", name.value(), "slot", slotLabel, "material", materialName));
        }
    }
}
