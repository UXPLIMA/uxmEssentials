package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.application.port.LinkedNpcLocator;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.Visibility;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.hologram.HologramManager;
import com.uxplima.uxmlib.hologram.Holograms;
import org.jspecify.annotations.NullMarked;

/**
 * The outbound seam that keeps the in-world rendering in step with the stored model, realised over the uxmLib
 * native-Display hologram API. Each domain {@link Hologram} maps to one live uxmLib hologram by its type: a
 * {@code TEXT} hologram to a single multi-line {@code TextDisplay}, an {@code ITEM} hologram to an
 * {@code ItemDisplay} (its {@code Material} name resolved here), a {@code BLOCK} hologram to a
 * {@code BlockDisplay} (its BlockData string parsed here). The renderer tracks them by name so a re-render, a
 * refresh, or a despawn finds the live entity; an unknown material or unparseable BlockData is failed soft
 * (logged and skipped) so it never crashes the render.
 *
 * <p>Spawning and despawning a display entity must run on the owning region thread (Folia), so every mutation
 * hops through the injected {@link Scheduler} port: {@code render} schedules onto the hologram's location, and
 * {@code despawn} reuses the {@link Position} the entity was spawned at (tracked alongside the live entity)
 * rather than reading the live entity's location off-thread. A render replaces any existing live entity for
 * the same name (remove-then-spawn), so a line edit, a line-count change, a restyle, and a move all converge
 * to "the world matches the model"; the old entity is always removed on its own region thread, which matters
 * on a cross-world move where it lives in a different world from the new one. A world that is not loaded is
 * skipped with a warning rather than throwing.
 *
 * <p>Each line's MiniMessage source is run through the injected {@code placeholders} transform before it is
 * deserialised, so an operator may embed server-global {@code %papi%} tokens (online count, time, TPS). The
 * hologram is a single shared entity, so that transform resolves server-relative placeholders for the broadcast
 * base text every viewer sees by default; a hologram with a positive refresh interval is re-rendered by the
 * refresh task on its cadence, picking up fresh values. A static hologram with no placeholder renders once and
 * never again.
 *
 * <p>On top of that shared base, a text hologram whose lines embed a {@code %...%} token additionally renders
 * <em>per viewer</em>: after the native spawn, each eligible viewer is sent a text-override metadata packet (via
 * the {@link HologramTextOverrides} collaborator over the lib {@code DisplayTextPackets} port) carrying their own
 * resolved placeholder values, so each viewer sees their own text over the one shared {@code TextDisplay}, no
 * per-viewer entity. Overrides are sent on spawn, on join (so a joiner sees their values at once), and on each
 * refresh re-render (a remove-then-spawn re-sends them, keeping a refreshing hologram's per-viewer values
 * fresh). When PlaceholderAPI is absent the per-viewer bridge is the identity, so per-viewer text equals the
 * global text: the path is harmless. A static, no-placeholder, or item/block hologram is never per-viewer and
 * pays nothing.
 *
 * <p>A hologram's {@link Visibility} is applied at the spawn boundary. {@link Visibility.Mode#ALL} is the cheap
 * default. The shared entity is visible by default to everyone. {@link Visibility.Mode#PERMISSION} restricts
 * the entity to an allowed-viewer set (Paper's native {@code show/hideEntity}) recomputed from the online
 * permission-holders on every render and refresh. {@link Visibility.Mode#MANUAL} hides the entity from everyone
 * and restricts it to its persisted shown-viewer set, queried per hologram through the injected
 * {@code manualViewers} lookup; {@link #applyManualViewer(HologramName, java.util.UUID, boolean)} shows or hides
 * one online viewer the instant {@code /hologram show|hide} runs. {@link #recomputeVisibilityFor(Player)}
 * re-evaluates a single joiner so they pick up the permission-gated and manual holograms they qualify for
 * without waiting for a refresh tick. A finite {@link Visibility#distance()} maps onto the native display view
 * range. Blocks divided by the vanilla {@value HologramSpawns#VANILLA_VIEW_BLOCKS}-block tracking range, since the lib view
 * range is a multiplier, so the hologram culls beyond that radius; distance 0 leaves the appearance's own
 * view-range multiplier untouched.
 */
