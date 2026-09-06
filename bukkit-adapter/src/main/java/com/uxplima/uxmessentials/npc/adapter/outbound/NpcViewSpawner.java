package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.packet.npc.EquipmentSlot;
import com.uxplima.uxmlib.packet.npc.NamedColor;
import com.uxplima.uxmlib.packet.npc.NpcPackets;
import com.uxplima.uxmlib.packet.npc.NpcPose;
import com.uxplima.uxmlib.packet.tablist.TabSkin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Builds and sends the spawn packets for one (viewer, NPC) pair, branching on type. A fake player takes the
 * player path. A player-info ADD (carrying the name and skin) bundled with a spawn-player packet so they arrive
 * together; the entry is added unlisted ({@code listed=false}) so the body renders but the NPC shows no tab-list
 * row, or listed when the operator opted the NPC into the tab list, and either way the entry is kept (the
 * {@link NpcRenderer} drops it on despawn). Any other type spawns the mob through {@code spawnEntity} with no tab
 * entry and no skin. Both paths then aim the entity at its fixed facing and dress it (equipment + glow +
 * pose/scale + per-type metadata). The {@link NpcRenderer} owns which NPCs each viewer
 * has been shown and only marks a viewer shown when {@link #spawn(Player, RenderedNpc)} reports the spawn went
 * out, so a skipped bad type never leaves a phantom in the tracking map.
 *
 * <p>An unknown stored entity type resolves to nothing and is skipped (logged once, never thrown on the render
 * thread), so a bad row never spawns. The warn-once cache lives here because it is part of the type-resolution
 * concern: the renderer's 1s reconcile retries an unresolvable NPC for every viewer every tick (the skip never
 * marks the viewer shown), so an unconditional warn would flood the log. This caps it at one line per NPC per
 * distinct bad value and re-arms when the type changes. To a valid type (which renders and clears the record via
 * the {@code spawn} success path) or to a different bad value (which warns afresh). The renderer calls
 * {@link #forget(String)} / {@link #forgetAll()} on its despawn paths so a deleted NPC drops its record too.
 */
@NullMarked
public final class NpcViewSpawner {

    /** Profile names are capped at 16 chars by the protocol, so a longer NPC name is truncated for the entry. */
    private static final int MAX_PROFILE_NAME = 16;

    private final NpcPackets packets;
    private final Logger log;
    // NPC name -> the unresolvable entity-type value we already warned about, so a bad row is logged once rather
    // than every refresh tick for every viewer.
    private final Map<String, String> warnedBadType = new ConcurrentHashMap<>();

    public NpcViewSpawner(NpcPackets packets, Logger log) {
        this.packets = Objects.requireNonNull(packets, "packets");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Spawn the NPC for this viewer, branching on type. A fake player takes the player path (tab-add + spawn, the
     * entry kept either unlisted or listed, skin); any other type spawns the mob through {@code spawnEntity} with
     * no tab entry and no skin. An unknown stored type resolves to nothing and is skipped (logged, never thrown on
     * the render thread), so a bad row never spawns. Both real paths then aim the entity and dress it (equipment +
     * glow).
     *
     * @return {@code true} when the spawn went out (so the caller should mark the viewer shown), {@code false}
     *     when the stored type was unresolvable and the spawn was skipped.
     */
    boolean spawn(Player viewer, RenderedNpc rendered) {
        Npc npc = rendered.npc();
        if (npc.isPlayerType()) {
            spawnPlayerForViewer(viewer, rendered);
        } else {
            String typeKey = bukkitTypeKey(npc.entityType());
            if (typeKey == null) {
                warnBadTypeOnce(npc);
                return false;
            }
            spawnMobForViewer(viewer, rendered, typeKey);
        }
        // The type resolved (player or a real mob), so forget any earlier warning for this NPC: a fixed type
        // re-arms the warn if it ever breaks again.
        warnedBadType.remove(npc.name().value());
        Position at = npc.location();
        packets.send(viewer, packets.headLook(rendered.entityId(), at.yaw()));
        packets.send(viewer, packets.bodyLook(rendered.entityId(), at.yaw(), at.pitch()));
        applyAppearance(viewer, rendered);
        return true;
    }

    /** Drop the warned-bad-type record for a despawned NPC so a later recreate warns afresh on a fresh problem. */
    void forget(String npcName) {
        warnedBadType.remove(npcName);
    }

    /** Drop every warned-bad-type record: call on a full despawn so nothing outlives the module. */
    void forgetAll() {
        warnedBadType.clear();
    }

    private void spawnPlayerForViewer(Player viewer, RenderedNpc rendered) {
        Npc npc = rendered.npc();
        UUID profileId = rendered.profileId();
        Position at = rendered.npc().location();
        // A mirror-skin NPC wears the viewer's own skin (resolved per viewer); otherwise its stored skin. The
        // rendered name above the head is the display name when set, else the NPC id.
        TabSkin skin = npc.mirrorSkin() ? viewerSkin(viewer) : tabSkin(npc.skin());
        // The entry is added unlisted unless the operator opted the NPC into the tab list. Either way it is kept:
        // removing the player-info entry de-renders the fake player on modern clients, so the renderer drops it
        // only on despawn. An unlisted entry renders the body and skin without ever showing a tab-list row.
        Object tabAdd = packets.tabAdd(profileId, renderedName(npc), skin, npc.showInTab());
        Object spawn =
                packets.spawnPlayer(rendered.entityId(), profileId, at.x(), at.y(), at.z(), at.yaw(), at.pitch());
        packets.send(viewer, packets.bundle(List.of(tabAdd, spawn)));
    }

    private void spawnMobForViewer(Player viewer, RenderedNpc rendered, String typeKey) {
        // A mob has no tab entry and no skin: the spawn UUID is the stable per-NPC entity uuid, not a profile.
        Position at = rendered.npc().location();
        packets.send(
                viewer,
                packets.spawnEntity(
                        rendered.entityId(),
                        rendered.profileId(),
                        typeKey,
                        at.x(),
                        at.y(),
                        at.z(),
                        at.yaw(),
                        at.pitch()));
    }

    /**
     * Dress the just-spawned fake player for this viewer: send its equipment, then its glow toggle and (when the
     * NPC carries a colour) the team that tints the outline. An equipment slot whose stored token does not resolve
     * to a real item (an unknown material name, or a corrupt serialized payload) is dropped from the map, so the
     * slot shows empty rather than failing the whole spawn, and an unparseable colour falls back to the default
     * white outline; the appearance is always fail-soft.
     */
    private void applyAppearance(Player viewer, RenderedNpc rendered) {
        Npc npc = rendered.npc();
        int id = rendered.entityId();
        if (npc.hasEquipment()) {
            packets.send(viewer, packets.equipment(id, resolveEquipment(npc)));
        }
        // The on-fire, glow, and invisible bits share one shared-flags byte, so they compose into a single packet
        // rather than overwriting each other; sent only when at least one is set (the entity defaults to all-clear).
        if (npc.onFire() || npc.glowing() || npc.invisible()) {
            packets.send(viewer, packets.sharedFlags(id, npc.onFire(), npc.glowing(), npc.invisible()));
        }
        applyTeam(viewer, npc);
        if (npc.silent()) {
            packets.send(viewer, packets.silent(id, true));
        }
        applyShape(viewer, rendered, npc);
        // The per-entity-type metadata (baby/size/charged/villager) is sent only for the type that carries each
        // field, fail-soft per property: the support map lives in NpcTypeData to keep this class under its limit.
        NpcTypeData.apply(packets, viewer, id, npc, log);
    }

    /**
     * Send the per-NPC scoreboard team carrying the three team-scoped properties: the glow tint, the collision rule,
     * and whether the nametag renders. All three ride one team because an entity is on only one. A glow colour, a
     * non-default (non-colliding) state, and a cleared display name each need the team; an NPC that glows white,
     * collides and shows its name needs nothing (the no-team default is exactly that). The colour falls back to the
     * default white outline when the name is unknown, never failing the spawn.
     */
    private void applyTeam(Player viewer, Npc npc) {
        NamedColor color = npc.glowing() && npc.hasGlowColor() ? parseColor(npc.glowColor()) : null;
        boolean hideNametag = npc.displayNameHidden();
        if (color == null && npc.collidable() && !hideNametag) {
            return; // the no-team default already collides, shows the name, and carries no colour
        }
        // Seat the rendered name (display name or id) on the team: the fake player's nametag is its profile name, so
        // every team property must bind to the same name the tab-add carried.
        packets.send(viewer, packets.team(glowTeam(npc), renderedName(npc), color, npc.collidable(), hideNametag));
    }

    /**
     * Apply the NPC's pose and scale for this viewer. A non-default pose resolves to a packet-layer {@link NpcPose}
     * (an unknown name renders standing, fail-soft, never thrown on the render thread); a non-default scale ships
     * the resize attribute. The natural-size, standing default sends nothing: the entity already renders that way.
     */
    private void applyShape(Player viewer, RenderedNpc rendered, Npc npc) {
        if (npc.hasPose()) {
            NpcPose pose = parsePose(npc.pose());
            if (pose != null) {
                packets.send(viewer, packets.pose(rendered.entityId(), pose));
            }
        }
        if (npc.hasScale()) {
            packets.send(viewer, packets.scale(rendered.entityId(), npc.scale()));
        }
    }

    /**
     * Log the unresolvable stored type for {@code npc} once. The 1s reconcile retries a bad NPC for every viewer
     * every tick (the skip never marks the viewer shown), so warning unconditionally would flood the log; this
     * warns only the first time a given NPC carries a given bad value, and re-warns if the value later changes.
     */
    private void warnBadTypeOnce(Npc npc) {
        String name = npc.name().value();
        String badType = npc.entityType();
        if (!badType.equals(warnedBadType.put(name, badType))) {
            log.warn("NPC {} has an unknown entity type {}, skipping its spawn", name, badType);
        }
    }

    private static String profileName(Npc npc) {
        String name = npc.name().value();
        return name.length() <= MAX_PROFILE_NAME ? name : name.substring(0, MAX_PROFILE_NAME);
    }

    /**
     * The name rendered above the fake player and seated on its team: the display name when one is shown, otherwise
     * the NPC id. A fake player's nametag is its player-info profile name, so the display name lands here (capped to
     * the protocol's 16-char profile-name limit). An unset display name falls back to the id, which is the default,
     * and so does a cleared one: a cleared label is hidden through the team's nametag visibility rather than by
     * sending a blank profile name, which would leave the entry and the team membership malformed. The team member
     * name must equal this so the glow colour, collision rule and nametag rule all bind to the same name.
     */
    private static String renderedName(Npc npc) {
        if (!npc.hasDisplayName()) {
            return profileName(npc);
        }
        String shown = Objects.requireNonNull(npc.displayName(), "displayName").strip();
        return shown.length() <= MAX_PROFILE_NAME ? shown : shown.substring(0, MAX_PROFILE_NAME);
    }

    private static @Nullable TabSkin tabSkin(@Nullable NpcSkin skin) {
        if (skin == null) {
            return null;
        }
        if (skin.slim()) {
            // The model lives in the texture value, so forcing slim re-encodes it; the re-encoded value can no
            // longer carry the original signature, so it goes out unsigned (fine for a synthetic NPC profile).
            Optional<String> slimValue = NpcSkinModel.slimTexture(skin.texture());
            if (slimValue.isPresent()) {
                return TabSkin.unsigned(slimValue.get());
            }
        }
        return new TabSkin(skin.texture(), skin.signature());
    }

    /** The viewer's own skin for a mirror-skin NPC, or {@code null} when the viewer carries none (default Steve). */
    private static @Nullable TabSkin viewerSkin(Player viewer) {
        return BukkitNpcSkins.of(viewer)
                .map(skin -> new TabSkin(skin.texture(), skin.signature()))
                .orElse(null);
    }

    /** Resolve each stored token (a serialized item or a legacy material name) to a real item, dropping a slot
     * whose token this server cannot resolve. */
    private static Map<EquipmentSlot, ItemStack> resolveEquipment(Npc npc) {
        Map<EquipmentSlot, ItemStack> resolved = new EnumMap<>(EquipmentSlot.class);
        for (Map.Entry<com.uxplima.uxmessentials.npc.domain.EquipmentSlot, String> entry :
                npc.equipment().entrySet()) {
            EquipmentSlot slot = toPacketSlot(entry.getKey());
            EquipmentPayloads.resolve(entry.getValue()).ifPresent(item -> resolved.put(slot, item));
        }
        return resolved;
    }

    /** Map a domain equipment slot onto the uxmLib packet slot: the single place those two enums meet. */
    private static EquipmentSlot toPacketSlot(com.uxplima.uxmessentials.npc.domain.EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> EquipmentSlot.MAINHAND;
            case OFFHAND -> EquipmentSlot.OFFHAND;
            case HEAD -> EquipmentSlot.HEAD;
            case CHEST -> EquipmentSlot.CHEST;
            case LEGS -> EquipmentSlot.LEGS;
            case FEET -> EquipmentSlot.FEET;
        };
    }

    /** Parse a stored colour name to a {@link NamedColor}, falling back to the default white outline when unknown. */
    private static @Nullable NamedColor parseColor(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return NamedColor.valueOf(name.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** Parse a stored pose name to an {@link NpcPose}, or {@code null} when the name names no known pose (renders standing). */
    private static @Nullable NpcPose parsePose(String name) {
        try {
            return NpcPose.valueOf(name.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** The stable per-NPC scoreboard team name that tints its glow (capped well under the 16-char team-name limit). */
    private static String glowTeam(Npc npc) {
        return profileName(npc);
    }

    /**
     * Resolve a stored uppercase entity-type name to its canonical {@code minecraft:…} key, or {@code null} when
     * the name no longer names a real Bukkit type. A type that vanished between saves (a removed type, a typo in a
     * hand-edited row) returns {@code null} so the caller skips the spawn rather than throwing on the render thread.
     */
    private static @Nullable String bukkitTypeKey(String entityTypeName) {
        try {
            return EntityType.valueOf(entityTypeName.toUpperCase(Locale.ROOT))
                    .getKey()
                    .asString();
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
