package com.uxplima.uxmessentials.nametags.adapter.outbound;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.nametags.application.port.NametagVanish;
import com.uxplima.uxmessentials.nametags.domain.NametagConfig;
import com.uxplima.uxmessentials.nametags.domain.NametagFormat;
import com.uxplima.uxmessentials.nametags.domain.NametagVisibility;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.adapter.outbound.team.PlayerTeamCoordinator;
import com.uxplima.uxmessentials.shared.display.ConditionContext;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.nametag.Appearance;
import com.uxplima.uxmlib.nametag.NametagHandle;
import com.uxplima.uxmlib.nametag.NametagRenderer;
import com.uxplima.uxmlib.nametag.PerViewerText;
import org.jspecify.annotations.NullMarked;

/**
 * Drives the per-wearer above-head nametag from the live {@link NametagConfig} over uxmLib's packet
 * {@link NametagRenderer}: true per-viewer text, multi-line stacks, line-of-sight fading, and a per-target refresh loop
 * the <em>lib</em> owns. Each wearer is given the format {@link NametagConfig#select selected} for them, the
 * highest-priority format whose condition matches, evaluated against a {@link ConditionContext} built from the live
 * player.
 *
 * <h2>Who owns which loop</h2>
 *
 * The lib's {@code show(...)} starts a region-thread refresh task per wearer. At the operator's configured
 * {@code refresh-ticks} period, the same interval that drives this context's animation clock. That re-asks the viewer
 * supplier for the live audience, diffs it (spawn newcomers, drop departed, refresh the rest), re-resolves the
 * per-viewer text, and re-applies line-of-sight fading, so this class runs <em>no</em> render loop of its own. What
 * this class still owns is
 * <em>format selection</em>: a permission/world/gamemode/sneak/show-when change is not something the lib loop sees, so
 * {@link #update} re-selects the wearer's format each reconcile tick and removes-then-re-shows when the selected format
 * (or its appearance) changed. A wearer who matches no format has their handle removed; the eligible-viewer cull
 * (range, vanish, sneak) lives in the viewer supplier and is re-read by the lib loop every refresh.
 *
 * <h2>Folia region-thread discipline</h2>
 *
 * {@link #show} and {@link #update} are called by the caller (the reconcile task and the lifecycle listener) already
 * hopped onto the wearer's entity thread; the lib's refresh task is itself an {@code entityTimer} on the wearer, so its
 * viewer-supplier and text callbacks run on the wearer's region thread too. {@link #remove} only drops the handle and
 * sends remove packets, which is channel I/O safe from any thread.
 */
@NullMarked
public final class PacketNametagPresenter {

    // The cull radius used when a format authors no viewer-distance: nametags past this many blocks are dropped from
    // the per-tick refresh. Chosen above the usual entity-tracking range so a visible player keeps their nametag.
    private static final double DEFAULT_VIEWER_DISTANCE_BLOCKS = 48.0;

    private final Supplier<NametagConfig> config;
    private final NametagRenderer libRenderer;
    private final AnimationRegistry animations;
    private final NametagVanish vanish;
    private final Supplier<Duration> refreshPeriod;
    private final PlayerTeamCoordinator teams;
    private final BooleanSupplier hideVanillaName;
    private final Map<UUID, Tracked> live = new ConcurrentHashMap<>();

    /**
     * The format {@code who}'s nametag is currently drawn from, or empty when they wear none: nobody matched a
     * format, or their handle was removed. Read by the {@code nametags_format} placeholder off the same map the
     * reconcile tick maintains, so it answers with what is actually shown above their head rather than re-running
     * the selector (which would evaluate the format conditions a second time).
     */
    public Optional<String> appliedFormat(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Tracked tracked = live.get(who.uuid());
        return tracked == null ? Optional.empty() : Optional.of(tracked.format().name());
    }