@NullMarked
public final class HologramRenderer implements HologramView, HologramPageCycler {

    /**
     * How far above the linked NPC's feet a linked hologram floats, so it sits over the NPC's head rather than
     * inside it. Roughly a player's standing height plus a little clearance, the conventional default
     * NPC-link offset.
     */
    static final double LINKED_NPC_Y_OFFSET = 2.2;

    /** The per-line height a grow-up hologram is raised by so its bottom sits at the anchor, the same per-line
     * factor the click-box span uses, an estimate the operator can fine-tune with the translation setting. */
    static final double LINE_HEIGHT = 0.28;

    private final Plugin plugin;
    private final HologramManager manager;
    private final Scheduler scheduler;
    private final Logger log;
    private final UnaryOperator<String> placeholders;
    private final Supplier<TagResolver> globalTags;
    private final HologramViewers viewers;
    private final HologramTextOverrides textOverrides;
    private final LinkedNpcLocator linkedNpcs;
    private final com.uxplima.uxmessentials.holograms.application.port.LeaderboardProviders leaderboards;
    private final HologramPageState pageState;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<String, Tracked> live = new ConcurrentHashMap<>();
    /** The PDC key stamped on a clickable hologram's Interaction hitbox; the click listener reads the same key. */
    private final org.bukkit.NamespacedKey clickKey;
    /** The animation frame, advanced on every (re-)render so a line's {@code <anim:…>} directive moves on refresh. */
    private final java.util.concurrent.atomic.AtomicInteger animationPhase =
            new java.util.concurrent.atomic.AtomicInteger();

    public HologramRenderer(
            Plugin plugin,
            HologramManager manager,
            Scheduler scheduler,
            Logger log,
            UnaryOperator<String> placeholders,
            Supplier<TagResolver> globalTags,
            HologramViewers viewers,
            HologramTextOverrides textOverrides,
            LinkedNpcLocator linkedNpcs,
            com.uxplima.uxmessentials.holograms.application.port.LeaderboardProviders leaderboards,
            HologramPageState pageState) {
        this.leaderboards = Objects.requireNonNull(leaderboards, "leaderboards");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.manager = Objects.requireNonNull(manager, "manager");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.placeholders = Objects.requireNonNull(placeholders, "placeholders");
        this.globalTags = Objects.requireNonNull(globalTags, "globalTags");
        this.viewers = Objects.requireNonNull(viewers, "viewers");
        this.textOverrides = Objects.requireNonNull(textOverrides, "textOverrides");
        this.linkedNpcs = Objects.requireNonNull(linkedNpcs, "linkedNpcs");
        this.pageState = Objects.requireNonNull(pageState, "pageState");
        this.clickKey = new org.bukkit.NamespacedKey(plugin, HologramClickKey.PDC_KEY);
    }

    @Override
    public void render(Hologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        Position anchor = anchorFor(hologram);
        World world = Bukkit.getWorld(anchor.world().uid());
        if (world == null) {
            log.warn(
                    "skipping hologram {}. World {} is not loaded",
                    hologram.name().value(),
                    anchor.world().name());
            return;
        }
        Location at = BukkitRefs.toLocation(world, anchor);
        com.uxplima.uxmessentials.holograms.domain.LeaderboardSpec leaderboard = hologram.leaderboard();
        if (leaderboard == null) {
            // Spawn on the anchor's region thread (Folia): for a linked hologram that is the NPC's region, where
            // the display entity actually lives, not the hologram's stored region.
            scheduler.onRegion(anchor, () -> spawnReplacing(hologram, anchor, at));
            return;
        }
        // A leaderboard's rows come from a (possibly DB-backed) provider, so fetch them off the region thread, then
        // hop back to render the hologram with the generated lines swapped in (not persisted).
        scheduler.async(() -> {
            List<HologramLine> rows = generateLeaderboardLines(leaderboard);
            scheduler.onRegion(anchor, () -> spawnReplacing(hologram.withLines(rows), anchor, at));
        });
    }

