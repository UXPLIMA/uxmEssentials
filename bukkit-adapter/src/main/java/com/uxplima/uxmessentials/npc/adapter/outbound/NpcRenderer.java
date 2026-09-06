package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.npc.application.port.NpcView;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.packet.npc.NpcPackets;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The outbound seam that keeps the in-world fake players in step with the stored model, realised over the uxmLib
 * NPC packet stack. An NPC has no real entity: each viewer is sent a player-info ADD (carrying the name and
 * skin), then a spawn-player packet, in one bundle so they arrive together. The entry is added unlisted so the
 * fake player renders without a tab-list row, and it is kept until the NPC despawns, removing the player-info
 * entry would de-render the body on modern clients, so the tab-remove rides the despawn path, not the spawn. The
 * renderer tracks which NPCs each viewer has been shown ({@link #shownTo}), so it can send a clean remove on
 * quit, delete, or a move out of range and re-send on join: a viewer never keeps a ghost.
 *
 * <p>Every NPC has a stable {@link RenderedNpc#profileId() profile uuid} (derived from its name) and an entity
 * id allocated once and reused, so a re-render (move or re-skin) is a remove-then-spawn under the same id and a
 * client never accumulates duplicates. Sends hop onto the viewer's entity region thread through the injected
 * {@link Scheduler} (Folia-correct); resolving the viewer's distance reads the live player there. A viewer
 * within {@link #renderRange} blocks of an NPC in the same world is eligible.
 *
 * <p>Two distinct paths keep the world in step with the model:
 *
 * <ul>
 *   <li><b>{@link #render(Npc) explicit render}</b>, driven by the create/move/re-skin/look-toggle use cases. For an
 *       in-range viewer it <em>forces</em> a fresh re-render (remove-then-spawn under the same entity id) so a new skin
 *       or position is reflected immediately even for a viewer that already had the old one; an in-range viewer that
 *       was not shown is spawned; an out-of-range viewer that was shown is removed.</li>
 *   <li><b>{@link #refresh() refresh tick} / join / world-change</b>, an <em>idempotent</em> reconcile that acts only
 *       on transitions: a not-shown in-range viewer is spawned, an already-shown in-range viewer is left untouched (the
 *       look loop owns ongoing rotation), an out-of-range shown viewer is removed. This is what stops the once-a-second
 *       re-spawn flood and tab flicker for a stationary player.</li>
 * </ul>
 *
 * <p>Composing and sending the spawn packets. The player-vs-mob branch, the unlisted tab entry, the skin,
 * equipment, glow, and the warn-once on a bad stored type. Is delegated to the injected {@link NpcViewSpawner};
 * this class owns the per-viewer tracking and decides only <em>when</em> to spawn or remove.
 */
@NullMarked
public final class NpcRenderer implements NpcView {

    /** A vanilla player's eye height above its feet: where a fake player's head sits for the look aim. */
    private static final double EYE_HEIGHT = 1.62;
    /** Team names are capped at 16 chars by the protocol, so the glow-team name is truncated to match the spawn. */
    private static final int MAX_TEAM_NAME = 16;

    private final NpcPackets packets;
    private final NpcViewSpawner spawner;
    private final Scheduler scheduler;
    private final double renderRange;
    private final double lookRange;
    private final Map<String, RenderedNpc> live = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> shownTo = new ConcurrentHashMap<>();
    private final Map<Integer, String> nameByEntityId = new ConcurrentHashMap<>();

    public NpcRenderer(
            NpcPackets packets, NpcViewSpawner spawner, Scheduler scheduler, double renderRange, double lookRange) {
        this.packets = Objects.requireNonNull(packets, "packets");
        this.spawner = Objects.requireNonNull(spawner, "spawner");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.renderRange = renderRange;
        this.lookRange = lookRange;
    }

    @Override
    public void render(Npc npc) {
        Objects.requireNonNull(npc, "npc");
        RenderedNpc rendered = track(npc);
        // The create/edit use cases drive this off an async worker (the DB write runs off the tick thread), so the
        // online-player snapshot has to be taken on the global region thread. Reading it on the async thread misses
        // every viewer and the fresh NPC never spawns. Each per-viewer step then hops to that viewer's region thread.
        scheduler.onGlobal(() -> {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                forceRenderForViewer(viewer, rendered);
            }
        });
    }

    @Override
    public void despawn(NpcName name) {
        Objects.requireNonNull(name, "name");
        RenderedNpc removed = live.remove(name.value());
        if (removed == null) {
            return;
        }
        nameByEntityId.remove(removed.entityId());
        spawner.forget(name.value());
        // Delete also runs off an async worker, so take the viewer snapshot on the global region thread for the
        // same reason as render: an async read would skip every viewer and leave the fake player ghosting.
        scheduler.onGlobal(() -> {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                removeFromViewer(viewer, removed);
            }
        });
    }

    /** Show every in-range NPC to a player who just joined, and start tracking them as a viewer. */
    public void showAllTo(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        shownTo.computeIfAbsent(viewer.getUniqueId(), id -> ConcurrentHashMap.newKeySet());
        for (RenderedNpc rendered : live.values()) {
            reconcileViewer(viewer, rendered);
        }
    }

    /** Forget a player who quit: drop their shown-set so nothing leaks, no packets needed (they are gone). */
    public void forget(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        shownTo.remove(viewer.getUniqueId());
    }

    /**
     * The 1s refresh tick: reconcile every NPC for every online viewer on transitions only. A newly in-range viewer is
     * spawned, a newly out-of-range viewer is removed, and an already-shown stationary viewer is left untouched, so a
     * standing player never gets a redundant once-a-second re-spawn or tab flicker.
     */
    public void refresh() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (RenderedNpc rendered : live.values()) {
                reconcileViewer(viewer, rendered);
            }
        }
    }

    /** Remove every NPC from every viewer now, call on module stop so no fake player is orphaned. */
    public void despawnAll() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (RenderedNpc rendered : live.values()) {
                removeFromViewer(viewer, rendered);
            }
        }
        live.clear();
        shownTo.clear();
        nameByEntityId.clear();
        spawner.forgetAll();
    }

    /**
     * The name of the NPC rendered under {@code entityId}, or {@code null} when no NPC owns it. Resolves a click
     * on a fake player's (server-unknown) entity id back to the stored NPC. The entity id is allocated once per
     * NPC and shared by every viewer, so a single map answers for all of them.
     */
    public @Nullable String npcNameAt(int entityId) {
        return nameByEntityId.get(entityId);
    }

    /**
     * Every tracked NPC's name, for command tab-completion. The renderer tracks every stored NPC (each is rendered
     * on enable and on create, and stays tracked until deleted), so its live keyset is the warm, in-memory name set
     * a suggestion provider reads on the tick thread without touching the DB. The returned set is a copy, so a
     * concurrent edit never leaks the backing map.
     */
    public java.util.Set<String> npcNames() {
        return java.util.Set.copyOf(live.keySet());
    }

    /**
     * Re-aim every looking NPC at its nearby viewers (the look tick). For each online viewer and each NPC the
     * viewer is currently shown, if the NPC has look-at-player on and the viewer is within the look range, send
     * that viewer head- and body-look packets turning the fake player toward the viewer's eyes, a per-viewer
     * rotation, so each viewer sees the NPC face them. Disabled or out-of-range NPCs are left at their fixed
     * facing.
     */
    public void lookTick() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (RenderedNpc rendered : live.values()) {
                if (rendered.npc().lookAtPlayer()) {
                    aimAtViewer(viewer, rendered);
                }
            }
        }
    }

    private void aimAtViewer(Player viewer, RenderedNpc rendered) {
        // Reads the live viewer location, so it must run on the viewer's region thread alongside the send.
        scheduler.onEntity(BukkitRefs.toRef(viewer), () -> {
            if (!isShown(viewer, rendered) || !inLookRange(viewer, rendered.npc())) {
                return;
            }
            Location eye = viewer.getEyeLocation();
            Position at = rendered.npc().location();
            LookAngles look = LookAngles.facing(
                    at.x(), at.y() + EYE_HEIGHT, at.z(), eye.getX(), eye.getY(), eye.getZ(), at.yaw());
            packets.send(viewer, packets.headLook(rendered.entityId(), look.yaw()));
            packets.send(viewer, packets.bodyLook(rendered.entityId(), look.yaw(), look.pitch()));
        });
    }

    private boolean isShown(Player viewer, RenderedNpc rendered) {
        Set<String> shown = shownTo.get(viewer.getUniqueId());
        return shown != null && shown.contains(rendered.npc().name().value());
    }

    private boolean inLookRange(Player viewer, Npc npc) {
        Position viewerAt = BukkitRefs.toPosition(Objects.requireNonNull(viewer.getLocation(), "viewer location"));
        Double override = npc.turnDistance();
        double range = override != null ? override : lookRange;
        return viewerAt.distanceTo(npc.location()) <= range;
    }

    private RenderedNpc track(Npc npc) {
        RenderedNpc rendered = live.compute(
                npc.name().value(),
                (name, existing) ->
                        existing == null ? new RenderedNpc(npc, packets.allocateEntityId()) : existing.withNpc(npc));
        nameByEntityId.put(rendered.entityId(), npc.name().value());
        return rendered;
    }

    /**
     * The idempotent reconcile used by the refresh tick, join, and world-change: act only on transitions. A not-shown
     * in-range viewer is spawned; an already-shown in-range viewer is left untouched (no re-spawn, no re-tabAdd, the
     * look loop owns ongoing rotation); an out-of-range shown viewer is removed.
     */
    private void reconcileViewer(Player viewer, RenderedNpc rendered) {
        // The range check reads the live player location, so it must run on the viewer's own region thread along
        // with the send; doing it inline here would touch a Player off its region thread (unsafe on Folia).
        scheduler.onEntity(BukkitRefs.toRef(viewer), () -> {
            if (inRange(viewer, rendered.npc())) {
                if (!isShown(viewer, rendered)) {
                    spawnForViewer(viewer, rendered);
                }
            } else {
                removeFromViewer(viewer, rendered);
            }
        });
    }

    /**
     * The explicit-edit reconcile used by {@link #render(Npc)} (create + every edit use case): force the viewer to the
     * current snapshot. An in-range viewer is re-rendered fresh, remove-then-spawn under the same entity id when it was
     * already shown so a new skin or position lands immediately, or a plain spawn when it was not shown; an out-of-range
     * shown viewer is removed.
     */
    private void forceRenderForViewer(Player viewer, RenderedNpc rendered) {
        scheduler.onEntity(BukkitRefs.toRef(viewer), () -> {
            if (inRange(viewer, rendered.npc())) {
                if (isShown(viewer, rendered)) {
                    removeFromViewer(viewer, rendered);
                }
                spawnForViewer(viewer, rendered);
            } else {
                removeFromViewer(viewer, rendered);
            }
        });
    }

    /**
     * Compose and send the spawn packets for this viewer, then mark the viewer shown, unless the stored type was
     * unresolvable, in which case the spawn is skipped (logged once by the spawner) and the viewer is left
     * unshown so the 1s reconcile retries it without leaving a phantom in the tracking map.
     */
    private void spawnForViewer(Player viewer, RenderedNpc rendered) {
        if (spawner.spawn(viewer, rendered)) {
            shownTo.computeIfAbsent(viewer.getUniqueId(), id -> ConcurrentHashMap.newKeySet())
                    .add(rendered.npc().name().value());
        }
    }

    private void removeFromViewer(Player viewer, RenderedNpc rendered) {
        Set<String> shown = shownTo.get(viewer.getUniqueId());
        if (shown == null || !shown.remove(rendered.npc().name().value())) {
            return;
        }
        packets.send(viewer, packets.remove(rendered.entityId()));
        packets.send(viewer, packets.tabRemove(rendered.profileId()));
        // The glow-colour team is client-side scoreboard state that outlives the despawned entity, so drop it here
        // on every despawn path (out of range, delete, world change, and the remove step of a glow re-render).
        // Removing a team the viewer never had is a harmless no-op, so this is unconditional rather than gated on
        // whether the NPC was glowing: it also clears a team left by a now-cleared colour.
        packets.send(viewer, packets.glowColorRemove(glowTeam(rendered.npc())));
    }

    private boolean inRange(Player viewer, Npc npc) {
        Position viewerAt = BukkitRefs.toPosition(Objects.requireNonNull(viewer.getLocation(), "viewer location"));
        Double override = npc.viewDistance();
        double range = override != null ? override : renderRange;
        return viewerAt.distanceTo(npc.location()) <= range;
    }

    /**
     * The stable per-NPC scoreboard team name that tints its glow. The NPC name truncated to the protocol's
     * 16-char team-name limit, matching the team the spawner adds, so the despawn path removes the right one.
     */
    private static String glowTeam(Npc npc) {
        String name = npc.name().value();
        return name.length() <= MAX_TEAM_NAME ? name : name.substring(0, MAX_TEAM_NAME);
    }
}
