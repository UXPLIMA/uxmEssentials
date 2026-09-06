package com.uxplima.uxmessentials.npc.application;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /npc info <name>}: print an NPC's stored properties to the operator, its location, appearance (type,
 * skin + slim, glow, pose, scale), the boolean state flags, the per-NPC ranges + cooldown, and its behaviour
 * (display name, click command, look, action count). As one header followed by structured {@link NpcMessageKey}
 * entries whose values interpolate the data. A name no NPC exists at is rejected with {@link NpcError#NOT_FOUND}.
 * The operator-only permission is enforced at the command gate.
 */
public final class DescribeNpc {

    private static final String DEFAULT = "default";

    private final NpcRepository repository;
    private final Notifier notifier;

    public DescribeNpc(NpcRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Print every property of the NPC {@code name} to {@code viewer}, or reject when no such NPC exists. */
    public Result<Unit, NpcError> describe(PlayerRef viewer, NpcName name) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(name, "name");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(viewer, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        emit(viewer, existing.get());
        return Result.ok();
    }

    private void emit(PlayerRef viewer, Npc npc) {
        notifier.send(
                viewer, NpcMessageKey.NPC_INFO_HEADER, Map.of("name", npc.name().value()));
        notifier.send(viewer, NpcMessageKey.NPC_INFO_LOCATION, location(npc.location()));
        notifier.send(viewer, NpcMessageKey.NPC_INFO_APPEARANCE, appearance(npc));
        notifier.send(viewer, NpcMessageKey.NPC_INFO_FLAGS, flags(npc));
        notifier.send(viewer, NpcMessageKey.NPC_INFO_RANGES, ranges(npc));
        notifier.send(viewer, NpcMessageKey.NPC_INFO_BEHAVIOR, behavior(npc));
    }

    private static Map<String, String> location(Position at) {
        return Map.of(
                "world", at.world().name(),
                "x", String.format(Locale.ROOT, "%.2f", at.x()),
                "y", String.format(Locale.ROOT, "%.2f", at.y()),
                "z", String.format(Locale.ROOT, "%.2f", at.z()),
                "yaw", String.format(Locale.ROOT, "%.1f", at.yaw()));
    }

    private static Map<String, String> appearance(Npc npc) {
        NpcSkin skin = npc.skin();
        String glowColor = npc.glowColor();
        return Map.of(
                "type", npc.entityType(),
                "skin", Boolean.toString(skin != null),
                "slim", Boolean.toString(skin != null && skin.slim()),
                "glow", Boolean.toString(npc.glowing()),
                "color", glowColor == null ? "" : glowColor,
                "pose", npc.pose(),
                "scale", String.format(Locale.ROOT, "%.2f", npc.scale()));
    }

    private static Map<String, String> flags(Npc npc) {
        return Map.of(
                "collidable", Boolean.toString(npc.collidable()),
                "tab", Boolean.toString(npc.showInTab()),
                "mirror", Boolean.toString(npc.mirrorSkin()),
                "onfire", Boolean.toString(npc.onFire()),
                "invisible", Boolean.toString(npc.invisible()),
                "silent", Boolean.toString(npc.silent()));
    }

    private static Map<String, String> ranges(Npc npc) {
        Double view = npc.viewDistance();
        Double turn = npc.turnDistance();
        long cooldown = npc.interactionCooldownMillis();
        return Map.of(
                "view", view == null ? DEFAULT : String.format(Locale.ROOT, "%.1f", view),
                "turn", turn == null ? DEFAULT : String.format(Locale.ROOT, "%.1f", turn),
                "cooldown", cooldown == 0 ? DEFAULT : Long.toString(cooldown));
    }

    private static Map<String, String> behavior(Npc npc) {
        String displayName = npc.displayName();
        String command = npc.clickCommand();
        return Map.of(
                "displayname",
                displayName == null ? "" : displayName,
                "command",
                command == null ? "" : command,
                "look",
                Boolean.toString(npc.lookAtPlayer()),
                "actions",
                Integer.toString(npc.actions().size()));
    }
}
