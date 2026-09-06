package com.uxplima.uxmessentials.tablist.adapter.outbound;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;

import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.BuiltinTokens;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.ConditionContext;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.tablist.domain.TablistContent;
import com.uxplima.uxmessentials.tablist.domain.TablistFiller;
import com.uxplima.uxmessentials.tablist.domain.TablistFormat;
import com.uxplima.uxmessentials.tablist.domain.TablistFormatConfig;
import com.uxplima.uxmessentials.tablist.domain.TablistLayout;
import com.uxplima.uxmessentials.tablist.domain.TablistLayoutDesign;
import com.uxplima.uxmessentials.tablist.domain.TablistRosterGroup;
import com.uxplima.uxmessentials.tablist.domain.VirtualTabCell;
import com.uxplima.uxmessentials.tablist.domain.VirtualTabGrid;
import com.uxplima.uxmessentials.tablist.domain.VirtualTabPlanner;
import com.uxplima.uxmlib.hud.Tablist;
import com.uxplima.uxmlib.packet.tablist.TabListPackets;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Renders the per-player tablist from the live {@link TablistFormatConfig}, dogfooding uxmLib's {@link Tablist}. Each
 * viewer is offered the format {@link TablistFormatConfig#select selected} for them: the highest-priority
 * {@link TablistFormat} whose {@link com.uxplima.uxmessentials.shared.display.DisplayCondition condition} matches, the
 * condition evaluated against a {@link ConditionContext} built from the live player (permission check, world, gamemode,
 * per-viewer PlaceholderAPI bridge) so a {@code %papi% >= 10} or {@code permission:uxmessentials.staff} condition sees
 * real values. When no format matches, the header/footer is cleared and any list name/order/skin/fillers this renderer
 * applied is reset to vanilla.
 *
 * <p>A selected format contributes four independent things:
 *
 * <ul>
 *   <li><strong>Header/footer.</strong> The {@link TablistContent} line lists, each source rendered through the shared
 *       pipeline ({@link AnimationRegistry} {@code %anim_<name>%} expansion → {@link BuiltinTokens} {@code {player}} /
 *       {@code {online}} / {@code {world}} → {@link HudText} PlaceholderAPI + MiniMessage) and joined with newlines. An
 *       <em>empty</em> header and footer (a name-only / order-only format) is left untouched rather than sent, because
 *       uxmLib's {@link Tablist#set} ships both in one native call and an empty pair would wipe whatever vanilla or
 *       another plugin set; {@link #appliedHeaderFooter} tracks who this renderer last sent one to so a switch away from
 *       a header-having format clears its own header/footer instead of leaving it stale.</li>
 *   <li><strong>Name, order, and skin.</strong> How the real player themselves appears in the tab, the list name, the
 *       sort order, and, when the format carries one, a custom-skin texture (the one thing native Paper cannot do, so it
 *       goes through a packet). Delegated to {@link RealPlayerRowPainter}, called from {@link #renderFor},
 *       {@link #clear}, {@link #forget}, and {@link #repaintSkinsFor}. The real players keep the early slots: the painter
 *       gives them the layout's {@link TablistLayout#realPlayerOrder() real-player order} (above every filler) unless the
 *       format authored an explicit sort order, which wins.</li>
 *   <li><strong>Filler grid.</strong> A {@link TablistLayout} of synthetic {@link TablistFiller} rows filling the cells
 *       the real players do not, delegated to {@link FillerPainter} (called from {@link #renderFor}, {@link #clear}, and
 *       {@link #forget}). Real-player suppression is deliberately not done; real players still show.</li>
 * </ul>
 *
 * <p>{@link #renderFor(Player)} touches the live player, so the caller must invoke it on the player's region/entity
 * thread, the render timer and the connection listener both hop there first.
 */
@NullMarked
public final class TablistRenderer {

    private final Tablist tablist;

    /**
     * Every player a roster group may draw. The same supplier the row painter and the suppression fan out over, so a
     * grid draws the players the rest of the renderer already knows about.
     */
    private final Supplier<? extends Collection<? extends Player>> roster;

    private final Supplier<TablistFormatConfig> formats;
    private final AnimationRegistry animations;

    /**
     * Whether this renderer currently has a header/footer applied for each player, keyed by player UUID. A {@code true}
     * value means the last selected format authored a header/footer and we sent it, so a switch to a name-only/order-only
     * format must clear it rather than leave it stale. An absent key means we never sent one, so a blank-content format
     * leaves the player's tab untouched. A {@link ConcurrentHashMap} guards the connect-while-rendering race and keeps
     * the project's "every player-keyed map is concurrent" convention; every mutation otherwise runs on the player's
     * region/entity thread.
     */
    private final Map<UUID, Boolean> appliedHeaderFooter = new ConcurrentHashMap<>();

    /**
     * The name of the format each player is currently drawn from, keyed by player UUID, or absent when they are
     * drawn from none (no format matched, or the content is suppressed in their world). Written on the same region
     * thread as the paint it describes and read by the {@code tablist_format} placeholder. It records what was
     * applied rather than re-running the selector, because re-selecting would evaluate the format conditions a
     * second time and a condition may itself expand a placeholder.
     */
    private final Map<UUID, String> appliedFormat = new ConcurrentHashMap<>();

    /** The format {@code who}'s tab is currently drawn from, or empty when this renderer is drawing them none. */
    public Optional<String> appliedFormat(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return Optional.ofNullable(appliedFormat.get(who.uuid()));
    }

    /** Paints the real player's name/order/skin row a selected format may carry; see {@link RealPlayerRowPainter}. */
    private final RealPlayerRowPainter rowPainter;

    /** Paints the fixed-slot {@link TablistLayout filler grid} a selected format may carry; see {@link FillerPainter}. */
    private final FillerPainter fillerPainter;

    /** The design name the planner reports a rejected cell under; operator-facing only, one renderer, one grid. */
    private static final String DESIGN_ID = "tablist";

    /** The seating planned for the current tick; see {@link #seating(TablistLayout, long)}. Region threads read it. */
    private final AtomicReference<Seating> seating = new AtomicReference<>();

    /**
     * The opt-in "suppress real players" mechanism, or {@code null} when the packet-interception pipeline is not wired
     * (every constructor but the suppression-enabled one). A {@code null} suppression means the {@code suppress-real-
     * players} flag is inert, the tab is never rewritten, so the gate is default-off and the historical behaviour
     * stands; this keeps the renderer's many existing call sites unchanged.
     */
    private final @Nullable TablistSuppression suppression;

    /** Build a renderer with the full packet path. {@code viewers} supplies who a skin packet is broadcast to. */
    public TablistRenderer(
            Supplier<TablistFormatConfig> formats,
            AnimationRegistry animations,
            TabListPackets packets,
            TablistSkinResolver skinResolver,
            Supplier<? extends Collection<? extends Player>> viewers,
            Scheduler scheduler) {
        this(formats, animations, packets, skinResolver, viewers, scheduler, null);
    }

    /** Build a renderer with the full packet path plus the {@code suppression} collaborator driving TAB-C. */
    public TablistRenderer(
            Supplier<TablistFormatConfig> formats,
            AnimationRegistry animations,
            TabListPackets packets,
            TablistSkinResolver skinResolver,
            Supplier<? extends Collection<? extends Player>> viewers,
            Scheduler scheduler,
            @Nullable TablistSuppression suppression) {
        this.formats = Objects.requireNonNull(formats, "formats");
        this.animations = Objects.requireNonNull(animations, "animations");
        Objects.requireNonNull(packets, "packets");
        Objects.requireNonNull(skinResolver, "skinResolver");
        Objects.requireNonNull(viewers, "viewers");
        Objects.requireNonNull(scheduler, "scheduler");
        this.tablist = new Tablist();
        this.roster = viewers;
        this.rowPainter =
                new RealPlayerRowPainter(packets, skinResolver, this::render, animations::tick, viewers, scheduler);
        this.fillerPainter = new FillerPainter(packets, skinResolver, this::render);
        this.suppression = suppression;
    }

    /** Build a renderer whose viewers are every online player, the production fan-out. */
    public TablistRenderer(
            Supplier<TablistFormatConfig> formats,
            AnimationRegistry animations,
            TabListPackets packets,
            TablistSkinResolver skinResolver,
            Scheduler scheduler) {
        this(formats, animations, packets, skinResolver, Bukkit::getOnlinePlayers, scheduler, null);
    }

    /** Render (or clear) {@code player}'s tablist from the selected format. Must run on the player's region thread. */
    public void renderFor(Player player) {
        Objects.requireNonNull(player, "player");
        ConditionContext ctx = conditionContext(player);
        // Capture the global animation tick once for this paint, so the header, footer, and name format all read the
        // same frame: the render task steps the clock once per loop tick before this fan-out.
        long tick = animations.tick();
        Optional<TablistFormat> selected = formats.get().select(ctx);
        if (selected.isEmpty()) {
            clear(player);
            return;
        }
        TablistFormat format = selected.get();
        TablistContent content = format.content();
        if (content.suppressedIn(player.getWorld().getName())) {
            clear(player);
            return;
        }
        appliedFormat.put(player.getUniqueId(), format.name());
        applyHeaderFooter(player, content, tick);
        // Where the roster groups seat the players this tick. The seating is the same for every viewer, so it is
        // planned once per tick and read here: a seated player is drawn by their OWN tab entry, given the cell's list
        // order and the group's text, which is how a real row lands in the grid without a synthetic row beside it.
        Seating seating = seating(format.layout(), tick);
        rowPainter.applyRow(player, format, seat(seating, player, format.layout()), tick);
        // Reconcile the opt-in suppress mode BEFORE the fillers are painted, priming the protected-id snapshot with the
        // ids the layout is about to paint. The interceptor is live for an already-suppressed viewer, so a filler whose
        // ADD_PLAYER crosses it must already be in the snapshot or it would be force-unlisted on its first (and, thanks
        // to the per-cell flicker guard, only) paint and stay hidden. Priming from the planned ids closes that window
        // for a layout that grows mid-suppression; the entering edge is unaffected because the snapshot is a superset.
        if (suppression != null) {
            suppression.apply(player, format.suppressRealPlayers(), keptEntries(player, format.layout(), seating));
        }
        fillerPainter.applyFillers(player, format.layout(), seating.slots(), tick);
    }

    /** The seat {@code player} holds this tick, or {@code null} when no roster group seated them. */
    private RealPlayerRowPainter.@Nullable RowSeat seat(Seating seating, Player player, TablistLayout layout) {
        RosterSeat seated = seating.seats().get(player.getUniqueId());
        if (seated == null) {
            return null;
        }
        int order = TablistLayout.slotToListOrder(seated.slot(), layout.direction(), layout.gridRows());
        return new RealPlayerRowPainter.RowSeat(seated.text(), order);
    }

    /**
     * The tab entries a suppress mode must keep listed for {@code viewer}: the filler cells the layout is about to
     * paint, plus every player a roster group seated, because a seated player is drawn by their own entry and hiding it
     * would empty the cell they sit in.
     */
    private Set<UUID> keptEntries(Player viewer, TablistLayout layout, Seating seating) {
        Set<UUID> kept = new java.util.HashSet<>(fillerPainter.plannedFillerIds(viewer, layout, seating.slots()));
        kept.addAll(seating.seats().keySet());
        return kept;
    }

    /**
     * The roster seating of {@code layout} for this tick, planned once and reused across the fan-out. The seating reads
     * only the online roster and each group's condition, never the viewer, so every viewer sees the same player in the
     * same cell; the memo therefore keys on the render tick and the layout instance a config load produced.
     */
    private Seating seating(TablistLayout layout, long tick) {
        Seating memo = seating.get();
        if (memo != null && memo.tick() == tick && memo.layout() == layout) {
            return memo;
        }
        Seating planned = planSeating(layout, tick);
        seating.set(planned);
        return planned;
    }

    /**
     * Plan which player sits in which cell. A layout with no group seats nobody. A design the planner rejects (the
     * codec normally catches this first) seats nobody either, so the grid falls back to its fillers and the render path
     * never throws.
     */
    private Seating planSeating(TablistLayout layout, long tick) {
        if (!layout.hasGroups()) {
            return new Seating(tick, layout, Map.of(), Set.of());
        }
        TablistLayoutDesign design;
        try {
            design = layout.design(DESIGN_ID);
        } catch (IllegalArgumentException rejected) {
            return new Seating(tick, layout, Map.of(), Set.of());
        }
        Map<String, TablistRosterGroup> groups = new LinkedHashMap<>();
        for (TablistRosterGroup group : layout.groups()) {
            groups.put(group.id(), group);
        }
        VirtualTabGrid<Player> grid = VirtualTabPlanner.plan(design, occupants(layout), Player::getUniqueId);
        Map<UUID, RosterSeat> seats = new LinkedHashMap<>();
        Set<Integer> slots = new java.util.HashSet<>();
        for (VirtualTabCell<Player> cell : grid.cells()) {
            if (!(cell.content() instanceof VirtualTabCell.Player<Player> occupied)) {
                continue;
            }
            TablistRosterGroup group = groups.get(occupied.groupId());
            if (group == null) {
                continue;
            }
            seats.put(occupied.occupant().getUniqueId(), new RosterSeat(cell.slot(), group.text()));
            slots.add(cell.slot());
        }
        return new Seating(tick, layout, Map.copyOf(seats), Set.copyOf(slots));
    }

    /** The cell one player was seated in, and the text of the group that seated them. */
    private record RosterSeat(int slot, String text) {}

    /**
     * The seating planned for one (tick, layout): who sits where, and which cells are taken. Held whole so the filler
     * painter can skip the taken cells and the suppress mode can keep the seated players listed.
     */
    private record Seating(long tick, TablistLayout layout, Map<UUID, RosterSeat> seats, Set<Integer> slots) {}

    /**
     * The candidates of each roster group in {@code layout}, in the order the grid fills its cells: every online player
     * whose condition matches, by name. A layout with no group returns nothing and the painter never asks. The
     * condition is evaluated against the <em>candidate</em>, so a staff group holds the staff whoever is looking, and a
     * viewer's own row is drawn by the group that matches them, like anybody else's.
     */
    private Map<String, List<Player>> occupants(TablistLayout layout) {
        if (!layout.hasGroups()) {
            return Map.of();
        }
        List<Player> candidates = new ArrayList<>(roster.get());
        candidates.removeIf(candidate -> !candidate.isOnline());
        candidates.sort(Comparator.comparing(candidate -> candidate.getName().toLowerCase(Locale.ROOT)));
        Map<String, List<Player>> byGroup = new LinkedHashMap<>();
        for (TablistRosterGroup group : layout.groups()) {
            List<Player> matching = new ArrayList<>(candidates.size());
            for (Player candidate : candidates) {
                if (group.condition().matches(conditionContext(candidate))) {
                    matching.add(candidate);
                }
            }
            byGroup.put(group.id(), matching);
        }
        return byGroup;
    }

    /**
     * Re-send every currently-skinned online player's packet entry to a single newly-joined {@code viewer} so a late
     * joiner sees the custom skins the steady-state tick would not repaint for them, delegated to
     * {@link RealPlayerRowPainter#repaintSkinsFor}. Native Paper replicates a player's list name and order to every
     * viewer including late joiners, but the skin packet does not, so without this the joiner would see real skins. Must
     * run on the joining {@code viewer}'s region/entity thread, like {@link #renderFor(Player)}, the connection listener
     * hops there first.
     */
    public void repaintSkinsFor(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        rowPainter.repaintSkinsFor(viewer);
    }

    /** Clear {@code player}'s header/footer and reset any list name/order/skin/fillers this renderer applied. */
    public void clear(Player player) {
        Objects.requireNonNull(player, "player");
        tablist.clear(player);
        appliedHeaderFooter.remove(player.getUniqueId());
        appliedFormat.remove(player.getUniqueId());
        fillerPainter.clear(player);
        rowPainter.resetRow(player);
        // Take the viewer out of suppress mode and relist the real players so a cleared tab is never left synthetic.
        if (suppression != null) {
            suppression.disable(player);
        }
    }

    /** Clear {@code player}'s header/footer and forget their name/order/skin/filler tracking on quit. */
    public void forget(Player player) {
        Objects.requireNonNull(player, "player");
        tablist.clear(player);
        appliedHeaderFooter.remove(player.getUniqueId());
        appliedFormat.remove(player.getUniqueId());
        // On quit the player's connection is gone; just drop the tracking. A native reset packet to a closing channel
        // is a no-op, so revert is skipped: only the tracking is forgotten so a relog re-paints from scratch.
        fillerPainter.forget(player);
        rowPainter.forget(player);
        // Drop the suppress tracking and eject the interceptor without a relist packet to the closing channel.
        if (suppression != null) {
            suppression.forget(player);
        }
    }

    /**
     * Reconcile the player's tab header/footer with the selected format's {@link TablistContent}. An authored
     * (non-blank) content is sent and the player marked as carrying this renderer's header/footer. A blank content (a
     * name-only / order-only format) sends nothing. UxmLib's {@link Tablist#set} would otherwise wipe the player's
     * existing header/footer, but if this renderer previously sent one for the player (a switch from a header-having
     * format) it clears its own to avoid leaving a stale header/footer behind.
     */
    private void applyHeaderFooter(Player player, TablistContent content, long tick) {
        UUID uuid = player.getUniqueId();
        if (content.isBlank()) {
            if (appliedHeaderFooter.remove(uuid) != null) {
                tablist.clear(player);
            }
            return;
        }
        tablist.set(player, joinLines(player, content.header(), tick), joinLines(player, content.footer(), tick));
        appliedHeaderFooter.put(uuid, Boolean.TRUE);
    }

    /**
     * Gather everything a format's condition needs from the live player: their permission check, world and gamemode
     * names, and the per-viewer PlaceholderAPI bridge so a {@code %papi%}-comparison condition expands the same way the
     * rendered lines do. Copied from the scoreboard renderer so both HUD modules select formats identically.
     */
    private ConditionContext conditionContext(Player player) {
        return new ConditionContext(
                player::hasPermission,
                player.getWorld().getName(),
                player.getGameMode().name(),
                PlaceholderApiSupport.messageBridge(player.getUniqueId()));
    }

    private Component joinLines(Player player, List<String> sources, long tick) {
        return Component.join(JoinConfiguration.newlines(), renderAll(player, sources, tick));
    }

    private List<Component> renderAll(Player player, List<String> sources, long tick) {
        List<Component> rendered = new ArrayList<>(sources.size());
        for (String source : sources) {
            rendered.add(render(player, source, tick));
        }
        return rendered;
    }

    private Component render(Player player, String source, long tick) {
        // Built-in {tokens} ({player}, {online}, {world}, …) resolve here off the live player, BEFORE the
        // PlaceholderAPI
        // bridge and MiniMessage, so the shipped header/footer/name-format show real values with or without
        // PlaceholderAPI. The animation %anim_<name>% pass runs first so a frame may itself carry tokens.
        String withTokens = BuiltinTokens.apply(player, animations.resolve(source, tick));
        return HudText.render(player.getUniqueId(), withTokens);
    }
}