    /** The top rows of {@code spec}'s provider, laid out into ranked lines; a single placeholder line when empty. */
    private List<HologramLine> generateLeaderboardLines(
            com.uxplima.uxmessentials.holograms.domain.LeaderboardSpec spec) {
        List<com.uxplima.uxmessentials.holograms.application.port.LeaderboardEntry> entries = leaderboards
                .find(spec.providerId())
                .map(provider -> provider.top(spec.limit()))
                .orElse(List.of());
        if (entries.isEmpty()) {
            return List.of(new HologramLine("<gray>(no data)"));
        }
        List<HologramLine> rows = new java.util.ArrayList<>(entries.size());
        int rank = 1;
        for (com.uxplima.uxmessentials.holograms.application.port.LeaderboardEntry entry : entries) {
            rows.add(new HologramLine(
                    "<gray>#" + rank + " <white>" + entry.name() + " <gray>- <green>" + entry.score()));
            rank++;
        }
        return rows;
    }

    private Position anchorFor(Hologram hologram) {
        return anchorFor(hologram, linkedNpcs);
    }

    /**
     * Where the hologram should render: when it is linked to an NPC that the locator can find, the NPC's current
     * position raised by {@link #LINKED_NPC_Y_OFFSET} so it floats above the NPC's head; otherwise (not linked, or
     * the linked NPC no longer exists) its own stored location. Failing soft on a missing NPC means a stale link
     * never crashes or hides the hologram: it simply renders where it was placed. Pure of any Bukkit call, so the
     * position math and the fail-soft fallback are unit-testable with a fake locator.
     */
    static Position anchorFor(Hologram hologram, LinkedNpcLocator linkedNpcs) {
        String linked = hologram.linkedNpcName();
        if (linked == null) {
            return hologram.location();
        }
        return linkedNpcs.locate(linked).map(HologramRenderer::aboveNpc).orElseGet(hologram::location);
    }

    private static Position aboveNpc(Position npc) {
        return new Position(npc.world(), npc.x(), npc.y() + LINKED_NPC_Y_OFFSET, npc.z(), npc.yaw(), npc.pitch());
    }

    /**
     * Re-render every tracked hologram linked to the NPC {@code npcName}, picking up the NPC's new position (or
     * falling back to the hologram's own location when the NPC was removed). Called off the npc-move/-delete event
     * so a linked hologram visually follows the NPC; a hologram not linked to that NPC is untouched.
     */
    public void reanchorLinkedTo(String npcName) {
        Objects.requireNonNull(npcName, "npcName");
        for (Tracked tracked : live.values()) {
            if (npcName.equals(tracked.hologram().linkedNpcName())) {
                render(tracked.hologram());
            }
        }
    }

    /** Despawn every tracked hologram now, call on module stop so no display entity is orphaned. */
    public void despawnAll() {
        for (Tracked tracked : live.values()) {
            // Each entity is removed on its own region thread, derived from the position it was spawned at.
            scheduler.onRegion(tracked.position(), () -> tracked.live().removeFrom(manager));
        }
        live.clear();
        pageState.clearAll();
    }

    @Override
    public void despawn(HologramName name) {
        Objects.requireNonNull(name, "name");
        Tracked existing = live.remove(name.value());
        pageState.clear(name.value());
        if (existing == null) {
            return;
        }
        // The display entity must be removed on its own region thread; route through its spawn position.
        scheduler.onRegion(existing.position(), () -> existing.live().removeFrom(manager));
    }

