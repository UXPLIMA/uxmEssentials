package com.uxplima.uxmessentials.npc.application;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.EquipmentSlot;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /npc equip <name> list}: print the slots an NPC wears and the stored item token for each, or the empty
 * notice when it wears nothing. The token is shown verbatim (a material name reads cleanly; a serialized
 * full-item payload is truncated to a bounded length so the line never floods chat). A name no NPC exists at is
 * rejected with {@link NpcError#NOT_FOUND}. The operator-only permission is enforced at the command gate.
 */
public final class ListNpcEquipment {

    /** A material name is short; a serialized full-item token is truncated to this many characters for display. */
    private static final int MAX_TOKEN_DISPLAY = 48;

    private final NpcRepository repository;
    private final Notifier notifier;

    public ListNpcEquipment(NpcRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Print the NPC {@code name}'s worn slots to {@code actor}, or reject when no such NPC exists. */
    public Result<Unit, NpcError> list(PlayerRef actor, NpcName name) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Map<EquipmentSlot, String> equipment = existing.get().equipment();
        if (equipment.isEmpty()) {
            notifier.send(actor, NpcMessageKey.NPC_EQUIP_LIST_EMPTY, Map.of("name", name.value()));
            return Result.ok();
        }
        notifier.send(
                actor,
                NpcMessageKey.NPC_EQUIP_LIST_HEADER,
                Map.of("name", name.value(), "count", Integer.toString(equipment.size())));
        for (Map.Entry<EquipmentSlot, String> entry : equipment.entrySet()) {
            String token = entry.getValue();
            String item = token.length() <= MAX_TOKEN_DISPLAY ? token : token.substring(0, MAX_TOKEN_DISPLAY);
            notifier.send(
                    actor,
                    NpcMessageKey.NPC_EQUIP_LIST_ENTRY,
                    Map.of("slot", entry.getKey().name().toLowerCase(Locale.ROOT), "item", item));
        }
        return Result.ok();
    }
}