    /**
     * Each online player's last-known {@link Position}, refreshed on that player's <em>own</em> region thread by
     * {@link #snapshotSelf}. The viewer-distance cull needs every candidate viewer's position, but a viewer's live
     * location is owned by the viewer's region thread, not the wearer's whose cull runs it, reading it cross-region
     * is a torn read on Folia. So each player publishes their own position here on their own thread (the reconcile
     * task already hops to every player's entity thread each tick), and the cull reads the viewer's snapshot instead
     * of the live location. A one-tick staleness at the reconcile cadence is immaterial to a distance cull; a viewer
     * not yet snapshotted (joined this tick, before their first reconcile) is simply excluded until their next tick.
     */
    private final Map<UUID, Position> positionSnapshots = new ConcurrentHashMap<>();

    public PacketNametagPresenter(
            Supplier<NametagConfig> config,
            NametagRenderer libRenderer,
            AnimationRegistry animations,
            NametagVanish vanish,
            Supplier<Duration> refreshPeriod,
            PlayerTeamCoordinator teams,
            BooleanSupplier hideVanillaName) {
        this.config = Objects.requireNonNull(config, "config");
        this.libRenderer = Objects.requireNonNull(libRenderer, "libRenderer");
        this.animations = Objects.requireNonNull(animations, "animations");
        this.vanish = Objects.requireNonNull(vanish, "vanish");
        this.refreshPeriod = Objects.requireNonNull(refreshPeriod, "refreshPeriod");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.hideVanillaName = Objects.requireNonNull(hideVanillaName, "hideVanillaName");
    }

    /** Show {@code wearer}'s nametag from the selected format. Must run on the wearer's region thread. */
    public void show(Player wearer) {
        Objects.requireNonNull(wearer, "wearer");
        // Seed this player's position snapshot on their own region thread on first show, so they are an eligible
        // viewer candidate for others' culls immediately rather than only after their first reconcile tick.
        snapshotSelf(wearer);
        // A wearer already shown (the reconcile tick showed for them, then a queued onJoin show arrives) must not be
        // shown a second time: that would orphan the first handle and leak its refresh task. Reconcile instead.
        if (live.containsKey(wearer.getUniqueId())) {
            update(wearer);
            return;
        }
        Optional<NametagFormat> selected = selectFor(wearer);
        if (selected.isEmpty()) {
            // No format applies (e.g. a reconnecting player who used to match one no longer does). Defensively restore
            // the vanilla name in case a stale hide-team entry somehow survived, so the player is never left nameless.
            // We are on the wearer's region thread, so touching the board is safe.
            teams.show(wearer);
            return;
        }
        NametagFormat format = selected.get();
        Appearance appearance = NametagAppearanceMapper.toAppearance(format.appearance());
        // One line cache per wearer-show: the lib loop calls the per-viewer text callback once per viewer each refresh,
        // and every viewer resolves to identical lines for a given animation frame today, so the cache parses the frame
        // once and hands the same list to every viewer until the frame advances.
        LineCache lineCache = new LineCache();
        NametagHandle handle = libRenderer.show(
                wearer,
                appearance,
                viewerSupplier(wearer, format),
                perViewerText(format, wearer, lineCache),
                Objects.requireNonNull(refreshPeriod.get(), "refreshPeriod"));
        live.put(wearer.getUniqueId(), new Tracked(handle, format));
        // With a custom nametag now live, hide the vanilla above-head name globally (a team with NEVER name-tag
        // visibility) so a viewer does not see both. Minor known interaction: a viewer just outside the packet
        // nametag's view-range sees no name at all rather than the vanilla one. We are on the wearer's region thread.
        if (hideVanillaName.getAsBoolean()) {
            teams.hide(wearer);
        }
    }

