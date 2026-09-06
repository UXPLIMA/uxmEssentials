package com.uxplima.uxmessentials.custommenus.adapter.convert;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Converts a zMenu inventory YAML into an equivalent uxmEssentials HOCON menu spec, so an operator migrating off
 * zMenu keeps their menus. It reads the well-known zMenu surface, the top-level {@code name} / {@code size} and each
 * {@code items.<id>} with its inline {@code item} block (material / name / lore / amount), its {@code slot} /
 * {@code slots}, its default {@code actions} list, and its {@code click-requirement} gates, and emits the
 * {@code title} / {@code rows} / {@code items { … }} shape
 * {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader} loads.
 *
 * <p>zMenu actions and requirements are <em>typed maps</em> (each a {@code - type: <t>} entry with type-specific
 * fields), not the bracketed {@code [tag] arg} strings DeluxeMenus uses. Each action type maps onto one or more of
 * our registered vocabulary refs. A {@code messages} list of two lines becomes two {@code message:} refs, a
 * {@code data} action becomes a {@code data-add} / {@code data-sub} / {@code data-set} / {@code data-remove} ref, and
 * so on. The refs are written in our engine's bare {@code id:value} form (not zMenu's {@code [type]}), the same form
 * the DeluxeMenus converter emits, so the output re-enters the engine through the ordinary {@code menus/} loader.
 *
 * <p>The conversion covers the common surface and degrades gracefully everywhere else: an unknown action type becomes
 * a best-effort {@code console} command when it carries a command-ish field (else it is skipped), an unmappable
 * requirement (a JavaScript condition, a type we have no condition for) is dropped, and a {@code pattern}-backed item
 * (whose expansion needs the referenced pattern file this slice does not read) is skipped whole. Every such
 * compromise is recorded as a {@link ConversionResult#warnings() warning} the caller logs, never a thrown exception.
 * Only a fundamentally unparsable YAML document raises, which the calling service catches per file so one bad export
 * never aborts a whole directory convert.
 *
 * <p>zMenu colour codes ({@code &a}, {@code #77ff77}) and PlaceholderAPI tokens ({@code %player_name%}) are carried
 * through verbatim: our renderer resolves MiniMessage and PAPI, so a placeholder keeps working, while a legacy code
 * is left as written (an operator may want to move it to MiniMessage) and warned about once. The whole class is
 * Configurate-only and touches no Bukkit type, so it is exercised by plain JUnit.
 */
public final class ZMenuConverter {

    /** The number of slots in one inventory row; a zMenu {@code size} divided by this is our {@code rows}. */
    private static final int SLOTS_PER_ROW = 9;

    /** A chest tops out at six rows; a zMenu size beyond {@code 54} is clamped to this with a warning. */
    private static final int MAX_ROWS = 6;

    /** The fallback row count for a menu that declares no {@code size}. */
    private static final int DEFAULT_ROWS = 6;

    /** A {@code #rrggbb} hex colour code; carried through verbatim like {@code &} codes but warned about once. */
    private static final Pattern HEX_COLOUR = Pattern.compile("#[0-9a-fA-F]{6}");

    /** The warning emitted once when a menu still carries legacy colour codes the operator may want to move. */
    private static final String LEGACY_COLOUR_WARNING =
            "menu uses legacy '&' or '#rrggbb' colour codes; they still render, but consider moving them to MiniMessage";

    /**
     * Convert a zMenu inventory YAML document into our HOCON spec text plus the warnings the conversion accrued. A
     * fundamentally unparsable YAML document raises {@link IllegalArgumentException}; every recoverable compromise is
     * a warning, not a throw.
     */
    public ConversionResult convert(String zMenuYaml) {
        Objects.requireNonNull(zMenuYaml, "zMenuYaml");
        ConfigurationNode source = parseYaml(zMenuYaml);
        List<String> warnings = new ArrayList<>();
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder().build();
        CommentedConfigurationNode spec = loader.createNode();
        buildSpec(source, spec, warnings);
        return new ConversionResult(render(spec), List.copyOf(warnings));
    }

    /** Populate our spec node from the zMenu source: title, rows, and items. */
    private void buildSpec(ConfigurationNode source, CommentedConfigurationNode spec, List<String> warnings) {
        try {
            spec.node("title").set(source.node("name").getString(""));
            spec.node("rows").set(rows(source, warnings));
            buildItems(source.node("items"), spec.node("items"), warnings);
        } catch (SerializationException failure) {
            throw new IllegalStateException("failed to build converted menu spec", failure);
        }
    }

    /** The zMenu {@code size} as a clamped row count: absent → six rows, a size beyond 54 → six rows + a warning. */
    private int rows(ConfigurationNode source, List<String> warnings) {
        ConfigurationNode size = source.node("size");
        if (size.virtual() || size.isNull()) {
            return DEFAULT_ROWS;
        }
        int declared = size.getInt(DEFAULT_ROWS * SLOTS_PER_ROW);
        if (declared > MAX_ROWS * SLOTS_PER_ROW) {
            warnings.add("size " + declared + " exceeds a chest maximum; clamped to " + MAX_ROWS + " rows");
        }
        return Math.max(1, Math.min(MAX_ROWS, declared / SLOTS_PER_ROW));
    }

    /** Convert every {@code items.<id>} block; a {@code pattern}-backed item is skipped whole with a warning. */
    private void buildItems(ConfigurationNode items, CommentedConfigurationNode out, List<String> warnings)
            throws SerializationException {
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                items.childrenMap().entrySet()) {
            String id = String.valueOf(entry.getKey());
            ConfigurationNode item = entry.getValue();
            if (isPattern(item)) {
                warnings.add("item '" + id + "' uses a zMenu pattern; convert the pattern manually");
                continue;
            }
            buildItem(id, item, out.node(id), warnings);
        }
    }

    /** Whether an item delegates its appearance to a zMenu {@code pattern} file rather than an inline {@code item}. */
    private static boolean isPattern(ConfigurationNode item) {
        ConfigurationNode pattern = item.node("pattern");
        return !pattern.virtual() && !pattern.isNull();
    }

    /** Convert one inline zMenu item: its {@code item} block, slots, priority, default actions and click gates. */
    private void buildItem(String id, ConfigurationNode item, CommentedConfigurationNode out, List<String> warnings)
            throws SerializationException {
        ConfigurationNode block = item.node("item");
        writeItemBlock(block, out, warnings);
        if (!block.node("amount").virtual()) {
            out.node("decor", "amount").set(block.node("amount").getInt(1));
        }
        writeSlots(item, out);
        if (!item.node("priority").virtual()) {
            out.node("priority").set(item.node("priority").getInt(0));
        }
        if (!item.node("buttons").virtual() && !item.node("buttons").isNull()) {
            warnings.add("item '" + id + "' has zMenu sub-buttons; only its default appearance was converted");
        }
        buildDefaultActions(item.node("actions"), out, warnings);
        buildClickRequirements(item.node("click-requirement"), out, warnings);
    }

    /** Copy the {@code item} block's material / name / lore verbatim and warn once about legacy colour codes. */
    private void writeItemBlock(ConfigurationNode block, CommentedConfigurationNode out, List<String> warnings)
            throws SerializationException {
        setIfPresent(
                block.node("material"),
                out.node("material"),
                block.node("material").getString(""));
        setIfPresent(block.node("name"), out.node("name"), block.node("name").getString(""));
        List<String> lore = stringList(block.node("lore"));
        if (!lore.isEmpty()) {
            out.node("lore").set(lore);
        }
        warnLegacyOnce(block.node("name").getString(""), lore, warnings);
    }

    /** Record the legacy-colour warning at most once when a name or any lore line still carries {@code &}/{@code #hex}. */
    private void warnLegacyOnce(String name, List<String> lore, List<String> warnings) {
        boolean legacy = hasLegacy(name) || lore.stream().anyMatch(ZMenuConverter::hasLegacy);
        if (legacy && !warnings.contains(LEGACY_COLOUR_WARNING)) {
            warnings.add(LEGACY_COLOUR_WARNING);
        }
    }

    /** Whether a line carries a legacy {@code &} code or a {@code #rrggbb} hex colour our renderer still resolves. */
    private static boolean hasLegacy(String line) {
        return line.contains("&") || HEX_COLOUR.matcher(line).find();
    }

    /** Copy the item's {@code slot}/{@code slots} (int or list) onto our {@code slots} list. */
    private void writeSlots(ConfigurationNode item, CommentedConfigurationNode out) throws SerializationException {
        ConfigurationNode slots = item.node("slots");
        if (!slots.virtual() && !slots.isNull()) {
            out.node("slots").set(slots.isList() ? stringList(slots) : List.of(slots.getString("")));
            return;
        }
        ConfigurationNode slot = item.node("slot");
        if (!slot.virtual() && !slot.isNull()) {
            out.node("slots").set(List.of(String.valueOf(slot.getInt(0))));
        }
    }

    /** Map the item's default {@code actions} list onto {@code click.any.actions}: the actions every click runs. */
    private void buildDefaultActions(ConfigurationNode actions, CommentedConfigurationNode out, List<String> warnings)
            throws SerializationException {
        if (actions.virtual() || actions.isNull()) {
            return;
        }
        List<String> refs = mapActions(actions, warnings);
        if (!refs.isEmpty()) {
            out.node("click", "any", "actions").set(refs);
        }
    }

    /** Map every named {@code click-requirement} block onto the click gestures its {@code clicks} restricts it to. */
    private void buildClickRequirements(
            ConfigurationNode clickReq, CommentedConfigurationNode out, List<String> warnings)
            throws SerializationException {
        if (clickReq.virtual() || clickReq.isNull()) {
            return;
        }
        for (ConfigurationNode block : clickReq.childrenMap().values()) {
            applyClickRequirement(block, out, warnings);
        }
    }

    /** Fold one requirement block's conditions, success/deny actions and minimum onto each gesture it restricts. */
    private void applyClickRequirement(ConfigurationNode block, CommentedConfigurationNode out, List<String> warnings)
            throws SerializationException {
        List<String> conditions = mapRequirements(block.node("requirements"), block.node("requirement"), warnings);
        List<String> success = mapActions(block.node("success"), warnings);
        List<String> deny = mapActions(block.node("deny"), warnings);
        int minimum = block.node("minimumRequirement")
                .getInt(block.node("minimum-requirement").getInt(0));
        for (String gesture : gestures(block.node("clicks"), warnings)) {
            writeGesture(out.node("click", gesture), success, conditions, deny, minimum);
        }
    }

    /** Write one gesture, merging its success/deny onto any default actions already on it and adding the gate. */
    private void writeGesture(
            CommentedConfigurationNode node,
            List<String> actions,
            List<String> conditions,
            List<String> deny,
            int minimum)
            throws SerializationException {
        List<String> mergedActions = merge(stringList(node.node("actions")), actions);
        if (!mergedActions.isEmpty()) {
            node.node("actions").set(mergedActions);
        }
        if (!conditions.isEmpty()) {
            node.node("requirements").set(conditions);
        }
        List<String> mergedDeny = merge(stringList(node.node("deny")), deny);
        if (!mergedDeny.isEmpty()) {
            node.node("deny").set(mergedDeny);
        }
        if (minimum > 0) {
            node.node("minimum").set(minimum);
        }
    }

    /** The HOCON gesture keys a {@code clicks} list restricts to; an absent/empty list is the {@code any} gesture. */
    private List<String> gestures(ConfigurationNode clicksNode, List<String> warnings) {
        List<String> clicks = stringList(clicksNode);
        if (clicks.isEmpty()) {
            return List.of("any");
        }
        List<String> result = new ArrayList<>();
        for (String click : clicks) {
            String gesture = gestureFor(click);
            if (gesture == null) {
                warnings.add("skipped unknown click type '" + click + "'");
            } else if (!result.contains(gesture)) {
                result.add(gesture);
            }
        }
        return result;
    }

    /** Map one zMenu click type onto our HOCON gesture key, or {@code null} when it is one we do not model. */
    private static @Nullable String gestureFor(String click) {
        return switch (click.strip().toUpperCase(Locale.ROOT)) {
            case "ALL", "" -> "any";
            case "LEFT", "LEFT_CLICK" -> "left";
            case "RIGHT", "RIGHT_CLICK" -> "right";
            case "SHIFT_LEFT", "SHIFT_LEFT_CLICK" -> "shift_left";
            case "SHIFT_RIGHT", "SHIFT_RIGHT_CLICK" -> "shift_right";
            case "MIDDLE", "MIDDLE_CLICK" -> "middle";
            default -> null;
        };
    }

    // --- actions ------------------------------------------------------------------------------------------------

    /** Map a list of typed zMenu action maps onto our action refs, expanding a multi-value action to several refs. */
    private List<String> mapActions(ConfigurationNode actions, List<String> warnings) {
        List<String> refs = new ArrayList<>();
        for (ConfigurationNode action : actions.childrenList()) {
            refs.addAll(mapAction(action, warnings));
        }
        return refs;
    }

    /**
     * Map one typed zMenu action map onto our action refs. A multi-value action expands to one ref per value (a
     * {@code messages: [a, b]} becomes {@code [message:a, message:b]}); an unknown type falls back to a best-effort
     * {@code console} command when it carries a command-ish field, else it is skipped. Both are warned about.
     */
    private List<String> mapAction(ConfigurationNode action, List<String> warnings) {
        String type = action.node("type").getString("").strip().toLowerCase(Locale.ROOT);
        return switch (canonicalType(type)) {
            case "message" -> prefixEach("message", firstList(action.node("messages"), action.node("message")));
            case "broadcast" -> prefixEach("broadcast", firstList(action.node("messages"), action.node("message")));
            case "console" -> prefixEach("console", firstList(action.node("commands"), action.node("command")));
            case "player" -> prefixEach("command", firstList(action.node("commands"), action.node("command")));
            case "data" -> dataRefs(action, warnings);
            case "refresh" -> List.of("refresh");
            case "inventory" -> openRef(action);
            case "connect" -> List.of("connect:" + serverName(action));
            case "sound" ->
                List.of("sound:" + action.node("sound").getString("").strip());
            case "close" -> List.of("close");
            default -> unknownAction(action, type, warnings);
        };
    }

    /** Collapse the many zMenu spellings and aliases of an action type onto the canonical head this converter maps. */
    private static String canonicalType(String type) {
        return switch (type) {
            case "message", "messages" -> "message";
            case "broadcast" -> "broadcast";
            case "console",
                    "console_command",
                    "console_commands",
                    "console command",
                    "console commands",
                    "console-command",
                    "console-commands",
                    "command",
                    "commands" -> "console";
            case "player",
                    "player_command",
                    "player_commands",
                    "player command",
                    "player commands",
                    "player-command",
                    "player-commands" -> "player";
            case "data" -> "data";
            case "refresh", "refresh-inventory", "refresh_inventory", "refresh inventory", "ri" -> "refresh";
            case "inventory", "inv" -> "inventory";
            case "connect" -> "connect";
            case "sound" -> "sound";
            case "close" -> "close";
            default -> type;
        };
    }

    /** Map a {@code data} action onto its {@code data-*} ref, wrapping a {@code math: true} value for our evaluator. */
    private List<String> dataRefs(ConfigurationNode action, List<String> warnings) {
        String operation = action.node("action").getString("SET").strip().toUpperCase(Locale.ROOT);
        String key = action.node("key").getString("").strip();
        String value = action.node("value").getString("");
        String resolved = action.node("math").getBoolean(false) ? "{math:" + value + "}" : value;
        String id = dataActionId(operation);
        if (id == null) {
            warnings.add("skipped a data action with unknown operation '" + operation + "'");
            return List.of();
        }
        return id.equals("data-remove") ? List.of(id + ":" + key) : List.of(id + ":" + key + " " + resolved);
    }

    /** The {@code data-*} action id for a zMenu data operation, or {@code null} when it is one we do not model. */
    private static @Nullable String dataActionId(String operation) {
        return switch (operation) {
            case "ADD" -> "data-add";
            case "SUBTRACT", "SUB" -> "data-sub";
            case "SET" -> "data-set";
            case "REMOVE" -> "data-remove";
            default -> null;
        };
    }

    /** An {@code inventory} action → {@code open:<inventory>}; a blank target is a skipped no-op. */
    private static List<String> openRef(ConfigurationNode action) {
        String inventory = action.node("inventory").getString("").strip();
        return inventory.isEmpty() ? List.of() : List.of("open:" + inventory);
    }

    /** The target server of a {@code connect} action: its {@code server}, else its {@code bungee}, else {@code hub}. */
    private static String serverName(ConfigurationNode action) {
        String server = action.node("server").getString("").strip();
        return server.isEmpty() ? action.node("bungee").getString("hub").strip() : server;
    }

    /** An unknown action type: a best-effort {@code console} command when it names commands, else a skipped no-op. */
    private List<String> unknownAction(ConfigurationNode action, String type, List<String> warnings) {
        List<String> commands = firstList(action.node("commands"), action.node("command"));
        if (!commands.isEmpty()) {
            warnings.add("unknown action type '" + type + "' converted to console commands; verify it");
            return prefixEach("console", commands);
        }
        warnings.add("skipped unknown action type '" + type + "'");
        return List.of();
    }

    /** Prefix each value with {@code <id>:} to form a valued action ref ({@code message:hi}). */
    private static List<String> prefixEach(String id, List<String> values) {
        return values.stream().map(value -> id + ":" + value).toList();
    }

    // --- requirements -------------------------------------------------------------------------------------------

    /** Map a requirement list (its {@code requirements}, or the {@code requirement} spelling) onto condition tokens. */
    private List<String> mapRequirements(ConfigurationNode primary, ConfigurationNode fallback, List<String> warnings) {
        List<String> conditions = new ArrayList<>();
        for (ConfigurationNode req : requirementNodes(primary, fallback)) {
            String token = mapRequirement(req, warnings);
            if (token != null) {
                conditions.add(token);
            }
        }
        return conditions;
    }

    /** The requirement child nodes: the {@code requirements} list, else the {@code requirement} spelling, else none. */
    private static List<? extends ConfigurationNode> requirementNodes(
            ConfigurationNode primary, ConfigurationNode fallback) {
        if (!primary.virtual() && primary.isList()) {
            return primary.childrenList();
        }
        return !fallback.virtual() && fallback.isList() ? fallback.childrenList() : List.of();
    }

    /**
     * Map one typed zMenu requirement onto our condition token. A {@code permission} becomes {@code perm} (a leading
     * {@code !} inverting it); a {@code placeholder} becomes a comparison condition. JavaScript is dropped (a locked
     * decision: no scripting) and an unknown type is dropped; both are warned about.
     */
    private @Nullable String mapRequirement(ConfigurationNode req, List<String> warnings) {
        String type = req.node("type").getString("").strip().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "permission" -> permissionCondition(req);
            case "placeholder" -> placeholderCondition(req, warnings);
            case "javascript", "js" ->
                skip(warnings, "skipped a 'javascript' requirement; JavaScript conditions are not supported");
            default -> skip(warnings, "skipped unknown requirement type '" + type + "'");
        };
    }

    /** A {@code permission} requirement → {@code perm:<node>}, honouring zMenu's leading {@code !} negation. */
    private String permissionCondition(ConfigurationNode req) {
        String permission = req.node("permission").getString("").strip();
        boolean inverted = permission.startsWith("!");
        String node = inverted ? permission.substring(1).strip() : permission;
        return (inverted ? "!perm:" : "perm:") + node;
    }

    /** A {@code placeholder} requirement → a comparison condition built from its action, placeholder and value. */
    private @Nullable String placeholderCondition(ConfigurationNode req, List<String> warnings) {
        String placeholder = req.node("placeholder")
                .getString(req.node("placeHolder").getString(""))
                .strip();
        String value = req.node("value").getString("").strip();
        String action = req.node("action").getString("").strip().toUpperCase(Locale.ROOT);
        return conditionFor(action, placeholder, value, warnings);
    }

    /**
     * The condition token for a zMenu placeholder action. A numeric comparison becomes an {@code expr} (which
     * evaluates both sides, so a {@code math} placeholder needs no extra wrapping); a string comparison becomes the
     * matching {@code equals-ignorecase} / {@code contains} condition; an unknown action is dropped with a warning.
     */
    private @Nullable String conditionFor(String action, String placeholder, String value, List<String> warnings) {
        String operator = numericOperator(action);
        if (operator != null) {
            return "expr:" + placeholder + " " + operator + " " + value;
        }
        return switch (action) {
            case "EQUALS_STRING", "EQUALSIGNORECASE_STRING" -> "equals-ignorecase:" + placeholder + " " + value;
            case "DIFFERENT_STRING" -> "!equals-ignorecase:" + placeholder + " " + value;
            case "CONTAINS_STRING", "CONTAINS" -> "contains:" + placeholder + " " + value;
            case "BOOLEAN" -> "expr:" + placeholder + " == " + value;
            default -> skip(warnings, "skipped placeholder requirement with unknown action '" + action + "'");
        };
    }

    /** The comparison operator for a numeric zMenu placeholder action, or {@code null} for a non-numeric action. */
    private static @Nullable String numericOperator(String action) {
        return switch (action) {
            case "SUPERIOR", ">" -> ">";
            case "SUPERIOR_OR_EQUAL", ">=" -> ">=";
            case "INFERIOR", "LOWER", "<" -> "<";
            case "INFERIOR_OR_EQUAL", "LOWER_OR_EQUAL", "<=" -> "<=";
            case "EQUALS", "EQUAL_TO", "==" -> "==";
            case "DIFFERENT", "!=" -> "!=";
            default -> null;
        };
    }

    // --- shared helpers -----------------------------------------------------------------------------------------

    /** Record {@code warning} and return {@code null}, the value a dropped action or requirement contributes. */
    private static @Nullable String skip(List<String> warnings, String warning) {
        warnings.add(warning);
        return null;
    }

    /** Concatenate two ref lists, keeping the cheap identity path when one side is empty. */
    private static List<String> merge(List<String> first, List<String> second) {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        List<String> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    /** The primary node's string list, or the fallback's when the primary carries nothing. */
    private static List<String> firstList(ConfigurationNode primary, ConfigurationNode fallback) {
        List<String> values = stringList(primary);
        return values.isEmpty() ? stringList(fallback) : values;
    }

    /** Set {@code target} to {@code value} only when the zMenu {@code source} node was actually present. */
    private static void setIfPresent(ConfigurationNode source, CommentedConfigurationNode target, String value)
            throws SerializationException {
        if (!source.virtual() && !source.isNull()) {
            target.set(value);
        }
    }

    /** The string-list value of a node, wrapping a lone scalar so a single-value field is still read as a list. */
    private static List<String> stringList(ConfigurationNode node) {
        if (node.virtual() || node.isNull()) {
            return List.of();
        }
        if (node.isList()) {
            try {
                List<String> values = node.getList(String.class);
                return values == null ? List.of() : List.copyOf(values);
            } catch (SerializationException malformed) {
                return List.of();
            }
        }
        String scalar = node.getString();
        return scalar == null ? List.of() : List.of(scalar);
    }

    /** Parse a zMenu YAML document; a fundamentally malformed document raises rather than silently emptying. */
    private static ConfigurationNode parseYaml(String yaml) {
        try {
            return YamlConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new StringReader(yaml)))
                    .build()
                    .load();
        } catch (ConfigurateException failure) {
            throw new IllegalArgumentException("could not parse zMenu YAML", failure);
        }
    }

    /** Render our populated spec node to HOCON text. */
    private static String render(CommentedConfigurationNode spec) {
        StringWriter writer = new StringWriter();
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                .sink(() -> new BufferedWriter(writer))
                .build();
        try {
            loader.save(spec);
        } catch (ConfigurateException failure) {
            throw new IllegalStateException("failed to render converted menu", failure);
        }
        return writer.toString();
    }

    /** The outcome of a conversion: the emitted HOCON spec text and the best-effort compromises worth logging. */
    public record ConversionResult(String hocon, List<String> warnings) {
        public ConversionResult {
            Objects.requireNonNull(hocon, "hocon");
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        }
    }
}