    /**
     * Re-render the lines of a currently-tracked hologram in place, picking up fresh placeholder values. A
     * full remove-then-spawn (rather than {@code setText}) keeps the path identical to {@link #render} and
     * works even when a re-render coincides with a region hop; a hologram no longer tracked is a no-op.
     */
    public void refresh(Hologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        if (!live.containsKey(hologram.name().value())) {
            return;
        }
        render(hologram);
    }

    /**
     * Re-evaluate every per-viewer hologram for a single {@code joiner} on their region thread. A permission-gated
     * or manual hologram is shown to the joiner when they qualify and hidden otherwise ({@code ALL} holograms are
     * visible by default and need no visibility call); a hologram whose lines embed a placeholder also sends the
     * joiner their own text override (for an {@code ALL} hologram too) when they may see it. Called from the join
     * listener so a joiner sees the holograms, and their own placeholder values, at once, not after a refresh.
     */
    public void recomputeVisibilityFor(Player joiner) {
        Objects.requireNonNull(joiner, "joiner");
        for (Tracked tracked : live.values()) {
            Hologram hologram = tracked.hologram();
            Visibility visibility = hologram.visibility();
            Set<UUID> shown = viewers.shownViewersFor(hologram);
            boolean gated = visibility.isPermissionGated() || visibility.isManual();
            boolean perViewerText = textOverrides.hasPerViewerText(hologram)
                    && tracked.live().textEntityId() != RenderedHologram.NO_ENTITY;
            boolean blacklisted = viewers.isBlacklisted(hologram, joiner);
            if (gated || perViewerText || blacklisted) {
                scheduler.onRegion(
                        tracked.position(),
                        () -> applyJoiner(tracked.live(), hologram, joiner, shown, gated, blacklisted));
            }
        }
    }

    /** Apply a joiner's visibility (when gated) and their per-viewer text override (when they may see it). */
    private void applyJoiner(
            RenderedHologram live,
            Hologram hologram,
            Player joiner,
            Set<UUID> shown,
            boolean gated,
            boolean blacklisted) {
        if (blacklisted) {
            // A blacklisted joiner is hidden regardless of mode; no override is sent (they cannot see the entity).
            live.hide(plugin, joiner);
            return;
        }
        if (gated) {
            // Showing/hiding the shared entity touches the hologram's own viewer set, so it stays on this
            // (the hologram's) region thread.
            viewers.applyViewer(live, hologram.visibility(), joiner, shown);
        }
        if (viewers.maySee(hologram, joiner, shown)
                && textOverrides.hasPerViewerText(hologram)
                && live.textEntityId() != RenderedHologram.NO_ENTITY) {
            dispatchPerViewerText(scheduler, textOverrides, List.of(joiner), live.textEntityId(), hologram);
        }
    }

