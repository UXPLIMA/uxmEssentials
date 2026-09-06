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

import com.google.common.base.Splitter;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Converts a DeluxeMenus menu YAML into an equivalent uxmEssentials HOCON menu spec, so an operator migrating off
 * DeluxeMenus keeps their menus. It reads the well-known DeluxeMenus surface, {@code menu_title}, {@code size},
 * {@code open_requirement}, and each {@code items.<id>} with its material / name / lore / slots / per-gesture click
 * commands and requirements, and emits the {@code title} / {@code rows} / {@code items { … }} shape
 * {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader} loads.
 *
 * <p>The conversion covers the common surface and degrades gracefully everywhere else: an unknown click tag becomes a
 * best-effort {@code console} command, an unmappable requirement type (a JavaScript expression, a construct we have no
 * condition for) is dropped, and every such compromise is recorded as a {@link ConversionResult#warnings() warning}
 * the caller logs, never a thrown exception. Only a fundamentally unparsable YAML document raises, which the calling
 * service catches per file so one bad export never aborts a whole directory convert.
 *
 * <p>DeluxeMenus colour codes ({@code &a}) and PlaceholderAPI tokens ({@code %player_name%}) are carried through
 * verbatim: our renderer resolves MiniMessage and PAPI, so a placeholder keeps working, while a legacy {@code &} code
 * is left as written (an operator may want to move it to MiniMessage) and warned about once. The whole class is
 * Configurate-only and touches no Bukkit type, so it is exercised by plain JUnit.
 */
public final class DeluxeMenusConverter {

    /** The number of slots in one inventory row; a DeluxeMenus {@code size} divided by this is our {@code rows}. */
    private static final int SLOTS_PER_ROW = 9;

    /** A chest tops out at six rows; a DeluxeMenus size beyond {@code 54} is clamped to this with a warning. */
    private static final int MAX_ROWS = 6;

    /** The fallback row count for a menu that declares no {@code size}: DeluxeMenus defaults an absent size to 54. */
    private static final int DEFAULT_ROWS = 6;

    /** The DeluxeMenus trailing per-command modifier ({@code <delay=100>}); stripped and warned about, not converted. */
    private static final Pattern MODIFIER = Pattern.compile("<\\w+=[^>]*>");

    /** The six DeluxeMenus click gestures paired with the requirement key and the HOCON click key each maps onto. */
    private static final List<Gesture> GESTURES = List.of(
            new Gesture("left_click_commands", "left_click_requirement", "left"),
            new Gesture("right_click_commands", "right_click_requirement", "right"),
            new Gesture("shift_left_click_commands", "shift_left_click_requirement", "shift_left"),
            new Gesture("shift_right_click_commands", "shift_right_click_requirement", "shift_right"),
            new Gesture("middle_click_commands", "middle_click_requirement", "middle"),
            new Gesture("click_commands", "click_requirement", "any"));

    /** The warning emitted once when a menu still carries legacy {@code &} colour codes the operator may want to move. */
    private static final String LEGACY_COLOUR_WARNING =
            "menu uses legacy '&' colour codes; they still render, but consider moving them to MiniMessage";

    /**
     * Convert a DeluxeMenus menu YAML document into our HOCON spec text plus the warnings the conversion accrued. A
     * fundamentally unparsable YAML document raises {@link IllegalArgumentException}; every recoverable compromise is a
     * warning, not a throw.
     */
    public ConversionResult convert(String deluxeMenusYaml) {
        Objects.requireNonNull(deluxeMenusYaml, "deluxeMenusYaml");
        ConfigurationNode source = parseYaml(deluxeMenusYaml);
        List<String> warnings = new ArrayList<>();
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder().build();
        CommentedConfigurationNode spec = loader.createNode();
        buildSpec(source, spec, warnings);
        return new ConversionResult(render(spec), List.copyOf(warnings));
    }

    /** Populate our spec node from the DeluxeMenus source: title, rows, the open-command note, open gate, and items. */
    private void buildSpec(ConfigurationNode source, CommentedConfigurationNode spec, List<String> warnings) {
        try {
            spec.node("title").set(source.node("menu_title").getString(""));
            openCommandNote(source).ifPresent(note -> spec.node("title").comment(note));
            spec.node("rows").set(rows(source, warnings));
            openRequirement(source, spec, warnings);
            buildItems(source.node("items"), spec.node("items"), warnings);
        } catch (SerializationException failure) {
            throw new IllegalStateException("failed to build converted menu spec", failure);
        }
    }

    /** The DeluxeMenus {@code size} as a clamped row count: absent → six rows, a size beyond 54 → six rows + a warning. */
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

    /** The {@code open_command} (string or list) as a note for the emitted spec: our opener wiring is separate. */
    private java.util.Optional<String> openCommandNote(ConfigurationNode source) {
        ConfigurationNode open = source.node("open_command");
        if (open.virtual() || open.isNull()) {
            return java.util.Optional.empty();
        }
        String value = open.isList() ? String.join(", ", stringList(open)) : open.getString("");
        if (value.isBlank()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of("open_command: " + value + ". Wire this via /menu open or an openers.conf entry");
    }

    /** Map the DeluxeMenus {@code open_requirement} conditions onto our flat {@code open-requirement} list. */
    private void openRequirement(ConfigurationNode source, CommentedConfigurationNode spec, List<String> warnings)
            throws SerializationException {
        ConfigurationNode open = source.node("open_requirement");
        if (open.virtual() || open.isNull()) {
            return;
        }
        RequirementBlock block = requirementBlock(open, warnings);
        if (!block.conditions().isEmpty()) {
            spec.node("open-requirement").set(block.conditions());
        }
        if (!block.deny().isEmpty()) {
            warnings.add("open_requirement deny_commands are not representable in open-requirement; dropped");
        }
    }

    /** Convert every {@code items.<id>} block onto our {@code items.<id>} spec. */
    private void buildItems(ConfigurationNode items, CommentedConfigurationNode out, List<String> warnings)
            throws SerializationException {
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                items.childrenMap().entrySet()) {
            buildItem(entry.getValue(), out.node(String.valueOf(entry.getKey())), warnings);
        }
    }

    /** Convert one DeluxeMenus item: material, name, slots, lore, amount, priority, the view gate and click gestures. */
    private void buildItem(ConfigurationNode item, CommentedConfigurationNode out, List<String> warnings)
            throws SerializationException {
        setIfPresent(
                item.node("material"),
                out.node("material"),
                mapMaterial(item.node("material").getString("")));
        setIfPresent(
                item.node("display_name"),
                out.node("name"),
                item.node("display_name").getString(""));
        writeSlots(item, out);
        writeLore(item, out, warnings);
        if (!item.node("amount").virtual()) {
            out.node("decor", "amount").set(item.node("amount").getInt(1));
        }
        if (!item.node("priority").virtual()) {
            out.node("priority").set(item.node("priority").getInt(0));
        }
        buildView(item.node("view_requirement"), out, warnings);
        buildClick(item, out, warnings);
    }

    /** Copy the item's {@code slot}/{@code slots} onto our {@code slot}/{@code slots}, splitting a comma range scalar. */
    private void writeSlots(ConfigurationNode item, CommentedConfigurationNode out) throws SerializationException {
        ConfigurationNode slots = item.node("slots");
        if (!slots.virtual() && !slots.isNull()) {
            List<String> tokens = slots.isList() ? stringList(slots) : splitRange(slots.getString(""));
            out.node("slots").set(tokens);
            return;
        }
        ConfigurationNode slot = item.node("slot");
        if (!slot.virtual() && !slot.isNull()) {
            out.node("slot").set(slot.getInt(0));
        }
    }

    /** Split a DeluxeMenus comma-separated slot scalar ({@code "0-8, 10"}) into our range tokens, dropping blanks. */
    private static List<String> splitRange(String scalar) {
        List<String> tokens = new ArrayList<>();
        for (String part : Splitter.on(',').trimResults().omitEmptyStrings().split(scalar)) {
            tokens.add(part);
        }
        return tokens;
    }

    /** Copy the item's {@code lore} verbatim and warn once when it (or a name) still carries legacy {@code &} codes. */
    private void writeLore(ConfigurationNode item, CommentedConfigurationNode out, List<String> warnings)
            throws SerializationException {
        List<String> lore = stringList(item.node("lore"));
        if (!lore.isEmpty()) {
            out.node("lore").set(lore);
        }
        boolean legacy = lore.stream().anyMatch(line -> line.contains("&"))
                || item.node("display_name").getString("").contains("&");
        if (legacy && !warnings.contains(LEGACY_COLOUR_WARNING)) {
            warnings.add(LEGACY_COLOUR_WARNING);
        }
    }

    /** Map the DeluxeMenus {@code view_requirement} onto our item {@code view { requirements, minimum }} gate. */
    private void buildView(ConfigurationNode viewReq, CommentedConfigurationNode out, List<String> warnings)
            throws SerializationException {
        if (viewReq.virtual() || viewReq.isNull()) {
            return;
        }
        RequirementBlock block = requirementBlock(viewReq, warnings);
        if (block.conditions().isEmpty()) {
            return;
        }
        CommentedConfigurationNode view = out.node("view");
        view.node("requirements").set(block.conditions());
        if (block.minimum() > 0) {
            view.node("minimum").set(block.minimum());
        }
        if (!block.deny().isEmpty()) {
            warnings.add("view_requirement deny_commands are not representable in a view gate; dropped");
        }
    }

    /** Convert every DeluxeMenus click gesture (and its per-gesture requirement) onto our {@code click { … }} block. */
    private void buildClick(ConfigurationNode item, CommentedConfigurationNode out, List<String> warnings)
            throws SerializationException {
        for (Gesture gesture : GESTURES) {
            ConfigurationNode commands = item.node(gesture.commandsKey());
            ConfigurationNode requirement = item.node(gesture.requirementKey());
            boolean hasCommands = !commands.virtual() && !commands.isNull();
            boolean hasRequirement = !requirement.virtual() && !requirement.isNull();
            if (!hasCommands && !hasRequirement) {
                continue;
            }
            List<String> actions = hasCommands ? mapActions(commands, warnings) : List.of();
            RequirementBlock block = hasRequirement ? requirementBlock(requirement, warnings) : RequirementBlock.EMPTY;
            writeGesture(out.node("click", gesture.hoconKey()), actions, block);
        }
    }

    /** Write one gesture: a bare action list when it has no requirement, else the map form with requirements/deny. */
    private void writeGesture(CommentedConfigurationNode node, List<String> actions, RequirementBlock block)
            throws SerializationException {
        if (block.isEmpty()) {
            node.set(actions);
            return;
        }
        node.node("actions").set(actions);
        if (!block.conditions().isEmpty()) {
            node.node("requirements").set(block.conditions());
        }
        if (!block.deny().isEmpty()) {
            node.node("deny").set(block.deny());
        }
        if (block.minimum() > 0) {
            node.node("minimum").set(block.minimum());
        }
        if (block.stopAtSuccess()) {
            node.node("stop_at_success").set(true);
        }
    }

    /**
     * Map a DeluxeMenus requirement block. Its {@code requirements} map, {@code deny_commands}, {@code
     * minimum_requirements} and {@code stop_at_success}: onto our condition tokens plus deny actions.
     */
    private RequirementBlock requirementBlock(ConfigurationNode block, List<String> warnings) {
        List<String> conditions = new ArrayList<>();
        for (ConfigurationNode requirement :
                block.node("requirements").childrenMap().values()) {
            String token = mapRequirement(requirement, warnings);
            if (token != null) {
                conditions.add(token);
            }
        }
        return new RequirementBlock(
                conditions,
                mapActions(block.node("deny_commands"), warnings),
                block.node("minimum_requirements").getInt(0),
                block.node("stop_at_success").getBoolean(false));
    }

    /**
     * Map one DeluxeMenus requirement onto our condition token, honouring a leading {@code !} inversion. JavaScript is
     * dropped (a locked decision: no scripting), and an unknown type is dropped; both are warned about.
     */
    private @Nullable String mapRequirement(ConfigurationNode req, List<String> warnings) {
        String type = req.node("type").getString("").strip();
        boolean inverted = type.startsWith("!");
        String base = (inverted ? type.substring(1) : type).strip().toLowerCase(Locale.ROOT);
        if (base.equals("javascript")) {
            warnings.add("skipped a 'javascript' requirement; JavaScript conditions are not supported");
            return null;
        }
        String token = requirementToken(base, req, warnings);
        if (token == null) {
            warnings.add("skipped unknown requirement type '" + type + "'");
            return null;
        }
        return inverted ? "!" + token : token;
    }

    /** The condition token for a known DeluxeMenus requirement type, or {@code null} when the type is unrecognised. */
    private @Nullable String requirementToken(String base, ConfigurationNode req, List<String> warnings) {
        return switch (base) {
            case "has permission" -> "perm:" + req.node("permission").getString("");
            case "has money" -> "has-money:" + req.node("amount").getString("0");
            case "has exp" -> "has-exp:" + req.node("amount").getString("0");
            case "has item" -> hasItem(req);
            case "string contains" -> "contains:" + operands(req);
            case "string equals ignorecase" -> "equals-ignorecase:" + operands(req);
            case "string equals" -> stringEquals(req, warnings);
            case "regex matches" ->
                "regex:" + req.node("input").getString("") + " "
                        + req.node("regex").getString("");
            case ">", ">=", "==", "<=", "<" ->
                "expr:" + req.node("input").getString("") + " " + base + " "
                        + req.node("output").getString("");
            default -> null;
        };
    }

    /** {@code has item} → our {@code has-item:<material> [amount]}; name/data facets are best-effort and dropped. */
    private static String hasItem(ConfigurationNode req) {
        String material = req.node("material").getString("");
        String amount = req.node("amount").getString("");
        return amount.isBlank() ? "has-item:" + material : "has-item:" + material + " " + amount;
    }

    /** {@code string equals} has no exact-case condition here, so map it to a case-insensitive compare with a warning. */
    private static String stringEquals(ConfigurationNode req, List<String> warnings) {
        warnings.add("mapped 'string equals' to a case-insensitive comparison; no exact-case condition exists");
        return "equals-ignorecase:" + operands(req);
    }

    /** The two operands of a DeluxeMenus string requirement joined into our {@code <input> <output>} value form. */
    private static String operands(ConfigurationNode req) {
        return req.node("input").getString("") + " " + req.node("output").getString("");
    }

    /** Map a DeluxeMenus command list onto our action refs, dropping any that reduce to nothing. */
    private List<String> mapActions(ConfigurationNode node, List<String> warnings) {
        List<String> refs = new ArrayList<>();
        for (String raw : stringList(node)) {
            String ref = mapAction(raw, warnings);
            if (ref != null) {
                refs.add(ref);
            }
        }
        return refs;
    }

    /**
     * Map one DeluxeMenus click command onto our action ref. A {@code [tag] arg} command is translated through the tag
     * map; a raw command with no tag runs as the player (DeluxeMenus' default); an unknown tag becomes a best-effort
     * {@code console} command with a warning. A trailing {@code <delay=…>} modifier is stripped (and warned about).
     */
    private @Nullable String mapAction(String rawCommand, List<String> warnings) {
        String command = stripModifiers(rawCommand, warnings).strip();
        if (command.isEmpty()) {
            return null;
        }
        if (!command.startsWith("[")) {
            return "command:" + command;
        }
        int end = command.indexOf(']');
        if (end < 0) {
            return "command:" + command;
        }
        String tag = command.substring(1, end).strip().toLowerCase(Locale.ROOT);
        String arg = command.substring(end + 1).strip();
        return mapTag(tag, arg, warnings);
    }

    /** Translate one DeluxeMenus click tag onto our action ref; an unknown tag falls back to a console command. */
    private @Nullable String mapTag(String tag, String arg, List<String> warnings) {
        return switch (tag) {
            case "message" -> "message:" + arg;
            case "broadcast" -> "broadcast:" + arg;
            case "console" -> "console:" + arg;
            case "player" -> "command:" + arg;
            case "commandevent" -> "commandevent:" + arg;
            case "openguimenu" -> "open:" + arg;
            case "close", "closeinventory" -> "close";
            case "refresh" -> "refresh";
            case "sound" -> "sound:" + arg;
            case "broadcastsound" -> "broadcast-sound:" + arg;
            case "connect", "connectserver", "server" -> "connect:" + arg;
            case "chat", "chatplayer" -> "chat:" + arg;
            case "givemoney" -> "give-points:" + arg;
            case "takemoney" -> "take-points:" + arg;
            case "title" -> "title:" + arg;
            case "actionbar" -> "action-bar:" + arg;
            default -> {
                warnings.add("unknown click tag '[" + tag + "]' converted to a console command; verify it");
                yield "console:" + arg;
            }
        };
    }

    /** Strip a DeluxeMenus trailing {@code <delay=…>}-style modifier (which we do not convert) and warn once. */
    private String stripModifiers(String raw, List<String> warnings) {
        if (!MODIFIER.matcher(raw).find()) {
            return raw;
        }
        String stripped = MODIFIER.matcher(raw).replaceAll("");
        String note = "dropped a '<…=…>' command modifier during conversion";
        if (!warnings.contains(note)) {
            warnings.add(note);
        }
        return stripped;
    }

    /** Translate a DeluxeMenus head material form ({@code head-Steve}, {@code hdb-5}) onto our icon-provider prefix. */
    private static String mapMaterial(String material) {
        String lower = material.toLowerCase(Locale.ROOT);
        if (lower.startsWith("head-")) {
            return "head:" + material.substring("head-".length());
        }
        if (lower.startsWith("basehead-")) {
            return "basehead:" + material.substring("basehead-".length());
        }
        if (lower.startsWith("hdb-")) {
            return "hdb:" + material.substring("hdb-".length());
        }
        return material;
    }

    /** Set {@code target} to {@code value} only when the DeluxeMenus {@code source} node was actually present. */
    private static void setIfPresent(ConfigurationNode source, CommentedConfigurationNode target, String value)
            throws SerializationException {
        if (!source.virtual() && !source.isNull()) {
            target.set(value);
        }
    }

    /** Parse a DeluxeMenus YAML document; a fundamentally malformed document raises rather than silently emptying. */
    private static ConfigurationNode parseYaml(String yaml) {
        try {
            return YamlConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new StringReader(yaml)))
                    .build()
                    .load();
        } catch (ConfigurateException failure) {
            throw new IllegalArgumentException("could not parse DeluxeMenus YAML", failure);
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

    /** The string-list value of a node, wrapping a lone scalar so a single-command field is still read as a list. */
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

    /** One DeluxeMenus click gesture: its command-list key, its requirement key, and the HOCON click key it maps to. */
    private record Gesture(String commandsKey, String requirementKey, String hoconKey) {}

    /** A mapped requirement block: the condition tokens, the deny actions, and the minimum / stop-at-success flags. */
    private record RequirementBlock(List<String> conditions, List<String> deny, int minimum, boolean stopAtSuccess) {

        private static final RequirementBlock EMPTY = new RequirementBlock(List.of(), List.of(), 0, false);

        /** Whether this block carries no requirement structure at all, so its gesture can be a bare action list. */
        private boolean isEmpty() {
            return conditions.isEmpty() && deny.isEmpty() && minimum == 0 && !stopAtSuccess;
        }
    }

    /** The outcome of a conversion: the emitted HOCON spec text and the best-effort compromises worth logging. */
    public record ConversionResult(String hocon, List<String> warnings) {
        public ConversionResult {
            Objects.requireNonNull(hocon, "hocon");
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        }
    }
}