    /** Reconcile {@code wearer}'s nametag with the selected format. Must run on the wearer's region thread. */
    public void update(Player wearer) {
        Objects.requireNonNull(wearer, "wearer");
        // We are on this player's own region thread (the reconcile task hops here each tick), so publish their live
        // position for every other wearer's distance cull to read off-thread. Done for every online player, formatted
        // or not, so a wearer can be culled against a viewer who themselves matches no format.
        snapshotSelf(wearer);
        Tracked tracked = live.get(wearer.getUniqueId());
        if (tracked == null) {
            show(wearer);
            return;
        }
        Optional<NametagFormat> selected = selectFor(wearer);
        if (selected.isEmpty()) {
            remove(wearer.getUniqueId());
            // No format applies any more, so the wearer should show their vanilla name again. We have the live player
            // on their region thread here, so restore it directly (remove(uuid) only clears the bookkeeping).
            teams.show(wearer);
            return;
        }
        NametagFormat format = selected.get();
        // A format switch (different format, or the same format with a changed appearance) is a remove-then-show: the
        // appearance is baked into the spawn, and the lib loop keeps text/viewers fresh on the unchanged format, so no
        // in-place format change is needed here.
        if (!format.name().equals(tracked.format().name())
                || !format.appearance().equals(tracked.format().appearance())) {
            remove(wearer.getUniqueId());
            show(wearer);
        }
    }

    /**
     * Force a wearer onto a freshly parsed config generation. Unlike {@link #update}, this always re-shows an existing
     * handle because line text, visibility and refresh cadence may have changed while format name/appearance stayed
     * equal. Must run on the wearer's region thread.
     */
    public void refresh(Player wearer) {
        Objects.requireNonNull(wearer, "wearer");
        remove(wearer.getUniqueId());
        show(wearer);
    }

    /**
     * Remove {@code uuid}'s nametag if it has one, sending the lib's remove packets to every viewer. Clears the
     * vanilla-name-hide bookkeeping (a pure map mutation) but does <em>not</em> touch the board, so it is safe from any
     * thread and is used where a re-show follows that re-hides on the player's region thread (a world change). It does
     * not restore the vanilla name on the board; the call sites that still hold the live player on its region thread do
     * that ({@link #remove(Player)} on quit, {@link #update}'s no-format branch, and module stop) since touching a
     * {@link org.bukkit.scoreboard.Team} off the region thread is unsafe.
     */
    public void remove(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        Tracked tracked = live.remove(uuid);
        if (tracked != null) {
            tracked.handle().remove();
        }
        teams.clear(uuid);
    }

    /**
     * Remove {@code player}'s nametag and drop their stranded name from the hide-team on their current board. The quit
     * handler calls this on the quitting player's region thread, where the player is still online and the board entry is
     * still valid: the main shared board is a server-lifetime singleton whose team entries survive the quit, so unless
     * the entry is removed here it would strand the vanilla name hidden and leak over uptime. Must run on the player's
     * region thread.
     */
    public void remove(Player player) {
        Objects.requireNonNull(player, "player");
        Tracked tracked = live.remove(player.getUniqueId());
        if (tracked != null) {
            tracked.handle().remove();
        }
        // The quit path: the player is leaving, so drop their published position so the snapshot map does not grow
        // over uptime. A world change goes through remove(uuid), which keeps the snapshot. The player is still online
        // and re-publishes on their next reconcile.
        positionSnapshots.remove(player.getUniqueId());
        teams.clear(player);
    }

    /** Remove every tracked nametag now: call on module stop so no nametag leaks. */
    public void removeAll() {
        for (UUID uuid : List.copyOf(live.keySet())) {
            // Restore the vanilla name for any wearer still online before dropping the handle, so a disable/reload
            // does not strand an online player nameless. An offline wearer's hide-team entry was already dropped when
            // they left, so resolving null is a no-op the clear in remove(uuid) covers.
            Player wearer = Bukkit.getPlayer(uuid);
            if (wearer != null) {
                teams.show(wearer);
            }
            remove(uuid);
        }
        // Module stop: drop every published position so a disable/reload leaves no snapshot behind.
        positionSnapshots.clear();
    }

    /** Whether {@code uuid} currently has a tracked nametag (test/observability seam). */
    public boolean isTracked(UUID uuid) {
        return live.containsKey(uuid);
    }