    /**
     * Apply a single MANUAL viewer change to the live entity at once: show the hologram under {@code name} to
     * the online {@code viewer} when {@code visible}, hide it otherwise, so {@code /hologram show|hide} takes
     * effect without a refresh tick. A no-op when the hologram is not tracked or the viewer is offline; the
     * change is routed onto the entity's region thread.
     */
    @Override
    public void applyManualViewer(HologramName name, UUID viewer, boolean visible) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(viewer, "viewer");
        Tracked tracked = live.get(name.value());
        Player online = Bukkit.getPlayer(viewer);
        if (tracked == null || online == null) {
            return;
        }
        scheduler.onRegion(tracked.position(), () -> {
            if (visible) {
                tracked.live().show(plugin, online);
            } else {
                tracked.live().hide(plugin, online);
            }
        });
    }

    /** The height a grow-up hologram's spawn is raised by, its text block, roughly one line height per line. */
    private static double growUpHeight(Hologram hologram) {
        return hologram.lineCount() * LINE_HEIGHT;
    }

    private void spawnReplacing(Hologram hologram, Position anchor, Location at) {
        Tracked previous = live.remove(hologram.name().value());
        if (previous != null) {
            // Despawn the old entity on its own region thread; on a cross-world move it lives in a
            // different world from the new one, so it must not be removed inline on this region thread.
            scheduler.onRegion(previous.position(), () -> previous.live().removeFrom(manager));
        }
        // A grow-up hologram anchors at the bottom of its text, so raise the spawn by the text-block height to
        // lay the lines out above the anchor instead of below it; the stored anchor (its region) is unchanged.
        Location spawnAt = hologram.growUp() ? at.clone().add(0, growUpHeight(hologram), 0) : at;
        // Compose the line transform: resolve server-global placeholders first, then expand any <anim:…> directive
        // for the current frame. The frame advances per render, so a refreshing hologram's animated line moves.
        int phase = animationPhase.getAndIncrement();
        UnaryOperator<String> animated = source -> HologramAnimations.expand(placeholders.apply(source), phase);
        RenderedHologram spawned =
                HologramSpawns.spawnFor(manager, log, hologram, spawnAt, animated, miniMessage, globalTags.get());
        if (spawned == null) {
            // Invalid item material or block data: already logged; leave nothing tracked rather than crash.
            return;
        }
        if (hologram.clickCommand() != null || hologram.isMultiPage()) {
            // A clickable hologram needs the hitbox to run its command; a multi-page hologram needs it so a
            // right-click can cycle the viewer's page (the listener routes one or the other).
            spawned = withClickBox(spawned, spawnAt, hologram);
        }
        viewers.applyOnSpawn(spawned, hologram);
        // Track the anchor the entity actually spawned at (the NPC's position for a linked hologram), so a later
        // despawn or replace is routed onto the entity's real region, not the hologram's stored location.
        live.put(hologram.name().value(), new Tracked(spawned, hologram, anchor));
        sendPerViewerText(spawned, hologram);
    }

    /**
     * Spawn the invisible {@code Interaction} hitbox beside a clickable hologram and bundle it with {@code spawned}
     * so it shares the lifecycle (despawned together, never orphaned). The box is stamped with the hologram's name
     * so the click listener can resolve it, and is non-persistent. A restart drops it and {@code spawnStored}
     * re-creates it, so a crash never leaves a stray hitbox. A text hologram's lines hang downward from the anchor,
     * so the box brackets that span; an item/block/head model sits at the anchor, so the box centres on it.
     */
    private RenderedHologram withClickBox(RenderedHologram spawned, Location at, Hologram hologram) {
        boolean text = hologram.type() == com.uxplima.uxmessentials.holograms.domain.HologramType.TEXT;
        // Size the hitbox to the tallest page so it brackets every page of a multi-page hologram; for a
        // single-page hologram maxPageLineCount() is just the line count, so the box is unchanged.
        float height = text ? Math.max(1.0f, hologram.maxPageLineCount() * 0.28f + 0.4f) : 1.2f;
        Location boxLocation =
                text ? at.clone().subtract(0, height, 0) : at.clone().subtract(0, 0.6, 0);
        String name = hologram.name().value();
        org.bukkit.entity.Interaction box = at.getWorld()
                .spawn(boxLocation, org.bukkit.entity.Interaction.class, entity -> {
                    entity.setInteractionWidth(1.2f);
                    entity.setInteractionHeight(height);
                    entity.setResponsive(true);
                    entity.setPersistent(false);
                    entity.getPersistentDataContainer()
                            .set(clickKey, org.bukkit.persistence.PersistentDataType.STRING, name);
                });
        return RenderedHologram.withClickBox(spawned, box);
    }

    /**
     * Send each eligible viewer their per-viewer text override over the just-spawned entity (no-op if not PAPI).
     * The eligibility scan runs here on the hologram's region thread (as the visibility scan does), but each
     * viewer's resolve is hopped onto that viewer's own entity thread: resolving a player-relative placeholder
     * reads the viewer's live state, which is not safe to read off the hologram's region thread under Folia.
     */
    private void sendPerViewerText(RenderedHologram spawned, Hologram hologram) {
        if (!textOverrides.hasPerViewerText(hologram) || spawned.textEntityId() == RenderedHologram.NO_ENTITY) {
            return;
        }
        dispatchPerViewerText(scheduler, textOverrides, viewers.eligible(hologram), spawned.textEntityId(), hologram);
    }

    /**
     * Advance {@code viewer} to the next page of the multi-page hologram {@code name} and re-send only their text
     * override, so the click flips just that viewer's page over the one shared display. A no-op when the hologram
     * is not tracked, is not multi-page, or has no text entity. The page is advanced first (atomically in
     * {@link HologramPageState}), then the viewer's resolve is hopped onto their own entity thread, where the
     * override reads back the new page: exactly as the spawn/join paths dispatch per-viewer text.
     */
    @Override
    public void cyclePage(Player viewer, HologramName name) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(name, "name");
        Tracked tracked = live.get(name.value());
        if (tracked == null) {
            return;
        }
        Hologram hologram = tracked.hologram();
        int entityId = tracked.live().textEntityId();
        if (!hologram.isMultiPage() || entityId == RenderedHologram.NO_ENTITY) {
            return;
        }
        pageState.advance(name.value(), viewer.getUniqueId(), hologram.pageCount());
        dispatchPerViewerText(scheduler, textOverrides, List.of(viewer), entityId, hologram);
    }

    /**
     * Hop each viewer's per-viewer text resolve onto <em>that viewer's</em> entity thread before resolving and
     * sending the override. Resolving a player-relative {@code %papi%} token reads the viewer's live entity
     * state, which under Folia is only safe to touch from the entity's owning thread: never the hologram's
     * region thread the spawn/refresh runs on, where a viewer may sit in a different region or world (the same
     * rule the scoreboard and tablist render loops follow). Pure of any spawn or live-entity read, so it is
     * unit-testable with a recording scheduler and fake viewers.
     */
    static void dispatchPerViewerText(
            Scheduler scheduler,
            HologramTextOverrides textOverrides,
            List<? extends Player> eligible,
            int entityId,
            Hologram hologram) {
        for (Player viewer : eligible) {
            scheduler.onEntity(BukkitRefs.toRef(viewer), () -> textOverrides.sendOverride(viewer, entityId, hologram));
        }
    }

    /**
     * Whether {@code who} may see a hologram with this {@code visibility}: everyone for {@code ALL}, only a
     * holder of the gating node for {@code PERMISSION}, and only a member of {@code shownViewers} for
     * {@code MANUAL}. Pure (the {@code Permissions} call aside), so the gated and manual viewer sets are
     * unit-testable with a fake {@code Permissions} and an explicit shown set; the visibility collaborator and
     * tests reach it here.
     */
    static boolean maySee(Permissions permissions, Visibility visibility, PlayerRef who, Set<UUID> shownViewers) {
        if (visibility.isManual()) {
            return shownViewers.contains(who.uuid());
        }
        if (!visibility.isPermissionGated()) {
            return true;
        }
        String node = visibility.permission();
        return node != null && permissions.has(who, node);
    }

    /**
     * The pure model-to-builder mapping, kept reachable here so the builder mapping stays unit-testable. Uses an
     * empty MiniPlaceholders resolver: the global-tag resolution is exercised directly against {@link HologramSpawns}.
     */
    static Holograms.Builder builderFor(
            Hologram hologram, UnaryOperator<String> placeholders, MiniMessage miniMessage) {
        return HologramSpawns.builderFor(hologram, placeholders, miniMessage, TagResolver.empty());
    }

    /**
     * A live uxmLib hologram paired with the domain {@link Hologram} it renders (so its {@link Visibility} is
     * known for a viewer recompute) and the {@link Position} it was spawned at (so its owning region is known).
     * The live entity is held as a type-agnostic {@link RenderedHologram} so a text, item or block hologram is
     * tracked, despawned and re-shown the same way.
     */
    private record Tracked(RenderedHologram live, Hologram hologram, Position position) {}
}
