package com.uxplima.uxmessentials.tablist.adapter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationDef;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.display.ConditionParser;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import com.uxplima.uxmessentials.tablist.domain.TablistContent;
import com.uxplima.uxmessentials.tablist.domain.TablistFiller;
import com.uxplima.uxmessentials.tablist.domain.TablistFormat;
import com.uxplima.uxmessentials.tablist.domain.TablistFormatConfig;
import com.uxplima.uxmessentials.tablist.domain.TablistLayout;
import com.uxplima.uxmessentials.tablist.domain.TablistRosterGroup;
import com.uxplima.uxmessentials.tablist.domain.TablistSkinSource;
import com.uxplima.uxmessentials.tablist.domain.TablistSlotRange;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Parses {@code modules/tablist/config.conf} into a {@link TablistFormatConfig} (the named formats the renderer selects
 * among per viewer) plus the global render cadence the timer re-reads each reschedule. Two operator-authored shapes are
 * accepted, mirroring the scoreboard codec.
 *
 * <p><strong>Multiple formats (current shape).</strong> A {@code formats { <name> { … } }} map, one entry per named
 * format:
 *
 * <pre>{@code
 * refresh-ticks = 20            # GLOBAL: how often every viewer is re-rendered
 * formats {
 *   staff {
 *     condition = "permission:uxmessentials.staff"   # see shared/display/ConditionParser for the grammar
 *     priority = 10                                   # higher wins; ties broken by format name (see below)
 *     header = [ "<red>Staff online" ]
 *     footer = [ "<gray>play.example.net" ]
 *     name-format = "<red>[Staff] {player}"           # how this viewer appears to everyone in the tab list
 *     sort-order = 100                                # higher = shown higher in the tab list; must be positive
 *     skin = "player:Notch"                           # OPTIONAL custom tab-row skin; "player:<name>" or "texture:<b64>"
 *     world-blacklist = [ "world_the_end" ]
 *     layout {                                         # OPTIONAL fixed-slot filler grid (see #layout)
 *       direction = "COLUMNS"
 *       fillers = [ { slot = 5, text = "<gray>play.example.net", skin = "player:Notch" } ]
 *       groups = [ { id = "players", slots = [ "21-40" ], text = "<white>{player}" } ]   # needs exact = true
 *       skin = "texture:<b64>"                       # the head every cell wears unless it names its own
 *     }
 *   }
 *   default { condition = "", priority = 0, header = [ "<gold>Welcome" ] }
 * }
 * }</pre>
 *
 * <p><strong>Single tablist (back-compat).</strong> When there is no {@code formats { … }} block but a top-level
 * {@code tablist { header, footer, … }} block exists, it is wrapped as one format named {@code default} with an
 * always-true condition, priority {@code 0}, and no name/order override, reproducing the historical single-tablist
 * behaviour. The global refresh cadence then comes from {@code tablist.refresh-ticks}.
 *
 * <p>An optional top-level {@code animations { <name> { type, frames, interval-ticks, … } }} block declares named
 * animations a header/footer line references as {@code %anim_<name>%}. It is parsed by the shared
 * {@link AnimationDef#parseAll} the scoreboard codec uses, so the animation grammar is identical across both HUD
 * modules and never drifts.
 *
 * <p>Every value is operator content rendered through MiniMessage and the placeholder pipeline later, never a
 * {@code MessageKey}. The parse is tolerant: an absent or non-positive {@code refresh-ticks} falls back to one second so
 * the render timer never busy-spins, a non-positive {@code sort-order} is treated as absent (the API requires a positive
 * order), and a format with neither header nor footer nor a name/order override is dropped rather than applying nothing.
 * An empty or virtual root yields {@link Parsed#inert()}.
 *
 * <p><strong>Tie-break.</strong> {@link TablistFormatConfig#select} resolves a viewer to the highest-priority matching
 * format. HOCON does not preserve the order formats are declared in (its object keys iterate alphabetically), so to keep
 * a priority tie deterministic and reload-stable the codec emits the formats sorted by name; on equal priority the
 * alphabetically-first format name wins. Operators give a format a higher {@code priority} to put it first explicitly.
 */
@NullMarked
final class TablistContentCodec {

    private static final long DEFAULT_REFRESH_TICKS = 20L;

    /** A roster row with no authored text is the occupant's name: the one thing every such row shows. */
    private static final String DEFAULT_GROUP_TEXT = "{player}";

    private static final long MILLIS_PER_TICK = 50L;

    private TablistContentCodec() {}

    /**
     * The parsed tablist config: the named formats the renderer selects among, the named animations the renderer
     * expands {@code %anim_<name>%} tokens against, and the global render cadence the timer re-reads each reschedule.
     */
    record Parsed(TablistFormatConfig formats, List<AnimationDef> animations, Duration refreshInterval) {
        Parsed {
            Objects.requireNonNull(formats, "formats");
            animations = List.copyOf(Objects.requireNonNull(animations, "animations"));
            Objects.requireNonNull(refreshInterval, "refreshInterval");
        }

        /** The do-nothing default an absent or unreadable config yields: no formats, no animations, once a second. */
        static Parsed inert() {
            return new Parsed(
                    TablistFormatConfig.empty(), List.of(), Duration.ofMillis(DEFAULT_REFRESH_TICKS * MILLIS_PER_TICK));
        }
    }

    /** Parse {@code root}; an empty or virtual root yields {@link Parsed#inert()}. {@code log} reports skipped entries. */
    static Parsed read(ConfigurationNode root, Logger log) {
        Objects.requireNonNull(log, "log");
        if (root.virtual() || root.empty()) {
            return Parsed.inert();
        }
        List<AnimationDef> animations = AnimationDef.parseAll(root.node("animations"), log);
        ConfigurationNode formats = root.node("formats");
        if (!formats.virtual() && formats.isMap()) {
            return new Parsed(readFormats(formats, log), animations, refreshInterval(root.node("refresh-ticks")));
        }
        Parsed single = readSingleFormat(root.node("tablist"));
        return new Parsed(single.formats(), animations, single.refreshInterval());
    }

    private static TablistFormatConfig readFormats(ConfigurationNode formats, Logger log) {
        List<TablistFormat> parsed = new ArrayList<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                formats.childrenMap().entrySet()) {
            readFormat(String.valueOf(entry.getKey()), entry.getValue(), log).ifPresent(parsed::add);
        }
        // HOCON does not preserve declaration order, so sort by name to give TablistFormatConfig.select a
        // deterministic,
        // documented tie-break: on equal priority the alphabetically-first format name wins.
        parsed.sort(Comparator.comparing(TablistFormat::name));
        return new TablistFormatConfig(parsed);
    }

    private static Optional<TablistFormat> readFormat(String name, ConfigurationNode node, Logger log) {
        if (node.virtual() || !node.isMap()) {
            return Optional.empty();
        }
        TablistContent content = tablistContent(node);
        Optional<String> nameFormat = optionalString(node.node("name-format"));
        OptionalInt sortOrder = sortOrder(node.node("sort-order"));
        Optional<TablistSkinSource> skin = skinSource(node.node("skin"));
        // Default false, so an unauthored or malformed value leaves the tab unchanged (real players show as before).
        boolean suppressRealPlayers = node.node("suppress-real-players").getBoolean(false);
        TablistLayout layout = layout(node.node("layout"), name, suppressRealPlayers, log);
        // A format that neither shows header/footer nor sets a name, order, skin, paints fillers, nor suppresses real
        // players does nothing; drop. A suppress-only format still alters the tab, so it survives.
        if (content.isBlank()
                && nameFormat.isEmpty()
                && sortOrder.isEmpty()
                && skin.isEmpty()
                && layout.isEmpty()
                && !suppressRealPlayers) {
            return Optional.empty();
        }
        DisplayCondition condition =
                ConditionParser.parse(node.node("condition").getString());
        int priority = node.node("priority").getInt(0);
        return Optional.of(new TablistFormat(
                name, condition, priority, content, nameFormat, sortOrder, skin, layout, suppressRealPlayers));
    }

    /**
     * Parse a format's optional fixed-slot filler grid:
     *
     * <pre>{@code
     * layout {
     *   rows = 20            # cells per column (the standard tab list is 4 columns of 20)
     *   direction = "COLUMNS" # COLUMNS (down each column, the default) or ROWS (across each row)
     *   fillers = [
     *     { slot = 5, text = "<gray>play.example.net", skin = "player:Notch" }
     *     { slot = 6, text = "<gold>Discord: discord.gg/x" }
     *   ]
     * }
     * }</pre>
     *
     * <p>{@code exact=true} materializes every cell, including empty cells, and is accepted only together with
     * {@code suppress-real-players=true}; otherwise the native roster plus a complete synthetic grid would exceed and
     * reshape the client grid. Sparse mode keeps the historical behaviour and still ignores blank filler text.
     */
    private static TablistLayout layout(
            ConfigurationNode node, String formatName, boolean suppressRealPlayers, Logger log) {
        if (node.virtual() || !node.isMap()) {
            return TablistLayout.empty();
        }
        TablistLayout.Direction direction = direction(node.node("direction"));
        int rows = node.node("rows").getInt(TablistLayout.DEFAULT_GRID_ROWS);
        if (rows <= 0) {
            rows = TablistLayout.DEFAULT_GRID_ROWS;
        }
        boolean exact = node.node("exact").getBoolean(false);
        if (exact && !suppressRealPlayers) {
            log.warn("tablist_exact_layout_disabled format={} reason=suppress-real-players-required", formatName);
            exact = false;
        }
        if (exact && TablistLayout.COLUMNS * rows > TablistLayout.MAX_EXACT_SLOTS) {
            log.warn(
                    "tablist_exact_layout_rows_reset format={} rows={} max-slots={}",
                    formatName,
                    rows,
                    TablistLayout.MAX_EXACT_SLOTS);
            rows = TablistLayout.DEFAULT_GRID_ROWS;
        }
        int capacity = TablistLayout.COLUMNS * rows;
        List<TablistFiller> fillers = fillers(node.node("fillers"), formatName, capacity, exact, log);
        List<TablistRosterGroup> groups = groups(node.node("groups"), formatName, capacity, exact, log);
        Optional<TablistSkinSource> skin = skinSource(node.node("skin"));
        if (fillers.isEmpty() && groups.isEmpty() && !exact) {
            return TablistLayout.empty();
        }
        TablistLayout layout = new TablistLayout(fillers, groups, skin, direction, rows, exact);
        if (groups.isEmpty()) {
            return layout;
        }
        try {
            // Building the design is the ownership check: it rejects a cell a filler and a group both claim, two groups
            // that overlap, and a repeated group id. A layout that cannot be planned would paint an ambiguous grid, so
            // the groups are dropped and the fillers alone are kept rather than failing the whole format.
            layout.design(formatName);
            return layout;
        } catch (IllegalArgumentException rejected) {
            log.warn(
                    "tablist_roster_groups_dropped format={} reason={}",
                    formatName,
                    String.valueOf(rejected.getMessage()));
            return new TablistLayout(fillers, direction, rows, exact);
        }
    }

    /**
     * Parse a layout's optional roster groups: the cells the live players are drawn into.
     *
     * <pre>{@code
     * groups = [
     *   { id = "players", slots = [ "21-40" ], text = "<white>{player}", condition = "" }
     * ]
     * }</pre>
     *
     * <p>A group owns cells, so it needs an exact grid; one authored without {@code exact = true} is dropped with a
     * warning rather than silently ignored. A group with no usable slot range, a blank id, or a repeated id is dropped
     * the same way, and the layout keeps the groups that parsed.
     */
    private static List<TablistRosterGroup> groups(
            ConfigurationNode node, String formatName, int capacity, boolean exact, Logger log) {
        if (node.virtual() || !node.isList()) {
            return List.of();
        }
        if (!exact) {
            log.warn("tablist_roster_groups_disabled format={} reason=exact-grid-required", formatName);
            return List.of();
        }
        List<TablistRosterGroup> parsed = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();
        for (ConfigurationNode child : node.childrenList()) {
            group(child, formatName, capacity, seenIds, log).ifPresent(parsed::add);
        }
        return parsed;
    }

    private static Optional<TablistRosterGroup> group(
            ConfigurationNode node, String formatName, int capacity, Set<String> seenIds, Logger log) {
        String id = node.node("id").getString("").trim();
        if (id.isEmpty()) {
            log.warn("tablist_roster_group_skipped format={} reason=blank-id", formatName);
            return Optional.empty();
        }
        if (!seenIds.add(id)) {
            log.warn("tablist_roster_group_skipped format={} group={} reason=duplicate-id", formatName, id);
            return Optional.empty();
        }
        List<TablistSlotRange> ranges = ranges(node.node("slots"), formatName, id, capacity, log);
        if (ranges.isEmpty()) {
            log.warn("tablist_roster_group_skipped format={} group={} reason=no-slots", formatName, id);
            return Optional.empty();
        }
        String text = node.node("text").getString(DEFAULT_GROUP_TEXT);
        DisplayCondition condition =
                ConditionParser.parse(node.node("condition").getString());
        return Optional.of(new TablistRosterGroup(id, ranges, condition, text));
    }

    /** Parse one group's {@code slots} list: {@code "7"} for a single cell, {@code "21-40"} for a run of them. */
    private static List<TablistSlotRange> ranges(
            ConfigurationNode node, String formatName, String groupId, int capacity, Logger log) {
        List<TablistSlotRange> parsed = new ArrayList<>();
        for (String source : strings(node)) {
            try {
                TablistSlotRange range = TablistSlotRange.parse(source);
                if (range.last() > capacity) {
                    log.warn(
                            "tablist_roster_range_skipped format={} group={} range={} reason=out-of-grid capacity={}",
                            formatName,
                            groupId,
                            source,
                            capacity);
                    continue;
                }
                parsed.add(range);
            } catch (IllegalArgumentException rejected) {
                log.warn(
                        "tablist_roster_range_skipped format={} group={} range={} reason=unreadable",
                        formatName,
                        groupId,
                        source);
            }
        }
        return parsed;
    }

    private static List<TablistFiller> fillers(
            ConfigurationNode node, String formatName, int capacity, boolean allowBlank, Logger log) {
        if (node.virtual() || !node.isList()) {
            return List.of();
        }
        List<TablistFiller> parsed = new ArrayList<>();
        Set<Integer> seenSlots = new LinkedHashSet<>();
        for (ConfigurationNode child : node.childrenList()) {
            filler(child, formatName, capacity, allowBlank, seenSlots, log).ifPresent(parsed::add);
        }
        return parsed;
    }

    private static Optional<TablistFiller> filler(
            ConfigurationNode node,
            String formatName,
            int capacity,
            boolean allowBlank,
            Set<Integer> seenSlots,
            Logger log) {
        int slot = node.node("slot").getInt(0);
        String text = node.node("text").getString("");
        if (slot <= 0 || (!allowBlank && text.isBlank())) {
            log.warn("tablist_filler_skipped format={} slot={} reason=invalid-slot-or-text", formatName, slot);
            return Optional.empty();
        }
        if (slot > capacity) {
            // A slot past the grid wraps onto an in-grid cell in ROWS mode (translateSlot is a bijection only within
            // the
            // grid), so two fillers would collide on one client cell. Reject it with the same clear operator feedback
            // as
            // a duplicate slot rather than silently painting one row over another.
            log.warn(
                    "tablist_filler_skipped format={} slot={} reason=slot-out-of-grid capacity={}",
                    formatName,
                    slot,
                    capacity);
            return Optional.empty();
        }
        if (!seenSlots.add(slot)) {
            log.warn("tablist_filler_skipped format={} slot={} reason=duplicate-slot", formatName, slot);
            return Optional.empty();
        }
        return Optional.of(new TablistFiller(slot, text, skinSource(node.node("skin"))));
    }

    private static TablistLayout.Direction direction(ConfigurationNode node) {
        String raw = node.getString("");
        for (TablistLayout.Direction direction : TablistLayout.Direction.values()) {
            if (direction.name().equalsIgnoreCase(raw.trim())) {
                return direction;
            }
        }
        return TablistLayout.Direction.COLUMNS;
    }

    /**
     * Back-compat: wrap a top-level {@code tablist { … }} block as the single implicit {@code default} format with an
     * always-true condition, priority {@code 0}, and no name/order override. A blank block yields no formats. The global
     * refresh cadence comes from {@code tablist.refresh-ticks}. Animations are read separately by {@link #read} and
     * merged in there, so the returned {@code animations} list is always empty.
     */
    private static Parsed readSingleFormat(ConfigurationNode tab) {
        Duration interval = refreshInterval(tab.node("refresh-ticks"));
        TablistContent content = tablistContent(tab);
        if (content.isBlank()) {
            return new Parsed(TablistFormatConfig.empty(), List.of(), interval);
        }
        TablistFormat single = new TablistFormat(
                "default", DisplayCondition.always(), 0, content, Optional.empty(), OptionalInt.empty());
        return new Parsed(new TablistFormatConfig(List.of(single)), List.of(), interval);
    }

    private static TablistContent tablistContent(ConfigurationNode node) {
        return new TablistContent(
                strings(node.node("header")),
                strings(node.node("footer")),
                refreshInterval(node.node("refresh-ticks")),
                worldBlacklist(node.node("world-blacklist")));
    }

    /**
     * Parse a format's optional {@code skin} value, the one tab thing native Paper cannot do. Two prefixes are accepted:
     *
     * <ul>
     *   <li>{@code "texture:<base64>"} or {@code "texture:<base64>:<signature>"}, a Mojang texture property given
     *       directly. The base64 value may itself contain {@code =} padding but no {@code :}, so the part after the first
     *       colon up to an optional second colon is the value and the remainder (if any) the signature;</li>
     *   <li>{@code "player:<name>"}. Copy a named player's skin (read live when online, fetched when offline).</li>
     * </ul>
     *
     * <p>The parse is tolerant like the rest of the codec: an absent, blank, or unrecognised value yields empty (no
     * skin, the native path) rather than failing the format.
     */
    private static Optional<TablistSkinSource> skinSource(ConfigurationNode node) {
        String raw = node.getString("");
        if (raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim();
        if (value.startsWith("player:")) {
            String name = value.substring("player:".length()).trim();
            return name.isBlank() ? Optional.empty() : Optional.of(new TablistSkinSource.PlayerName(name));
        }
        if (value.startsWith("texture:")) {
            return texture(value.substring("texture:".length()));
        }
        return Optional.empty();
    }

    /** Parse the part after {@code texture:} into a {@link TablistSkinSource.Texture}: {@code <base64>[:<signature>]}. */
    private static Optional<TablistSkinSource> texture(String body) {
        int split = body.indexOf(':');
        String value = (split < 0 ? body : body.substring(0, split)).trim();
        if (value.isBlank()) {
            return Optional.empty();
        }
        Optional<String> signature = split < 0
                ? Optional.empty()
                : Optional.of(body.substring(split + 1).trim()).filter(s -> !s.isBlank());
        return Optional.of(new TablistSkinSource.Texture(value, signature));
    }

    private static OptionalInt sortOrder(ConfigurationNode node) {
        if (node.virtual()) {
            return OptionalInt.empty();
        }
        int order = node.getInt(0);
        // The Paper API requires a positive order; treat anything non-positive as "leave the vanilla order untouched".
        return order > 0 ? OptionalInt.of(order) : OptionalInt.empty();
    }

    private static Duration refreshInterval(ConfigurationNode node) {
        long ticks = node.getLong(DEFAULT_REFRESH_TICKS);
        if (ticks <= 0L) {
            ticks = DEFAULT_REFRESH_TICKS;
        }
        return Duration.ofMillis(ticks * MILLIS_PER_TICK);
    }

    private static Set<String> worldBlacklist(ConfigurationNode node) {
        return new LinkedHashSet<>(strings(node));
    }

    private static List<String> strings(ConfigurationNode node) {
        if (node.virtual() || !node.isList()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            String value = child.getString();
            // A blank entry is kept so an operator can author a spacer line; a missing value is skipped.
            if (value != null) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static Optional<String> optionalString(ConfigurationNode node) {
        String value = node.getString("");
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