    /** How many wearers are currently tracked. Test/observability seam for the show-guard invariant. */
    int trackedCount() {
        return live.size();
    }

    /**
     * Seed a tracked entry directly, bypassing the lib spawn. Test-only seam so a test can put a wearer into the
     * tracked state to exercise the show-when-tracked guard (which must reconcile, not show a second handle) without
     * driving the NMS-backed lib renderer the unit test cannot stand up.
     */
    void trackForTest(Player wearer, NametagHandle handle, NametagFormat format) {
        live.put(wearer.getUniqueId(), new Tracked(handle, format));
    }

    /**
     * Publish {@code player}'s position to the cull snapshot, the same act {@link #update} performs on the player's
     * own region thread each reconcile. Test-only seam so the eligible-viewer cull can be exercised directly without
     * standing up the full reconcile loop, mirroring production where every online player publishes before a cull runs.
     */
    void snapshotForTest(Player player) {
        snapshotSelf(player);
    }

    /**
     * The format this wearer should get, or empty when none applies. Package-private so the selection rule can be
     * tested directly without spawning a packet nametag.
     */
    Optional<NametagFormat> selectFor(Player wearer) {
        Optional<NametagFormat> selected = config.get().select(conditionContext(wearer));
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        NametagFormat format = selected.get();
        // A format with no lines, or whose show-when gate fails for this wearer, means "no nametag" for them.
        if (format.lines().isEmpty() || !format.visibility().showWhen().matches(conditionContext(wearer))) {
            return Optional.empty();
        }
        return selected;
    }

    // The per-viewer text the lib re-asks for every refresh: resolve the wearer's lines for this specific viewer,
    // reading the live animation frame at call time so the lib's refresh loop animates without a plugin render loop.
    // The lines carry no relational (viewer-aware) placeholder today, so every viewer resolves to identical text and
    // the
    // per-wearer LineCache parses each frame once and replays it to every viewer. The resolution still flows through
    // the
    // per-viewer callback so a relational placeholder added later needs no wiring change. That change would bypass the
    // cache for the placeholder-bearing lines and thread the viewer into the PAPI bridge.
    private PerViewerText perViewerText(NametagFormat format, Player wearer, LineCache lineCache) {
        return viewer -> lineCache.linesFor(animations.tick(), () -> renderLines(format, wearer));
    }

    private List<Component> renderLines(NametagFormat format, Player wearer) {
        long frame = animations.tick();
        List<Component> rendered = new ArrayList<>(format.lines().size());
        for (String line : format.lines()) {
            String withName = line.replace("{player}", wearer.getName());
            rendered.add(HudText.render(wearer.getUniqueId(), animations.resolve(withName, frame)));
        }
        return rendered;
    }

    // The eligible-viewer supplier the lib loop re-reads each refresh on the wearer's region thread: online players
    // other than the wearer, within the format's viewer-distance cull, that the vanish gate (when respected) lets see
    // the wearer, and (unless the format hides on sneak while the wearer sneaks) at all. Returns UUIDs because that
    // is the lib audience type; the underlying cull mirrors what the old per-tick refresh computed.
    private Supplier<Set<UUID>> viewerSupplier(Player wearer, NametagFormat format) {
        return () -> {
            Set<UUID> uuids = new LinkedHashSet<>();
            for (Player viewer : eligibleViewers(wearer, format)) {
                uuids.add(viewer.getUniqueId());
            }
            return uuids;
        };
    }

    // Package-private so the cull is testable without spawning a nametag. See viewerSupplier for the contract.
    List<Player> eligibleViewers(Player wearer, NametagFormat format) {
        NametagVisibility visibility = format.visibility();
        if (visibility.hideWhileSneaking() && wearer.isSneaking()) {
            return List.of();
        }
        double maxBlocks = cullRadiusBlocks(visibility.viewerDistance());
        List<Player> eligible = new ArrayList<>();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(wearer.getUniqueId())) {
                continue;
            }
            if (visibility.respectVanish() && !canSee(viewer, wearer)) {
                continue;
            }
            if (maxBlocks != Double.MAX_VALUE && !withinRange(viewer, wearer, maxBlocks)) {
                continue;
            }
            eligible.add(viewer);
        }
        return eligible;
    }

    private boolean canSee(Player viewer, Player wearer) {
        return vanish.canSee(BukkitRefs.toRef(viewer), BukkitRefs.toRef(wearer));
    }

    /**
     * Whether {@code viewer} is within {@code maxBlocks} of {@code wearer}. The wearer's position is read live: this
     * cull runs on the wearer's own region thread, so that read is region-local, while the viewer's position comes
     * from the off-thread {@link #positionSnapshots} the viewer published on their own thread, never the viewer's live
     * location (a cross-region read on Folia). A viewer with no snapshot yet, or in another world, is out of range.
     */
    private boolean withinRange(Player viewer, Player wearer, double maxBlocks) {
        Position viewerPosition = positionSnapshots.get(viewer.getUniqueId());
        Location wearerLocation = wearer.getLocation();
        if (viewerPosition == null || wearerLocation == null) {
            return false;
        }
        Position wearerPosition = BukkitRefs.toPosition(wearerLocation);
        return wearerPosition.distanceTo(viewerPosition) <= maxBlocks;
    }

    /** Publish {@code player}'s live position to the shared snapshot. Must run on the player's own region thread. */
    private void snapshotSelf(Player player) {
        Location location = player.getLocation();
        if (location != null) {
            positionSnapshots.put(player.getUniqueId(), BukkitRefs.toPosition(location));
        }
    }

    // The cull radius in blocks for the eligible-viewer pass: an absent viewer-distance uses the renderer default
    // radius, an authored 0 disables the cull (Double.MAX_VALUE → every online viewer is a candidate, falling back to
    // the packet appearance view-range for distance fading), and a positive value is that flat block radius.
    private static double cullRadiusBlocks(OptionalDouble viewerDistance) {
        double blocks = viewerDistance.orElse(DEFAULT_VIEWER_DISTANCE_BLOCKS);
        if (blocks <= 0) {
            return Double.MAX_VALUE;
        }
        return blocks;
    }

    private ConditionContext conditionContext(Player player) {
        return new ConditionContext(
                player::hasPermission,
                player.getWorld().getName(),
                player.getGameMode().name(),
                PlaceholderApiSupport.messageBridge(player.getUniqueId()));
    }

    /**
     * A live nametag: the lib {@link NametagHandle} (whose {@code remove()} tears down the entity for every viewer and
     * cancels the lib's refresh task) and the selected {@link NametagFormat} (so a format/appearance change is detected
     * and triggers a re-show).
     */
    private record Tracked(NametagHandle handle, NametagFormat format) {}

    /**
     * A per-wearer, per-frame memo of the rendered lines. The lib's refresh loop calls the per-viewer text callback once
     * per viewer on the wearer's region thread; every viewer resolves to identical lines for a given animation frame, so
     * the first call of a frame renders the lines (one PAPI-expand + MiniMessage parse per line) and caches them keyed by
     * that frame, and every later viewer in the same frame replays the cached list. A new frame (the global animation
     * clock advanced) misses the cache and renders once more, so a frame's parse cost is one regardless of audience size.
     *
     * <p>Not shared across wearers and only touched from one wearer's region thread per show, so the holder needs no
     * synchronisation. A format/appearance change discards this cache with the old handle (the re-show builds a fresh
     * one), so there is no stale-content path.
     */
    private static final class LineCache {

        private static final long NO_FRAME = Long.MIN_VALUE;

        private long cachedFrame = NO_FRAME;
        private List<Component> cachedLines = List.of();

        List<Component> linesFor(long frame, Supplier<List<Component>> render) {
            if (frame != cachedFrame) {
                cachedLines = render.get();
                cachedFrame = frame;
            }
            return cachedLines;
        }
    }
}
