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
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.base.Splitter;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Converts an OGUI (OGUI-Custom-GUIs) GUI YAML into an equivalent uxmEssentials HOCON menu spec, so an operator
 * migrating off OGUI keeps their menus. An OGUI file wraps its whole GUI under a single root key, the GUI name
 * ({@code world_shop:}), beneath which sit the {@code title}, the {@code rows} (or {@code size}), the open-command
 * {@code commands} aliases, and the {@code items { … }} map. This reads that surface and emits the {@code title} /
 * {@code rows} / {@code items { … }} shape
 * {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader} loads, mirroring the sibling
 * {@link DeluxeMenusConverter} and {@link ZMenuConverter}.
 *
 * <p>An OGUI item carries its actions in two styles that both map onto our {@code click.any.actions}: a simple
 * {@code commands: [ … ]} list, which OGUI runs from the console, and a typed {@code actions: [ {type: …, …} ]} list,
 * where each typed action ({@code CONSOLE_COMMAND}, {@code PLAYER_MESSAGE}, {@code OPEN_GUI}, …) becomes one or more
 * of our registered vocabulary refs. A {@code close: true} appends our {@code close}, and an item {@code type}
 * ({@code BACK}, {@code MAIN_MENU}, …) becomes the matching navigation action. OGUI writes its player placeholder as
 * {@code {player}}, which is rewritten to our {@code %player%} in every emitted string. The refs are written in our
 * engine's bare {@code id:value} form (not bracketed), the same form the sibling converters emit, so the output
 * re-enters the engine through the ordinary {@code menus/} loader.
 *
 * <p>An item's {@code conditions} (and {@code view-requirements}) become the item's {@code view { requirements … }}
 * gate: a {@code PERMISSION} becomes {@code perm}, a {@code VAULT_MONEY} becomes {@code has-money}, a {@code WORLD}
 * becomes our single-name {@code world} condition (a multi-world whitelist becomes an OR-group with {@code minimum = 1},
 * a {@code blacklist} inverts each with a leading {@code !}), and a {@code PLACEHOLDER} comparison becomes an
 * {@code expr} / {@code contains} / {@code equals-ignorecase}. The conversion covers the common surface and degrades
 * gracefully everywhere else: a {@code SCRIPT} action or condition is dropped (a locked decision: no scripting), an
 * unknown typed action becomes a best-effort {@code console} command when it names one (else it is skipped), and an
 * unmappable condition (an integration-specific one we have no gate for) is dropped. Every such compromise is recorded
 * as a {@link ConversionResult#warnings() warning} the caller logs, never a thrown exception. Only a fundamentally
 * unparsable YAML document raises, which the calling service catches per file so one bad export never aborts a whole
 * directory convert.
 *
 * <p>A file that declares more than one GUI (some OGUI example files bundle several) has only its first GUI converted,
 * with a warning naming the rest: our loader is one menu per {@code .conf}, so the others belong in their own files.
 * OGUI colour codes ({@code &a}, {@code #77ff77}) and PlaceholderAPI tokens ({@code %player_name%}) are carried
 * through verbatim: our renderer resolves MiniMessage and PAPI, so a placeholder keeps working, while a legacy code is
 * left as written and warned about once. The whole class is Configurate-only and touches no Bukkit type, so it is
 * exercised by plain JUnit.
 */
public final class OguiConverter {

    /** The number of slots in one inventory row; an OGUI {@code size} divided by this is our {@code rows}. */
    private static final int SLOTS_PER_ROW = 9;

    /** A chest tops out at six rows; an OGUI {@code rows}/{@code size} beyond that is clamped with a warning. */
    private static final int MAX_ROWS = 6;

    /** The fallback row count for a menu that declares neither {@code rows} nor {@code size}. */
    private static final int DEFAULT_ROWS = 6;

    /** A {@code #rrggbb} hex colour code; carried through verbatim like {@code &} codes but warned about once. */
    private static final Pattern HEX_COLOUR = Pattern.compile("#[0-9a-fA-F]{6}");

    /** A slot key that is purely a number, so an item with no explicit {@code slot}/{@code slots} takes its key. */
    private static final Pattern SLOT_KEY = Pattern.compile("\\d+");

    /** Splits a comma-separated slot scalar ({@code "0-8, 10"}); each token is a single index or an {@code A-B} range. */
    private static final Splitter SLOT_COMMAS = Splitter.on(',').trimResults().omitEmptyStrings();

    /** The warning emitted once when a menu still carries legacy colour codes the operator may want to move. */
    private static final String LEGACY_COLOUR_WARNING =
            "menu uses legacy '&' or '#rrggbb' colour codes; they still render, but consider moving them to MiniMessage";

    /**
     * Convert an OGUI GUI YAML document into our HOCON spec text plus the warnings the conversion accrued. A
     * fundamentally unparsable YAML document raises {@link IllegalArgumentException}; every recoverable compromise is a
     * warning, not a throw.
     */
    public ConversionResult convert(String oguiYaml) {
        Objects.requireNonNull(oguiYaml, "oguiYaml");
        ConfigurationNode source = parseYaml(oguiYaml);
        List<String> warnings = new ArrayList<>();
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder().build();
        CommentedConfigurationNode spec = loader.createNode();
        buildSpec(source, spec, warnings);
        return new ConversionResult(render(spec), List.copyOf(warnings));
    }

    /** Populate our spec node from the OGUI source: title, the open-command note, rows, and items. */
    private void buildSpec(ConfigurationNode source, CommentedConfigurationNode spec, List<String> warnings) {
        ConfigurationNode gui = firstGui(source, warnings);
        try {
            spec.node("title").set(gui.node("title").getString(""));
            openCommandNote(gui).ifPresent(note -> spec.node("title").comment(note));
            warnNonChest(gui, warnings);
            spec.node("rows").set(rows(gui, warnings));
            buildItems(gui, gui.node("items"), spec.node("items"), warnings);
        } catch (SerializationException failure) {
            throw new IllegalStateException("failed to build converted menu spec", failure);
        }
    }

    /**
     * The GUI body to convert. An OGUI file wraps its GUI under a single root key (the GUI name), so the first root
     * child is that body; a file that carries {@code title}/{@code items} at its own top level is already the body
     * (no name wrapper). A file with several root keys has only its first GUI converted, with a warning.
     */
    private ConfigurationNode firstGui(ConfigurationNode source, List<String> warnings) {
        if (!source.node("items").virtual() || !source.node("title").virtual()) {
            return source;
        }
        Map<Object, ? extends ConfigurationNode> roots = source.childrenMap();
        if (roots.isEmpty()) {
            return source;
        }
        Map.Entry<Object, ? extends ConfigurationNode> first =
                roots.entrySet().iterator().next();
        if (roots.size() > 1) {
            warnings.add("file declares " + roots.size() + " GUIs; only the first, '" + first.getKey()
                    + "', was converted. Split the others into their own files");
        }
        return first.getValue();
    }

    /** The GUI's open-command {@code commands} aliases as a note: our opener wiring is separate, as in DeluxeMenus. */
    private Optional<String> openCommandNote(ConfigurationNode gui) {
        ConfigurationNode commands = gui.node("commands");
        if (commands.virtual() || commands.isNull()) {
            return Optional.empty();
        }
        String value = commands.isList() ? String.join(", ", stringList(commands)) : commands.getString("");
        if (value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of("open commands: " + value + ". Wire this via /menu open or an openers.conf entry");
    }

    /** Warn when a GUI uses a non-chest {@code inventory_type} (HOPPER, DISPENSER, …) we render as a chest instead. */
    private void warnNonChest(ConfigurationNode gui, List<String> warnings) {
        String type = gui.node("inventory_type").getString("").strip();
        if (!type.isEmpty() && !type.equalsIgnoreCase("CHEST")) {
            warnings.add("inventory_type '" + type + "' is not a chest; converted as a chest menu, adjust the rows");
        }
    }

    /** The OGUI {@code rows} (or {@code size}) as a clamped row count: absent → six rows, oversized → six + a warning. */
    private int rows(ConfigurationNode gui, List<String> warnings) {
        ConfigurationNode rows = gui.node("rows");
        if (!rows.virtual() && !rows.isNull()) {
            int declared = rows.getInt(DEFAULT_ROWS);
            if (declared > MAX_ROWS) {
                warnings.add("rows " + declared + " exceeds a chest maximum; clamped to " + MAX_ROWS);
            }
            return Math.max(1, Math.min(MAX_ROWS, declared));
        }
        ConfigurationNode size = gui.node("size");
        if (size.virtual() || size.isNull()) {
            return DEFAULT_ROWS;
        }
        int declared = size.getInt(DEFAULT_ROWS * SLOTS_PER_ROW);
        if (declared > MAX_ROWS * SLOTS_PER_ROW) {
            warnings.add("size " + declared + " exceeds a chest maximum; clamped to " + MAX_ROWS + " rows");
        }
        return Math.max(1, Math.min(MAX_ROWS, declared / SLOTS_PER_ROW));
    }

    /** Convert every {@code items.<key>} block onto our {@code items.<key>} spec. */
    private void buildItems(
            ConfigurationNode gui, ConfigurationNode items, CommentedConfigurationNode out, List<String> warnings)
            throws SerializationException {
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                items.childrenMap().entrySet()) {
            String key = String.valueOf(entry.getKey());
            buildItem(gui, key, entry.getValue(), out.node(key), warnings);
        }
    }

    /** Convert one OGUI item: material, name, lore, slots, amount, priority, its actions and its view gate. */
    private void buildItem(
            ConfigurationNode gui,
            String key,
            ConfigurationNode item,
            CommentedConfigurationNode out,
            List<String> warnings)
            throws SerializationException {
        writeMaterial(item, out, warnings);
        setIfPresent(item.node("name"), out.node("name"), item.node("name").getString(""));
        List<String> lore = stringList(item.node("lore"));
        if (!lore.isEmpty()) {
            out.node("lore").set(lore);
        }
        warnLegacyOnce(item.node("name").getString(""), lore, warnings);
        writeSlots(key, item, out, warnings);
        if (!item.node("amount").virtual()) {
            out.node("decor", "amount").set(item.node("amount").getInt(1));
        }
        if (!item.node("priority").virtual()) {
            out.node("priority").set(item.node("priority").getInt(0));
        }
        warnElseItem(key, item, warnings);
        buildClicks(gui, item, out, warnings);
        buildView(item, out, warnings);
    }

    /**
     * Write the item's {@code material}. A HeadDatabase item ({@code item_type: headdatabase} + {@code item_id}) maps
     * onto our {@code hdb:<id>} icon prefix; another custom-item provider ({@code itemsadder}, {@code oraxen}, …) keeps
     * the plain material and is warned about, since the provider icon is set differently in our spec.
     */
    private void writeMaterial(ConfigurationNode item, CommentedConfigurationNode out, List<String> warnings)
            throws SerializationException {
        String material = item.node("material").getString("").strip();
        String type = item.node("item_type").getString("vanilla").strip().toLowerCase(Locale.ROOT);
        String itemId = item.node("item_id").getString("").strip();
        String resolved = resolveMaterial(material, type, itemId, warnings);
        if (!resolved.isEmpty()) {
            out.node("material").set(resolved);
        }
    }

    /** Resolve a material token, translating a HeadDatabase item and warning about other custom-item providers. */
    private String resolveMaterial(String material, String type, String itemId, List<String> warnings) {
        if (type.equals("headdatabase") && !itemId.isEmpty()) {
            return "hdb:" + itemId;
        }
        if (!itemId.isEmpty() && !type.equals("vanilla")) {
            warnings.add("item uses a '" + type + "' custom item ('" + itemId
                    + "'); only its material was converted, set the provider icon manually");
        }
        return material;
    }

    /**
     * Copy the item's slots onto our {@code slots} list. OGUI's precedence is {@code slot} (a single index) over
     * {@code slots} (a list or a comma scalar of indices/ranges) over the item key when it is itself a slot number.
     * An item that names none of these and whose key is not numeric is left without a position, with a warning.
     */
    private void writeSlots(String key, ConfigurationNode item, CommentedConfigurationNode out, List<String> warnings)
            throws SerializationException {
        ConfigurationNode slot = item.node("slot");
        if (!slot.virtual() && !slot.isNull()) {
            out.node("slots").set(slotTokens(slot));
            return;
        }
        ConfigurationNode slots = item.node("slots");
        if (!slots.virtual() && !slots.isNull()) {
            out.node("slots").set(slotTokens(slots));
            return;
        }
        if (SLOT_KEY.matcher(key).matches()) {
            out.node("slots").set(List.of(key));
            return;
        }
        warnings.add("item '" + key + "' has no slot/slots and its key is not a slot number; position it manually");
    }

    /** The slot tokens of a {@code slot}/{@code slots} node: a list's entries, or a scalar split on its commas. */
    private static List<String> slotTokens(ConfigurationNode node) {
        List<String> tokens = new ArrayList<>();
        List<String> entries = node.isList() ? stringList(node) : List.of(node.getString(""));
        for (String entry : entries) {
            SLOT_COMMAS.split(entry).forEach(tokens::add);
        }
        return tokens;
    }

    // --- actions ------------------------------------------------------------------------------------------------

    /**
     * Build the item's click actions. Every gesture-agnostic source folds onto our {@code any} gesture, the legacy
     * {@code commands} list (each run as a {@code console} command), then the typed {@code actions}, then the item's
     * navigation {@code type}, then a trailing {@code close}. A per-gesture {@code click_actions} map, when present,
     * adds its typed actions onto the matching gesture.
     */
    private void buildClicks(
            ConfigurationNode gui, ConfigurationNode item, CommentedConfigurationNode out, List<String> warnings)
            throws SerializationException {
        List<String> any = anyActions(gui, item, warnings);
        if (!any.isEmpty()) {
            out.node("click", "any", "actions").set(any);
        }
        ConfigurationNode clickActions = item.node("click_actions");
        if (clickActions.virtual() || clickActions.isNull()) {
            return;
        }
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                clickActions.childrenMap().entrySet()) {
            writeGestureActions(String.valueOf(entry.getKey()), entry.getValue(), out, warnings);
        }
    }

    /** Map one {@code click_actions.<type>} block onto its gesture, skipping a click type we do not model. */
    private void writeGestureActions(
            String clickType, ConfigurationNode actions, CommentedConfigurationNode out, List<String> warnings)
            throws SerializationException {
        String gesture = gestureFor(clickType);
        if (gesture == null) {
            warnings.add("skipped unknown click type '" + clickType + "'");
            return;
        }
        List<String> refs = mapActions(actions, warnings);
        if (!refs.isEmpty()) {
            out.node("click", gesture, "actions").set(refs);
        }
    }

    /** The gesture-agnostic action list for the {@code any} click: legacy commands, typed actions, type-nav, close. */
    private List<String> anyActions(ConfigurationNode gui, ConfigurationNode item, List<String> warnings) {
        List<String> refs = new ArrayList<>();
        for (String command : stringList(item.node("commands"))) {
            refs.add("console:" + subVars(command));
        }
        refs.addAll(mapActions(item.node("actions"), warnings));
        typeAction(gui, item, warnings).ifPresent(refs::add);
        if (item.node("close").getBoolean(false) && !refs.contains("close")) {
            refs.add("close");
        }
        return refs;
    }

    /** The navigation action an item's {@code type} declares, or empty for a plain (or unmodelled) type. */
    private Optional<String> typeAction(ConfigurationNode gui, ConfigurationNode item, List<String> warnings) {
        String type =
                item.node("type").getString("").strip().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (type) {
            case "", "NONE" -> Optional.empty();
            case "BACK" -> Optional.of("back");
            case "CLOSE", "CLOSE_INVENTORY" -> Optional.of("close");
            case "INVENTORY" -> openTarget(item.node("inventory").getString("").strip());
            case "MAIN_MENU" -> mainMenuAction(gui, warnings);
            default -> skipType(type, warnings);
        };
    }

    /** A {@code MAIN_MENU} button opens the GUI-level {@code main_menu}; a GUI that names none skips the action. */
    private Optional<String> mainMenuAction(ConfigurationNode gui, List<String> warnings) {
        String main = gui.node("main_menu").getString("").strip();
        if (main.isEmpty()) {
            warnings.add("a MAIN_MENU button has no gui-level main_menu; skipped its action");
            return Optional.empty();
        }
        return Optional.of("open:" + main);
    }

    /** Record that a button {@code type} we do not model was left without an action, and return empty. */
    private Optional<String> skipType(String type, List<String> warnings) {
        warnings.add("button type '" + type + "' is not converted; wire its action manually");
        return Optional.empty();
    }

    /** Map a list of typed OGUI action maps onto our action refs, expanding an action that yields several. */
    private List<String> mapActions(ConfigurationNode actions, List<String> warnings) {
        List<String> refs = new ArrayList<>();
        for (ConfigurationNode action : actions.childrenList()) {
            refs.addAll(mapAction(action, warnings));
        }
        return refs;
    }

    /**
     * Map one typed OGUI action map onto our action refs. Each action's player text has {@code {player}} rewritten to
     * {@code %player%}. A {@code SCRIPT} action is dropped (a locked decision: no scripting); an unknown type becomes a
     * best-effort {@code console} command when it carries a {@code command} field, else it is skipped. Both are warned.
     */
    private List<String> mapAction(ConfigurationNode action, List<String> warnings) {
        String type = action.node("type")
                .getString("")
                .strip()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_');
        return switch (type) {
            case "PLAYER_MESSAGE" -> valued("message", action.node("message"));
            case "BROADCAST" -> valued("broadcast", action.node("message"));
            case "ACTION_BAR" -> valued("action-bar", action.node("message"));
            case "TITLE" -> titleRef(action);
            case "SOUND" -> soundRef("sound", action);
            case "BROADCAST_SOUND" -> soundRef("broadcast-sound", action);
            case "TELEPORT" -> teleportRef(action);
            case "CONSOLE_COMMAND" -> valued("console", action.node("command"));
            case "PLAYER_COMMAND" -> valued("command", action.node("command"));
            case "RANDOM_CONSOLE_COMMAND" -> randomRef("console-random", action);
            case "RANDOM_PLAYER_COMMAND" -> randomRef("command-random", action);
            case "OPEN_INVENTORY" -> openList(action.node("inventory").getString(""));
            case "OPEN_GUI" -> openList(first(action, "gui", "inventory"));
            case "OPEN_BEDROCK_FORM", "OPEN_BEDROCK_DIALOGUE" -> openList(first(action, "inventory", "gui"));
            case "CLOSE_INVENTORY" -> List.of("close");
            case "BACK" -> List.of("back");
            case "SERVER_CONNECT" -> connectRef(action);
            case "SCRIPT" -> skipScript(warnings);
            default -> unknownAction(action, type, warnings);
        };
    }

    /** A single valued ref ({@code message:hi}) from a node's text, with {@code {player}} rewritten; blank is skipped. */
    private static List<String> valued(String id, ConfigurationNode node) {
        String value = subVars(node.getString(""));
        return value.isEmpty() ? List.of() : List.of(id + ":" + value);
    }

    /** A {@code TITLE} action → our {@code title:<title>[|<subtitle>]}; a title and subtitle both blank is skipped. */
    private static List<String> titleRef(ConfigurationNode action) {
        String title = subVars(action.node("title").getString(""));
        String subtitle = subVars(action.node("subtitle").getString(""));
        if (title.isEmpty() && subtitle.isEmpty()) {
            return List.of();
        }
        return List.of(subtitle.isEmpty() ? "title:" + title : "title:" + title + "|" + subtitle);
    }

    /** A {@code SOUND}/{@code BROADCAST_SOUND} action → our {@code sound}/{@code broadcast-sound} ref; blank is skipped. */
    private static List<String> soundRef(String id, ConfigurationNode action) {
        String sound = action.node("sound").getString("").strip();
        return sound.isEmpty() ? List.of() : List.of(id + ":" + sound);
    }

    /** A {@code TELEPORT} action → our {@code teleport:<world> <x> <y> <z> <yaw> <pitch>} destination. */
    private static List<String> teleportRef(ConfigurationNode action) {
        String world = action.node("world").getString("world").strip();
        String coords = action.node("x").getString("0") + " "
                + action.node("y").getString("64") + " "
                + action.node("z").getString("0") + " "
                + action.node("yaw").getString("0") + " "
                + action.node("pitch").getString("0");
        return List.of("teleport:" + world + " " + coords);
    }

    /** A {@code RANDOM_*_COMMAND} action → our {@code console-random}/{@code command-random} with {@code ;}-joined choices. */
    private static List<String> randomRef(String id, ConfigurationNode action) {
        List<String> commands = stringList(action.node("commands"));
        if (commands.isEmpty()) {
            return List.of();
        }
        String joined = commands.stream().map(OguiConverter::subVars).collect(Collectors.joining(";"));
        return List.of(id + ":" + joined);
    }

    /** An {@code open:<target>} ref list, or empty when the target is blank. */
    private static List<String> openList(String target) {
        return openTarget(target).map(List::of).orElse(List.of());
    }

    /** An {@code open:<target>} ref, or empty when the target is blank. */
    private static Optional<String> openTarget(String target) {
        String stripped = target.strip();
        return stripped.isEmpty() ? Optional.empty() : Optional.of("open:" + stripped);
    }

    /** A {@code SERVER_CONNECT} action → our {@code connect:<server>}; a blank server is skipped. */
    private static List<String> connectRef(ConfigurationNode action) {
        String server = action.node("server").getString("").strip();
        return server.isEmpty() ? List.of() : List.of("connect:" + server);
    }

    /** Record that a {@code SCRIPT} action was dropped (no scripting), and contribute no ref. */
    private static List<String> skipScript(List<String> warnings) {
        warnings.add("skipped a SCRIPT action; JavaScript actions are not supported");
        return List.of();
    }

    /** An unknown action type: a best-effort {@code console} command when it names one, else a skipped no-op. */
    private static List<String> unknownAction(ConfigurationNode action, String type, List<String> warnings) {
        String command = action.node("command").getString("").strip();
        if (!command.isEmpty()) {
            warnings.add("unknown action type '" + type + "' converted to a console command; verify it");
            return List.of("console:" + subVars(command));
        }
        warnings.add("skipped unknown action type '" + type + "'");
        return List.of();
    }

    // --- conditions ---------------------------------------------------------------------------------------------

    /** Map the item's {@code conditions} (and {@code view-requirements}) onto its {@code view { requirements … }} gate. */
    private void buildView(ConfigurationNode item, CommentedConfigurationNode out, List<String> warnings)
            throws SerializationException {
        ViewGate gate = new ViewGate();
        foldConditions(item.node("conditions"), gate, warnings);
        foldConditions(item.node("view-requirements"), gate, warnings);
        gate.writeTo(out, warnings);
    }

    /** Fold every typed condition in {@code node} onto {@code gate}; an absent/empty block contributes nothing. */
    private void foldConditions(ConfigurationNode node, ViewGate gate, List<String> warnings) {
        if (node.virtual() || node.isNull()) {
            return;
        }
        for (ConfigurationNode condition : node.childrenList()) {
            mapCondition(condition, gate, warnings);
        }
    }

    /**
     * Map one typed OGUI condition onto our condition tokens on {@code gate}. A comparison keeps its
     * {@code %placeholder%} for our renderer to expand; a {@code SCRIPT} condition is dropped (no scripting), and an
     * integration-specific type we have no gate for is dropped. Both are warned about.
     */
    private void mapCondition(ConfigurationNode condition, ViewGate gate, List<String> warnings) {
        String type = condition.node("type").getString("").strip().toUpperCase(Locale.ROOT);
        switch (type) {
            case "VAULT_MONEY", "MONEY" ->
                gate.add("has-money:" + condition.node("amount").getString("0"));
            case "XP_LEVEL", "LEVEL" ->
                gate.add("has-level:" + condition.node("amount").getString("0"));
            case "XP_POINTS", "XP" ->
                gate.add("has-exp:" + condition.node("amount").getString("0"));
            case "ITEM" -> gate.add(itemCondition(condition));
            case "PERMISSION" -> gate.add(permissionCondition(condition));
            case "WORLD" -> worldCondition(condition, gate, warnings);
            case "PLACEHOLDER" -> placeholderCondition(condition, gate, warnings);
            case "EXPRESSION" ->
                gate.add("expr:" + subVars(condition.node("expression").getString("")));
            case "PLACEHOLDER_EQUALS" -> gate.add("equals-ignorecase:" + operands(condition));
            case "PLACEHOLDER_CONTAINS" -> gate.add("contains:" + operands(condition));
            case "PLACEHOLDER_GREATER_THAN" -> gate.add("expr:" + placeholder(condition) + " > " + value(condition));
            case "PLACEHOLDER_LESS_THAN" -> gate.add("expr:" + placeholder(condition) + " < " + value(condition));
            case "SCRIPT" -> warnings.add("skipped a SCRIPT condition; JavaScript conditions are not supported");
            default -> warnings.add("skipped unsupported condition type '" + type + "'");
        }
    }

    /** An {@code ITEM} condition → our {@code has-item:<material> [amount]}; a blank amount is dropped. */
    private static String itemCondition(ConfigurationNode condition) {
        String material = condition.node("material").getString("").strip();
        String amount = condition.node("amount").getString("").strip();
        return amount.isEmpty() ? "has-item:" + material : "has-item:" + material + " " + amount;
    }

    /** A {@code PERMISSION} condition → {@code perm:<node>}, honouring a leading {@code !} negation. */
    private static String permissionCondition(ConfigurationNode condition) {
        String permission = condition.node("permission").getString("").strip();
        boolean inverted = permission.startsWith("!");
        String node = inverted ? permission.substring(1).strip() : permission;
        return (inverted ? "!perm:" : "perm:") + node;
    }

    /**
     * Map a {@code WORLD} condition onto our single-name {@code world} gate. A single world is one mandatory
     * {@code world:<name>}; a whitelist of several is an OR-group ({@code minimum = 1}); a {@code blacklist} inverts
     * each world into a mandatory {@code !world:<name>} so the viewer must be in none of them. An empty list is skipped.
     */
    private void worldCondition(ConfigurationNode condition, ViewGate gate, List<String> warnings) {
        List<String> worlds = worldNames(condition);
        if (worlds.isEmpty()) {
            warnings.add("skipped a WORLD condition that names no world");
            return;
        }
        if (condition.node("blacklist").getBoolean(false)) {
            worlds.forEach(world -> gate.add("!world:" + world));
        } else if (worlds.size() == 1) {
            gate.add("world:" + worlds.get(0));
        } else {
            gate.orMembership(worlds.stream().map(world -> "world:" + world).toList(), warnings);
        }
    }

    /** The worlds a {@code WORLD} condition names: its {@code worlds} list, else its single {@code world}, else none. */
    private static List<String> worldNames(ConfigurationNode condition) {
        List<String> worlds = stringList(condition.node("worlds"));
        if (!worlds.isEmpty()) {
            return new ArrayList<>(worlds);
        }
        String single = condition.node("world").getString("").strip();
        return single.isEmpty() ? new ArrayList<>() : new ArrayList<>(List.of(single));
    }

    /** Map a {@code PLACEHOLDER} comparison onto our condition token; an unknown operator is dropped with a warning. */
    private void placeholderCondition(ConfigurationNode condition, ViewGate gate, List<String> warnings) {
        String placeholder = placeholder(condition);
        String value = value(condition);
        String operator = condition.node("operator").getString("==").strip().toLowerCase(Locale.ROOT);
        String token = placeholderToken(operator, placeholder, value);
        if (token == null) {
            warnings.add("skipped a PLACEHOLDER condition with unknown operator '" + operator + "'");
            return;
        }
        gate.add(token);
    }

    /**
     * The condition token for an OGUI {@code PLACEHOLDER} operator. A numeric/relational operator becomes an
     * {@code expr} (which expands the placeholder then evaluates); the string operators become the matching
     * {@code contains} / {@code equals-ignorecase} (an exact {@code equals} relaxes to case-insensitive, as no
     * exact-case condition exists). An unknown operator yields {@code null}.
     */
    private static @Nullable String placeholderToken(String operator, String placeholder, String value) {
        return switch (operator) {
            case "==", "=", "!=", ">", "<", ">=", "<=" ->
                "expr:" + placeholder + " " + (operator.equals("=") ? "==" : operator) + " " + value;
            case "equals" -> "equals-ignorecase:" + placeholder + " " + value;
            case "not_equals" -> "!equals-ignorecase:" + placeholder + " " + value;
            case "contains" -> "contains:" + placeholder + " " + value;
            default -> null;
        };
    }

    /** The {@code <placeholder> <value>} operand pair of a placeholder-compare condition, in our two-operand form. */
    private static String operands(ConfigurationNode condition) {
        return placeholder(condition) + " " + value(condition);
    }

    /** The {@code placeholder} operand of a condition, with {@code {player}} rewritten. */
    private static String placeholder(ConfigurationNode condition) {
        return subVars(condition.node("placeholder").getString("").strip());
    }

    /** The {@code value} operand of a condition, with {@code {player}} rewritten. */
    private static String value(ConfigurationNode condition) {
        return subVars(condition.node("value").getString("").strip());
    }

    // --- shared helpers -----------------------------------------------------------------------------------------

    /** Rewrite OGUI's {@code {player}} placeholder to our {@code %player%} so a converted string resolves live. */
    private static String subVars(String text) {
        return text.replace("{player}", "%player%");
    }

    /** The first non-empty string among {@code keys} of {@code node}, or empty when none carries a value. */
    private static String first(ConfigurationNode node, String... keys) {
        for (String key : keys) {
            String value = node.node(key).getString("");
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    /** Map one OGUI click type onto our HOCON gesture key, or {@code null} when it is one we do not model. */
    private static @Nullable String gestureFor(String click) {
        return switch (click.strip().toUpperCase(Locale.ROOT)) {
            case "ANY", "ALL", "" -> "any";
            case "LEFT", "LEFT_CLICK" -> "left";
            case "RIGHT", "RIGHT_CLICK" -> "right";
            case "SHIFT_LEFT", "SHIFT_LEFT_CLICK" -> "shift_left";
            case "SHIFT_RIGHT", "SHIFT_RIGHT_CLICK" -> "shift_right";
            case "MIDDLE", "MIDDLE_CLICK" -> "middle";
            default -> null;
        };
    }

    /** Record the legacy-colour warning at most once when a name or any lore line still carries {@code &}/{@code #hex}. */
    private void warnLegacyOnce(String name, List<String> lore, List<String> warnings) {
        boolean legacy = hasLegacy(name) || lore.stream().anyMatch(OguiConverter::hasLegacy);
        if (legacy && !warnings.contains(LEGACY_COLOUR_WARNING)) {
            warnings.add(LEGACY_COLOUR_WARNING);
        }
    }

    /** Whether a line carries a legacy {@code &} code or a {@code #rrggbb} hex colour our renderer still resolves. */
    private static boolean hasLegacy(String line) {
        return line.contains("&") || HEX_COLOUR.matcher(line).find();
    }

    /** Warn that an item's {@code else_item} fallback (shown when its condition fails) is not representable here. */
    private void warnElseItem(String key, ConfigurationNode item, List<String> warnings) {
        boolean present = isPresent(item.node("else_item")) || isPresent(item.node("else-item"));
        if (present) {
            warnings.add("item '" + key
                    + "' has an else_item fallback; our view gate hides the item instead. The fallback was dropped");
        }
    }

    /** Whether a node was actually present in the source document. */
    private static boolean isPresent(ConfigurationNode node) {
        return !node.virtual() && !node.isNull();
    }

    /** Set {@code target} to {@code value} only when the OGUI {@code source} node was actually present. */
    private static void setIfPresent(ConfigurationNode source, CommentedConfigurationNode target, String value)
            throws SerializationException {
        if (isPresent(source)) {
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

    /** Parse an OGUI YAML document; a fundamentally malformed document raises rather than silently emptying. */
    private static ConfigurationNode parseYaml(String yaml) {
        try {
            return YamlConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new StringReader(yaml)))
                    .build()
                    .load();
        } catch (ConfigurateException failure) {
            throw new IllegalArgumentException("could not parse OGUI YAML", failure);
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

    /**
     * The requirements a converted item's {@code view} gate accumulates. Most conditions are mandatory (an AND list);
     * a single multi-world {@code WORLD} whitelist is instead an OR-group written with {@code minimum = 1}. The two
     * cannot be combined precisely, a positive minimum applies to the whole list, so when a whitelist meets other
     * requirements the group folds into the mandatory list (over-restrictive, fails closed) with a warning.
     */
    private static final class ViewGate {

        private final List<String> mandatory = new ArrayList<>();
        private final List<String> orGroup = new ArrayList<>();

        /** Add one mandatory (AND) condition token to the gate. */
        void add(String token) {
            mandatory.add(token);
        }

        /** Record a multi-world membership OR-group; only the first such group is representable, the rest are dropped. */
        void orMembership(List<String> tokens, List<String> warnings) {
            if (!orGroup.isEmpty()) {
                warnings.add("a second multi-world WORLD condition was dropped; only one OR-group is representable");
                return;
            }
            orGroup.addAll(tokens);
        }

        /** Write the accumulated gate onto the item's {@code view} block, or nothing when the gate is empty. */
        void writeTo(CommentedConfigurationNode out, List<String> warnings) throws SerializationException {
            if (mandatory.isEmpty() && orGroup.isEmpty()) {
                return;
            }
            CommentedConfigurationNode view = out.node("view");
            if (mandatory.isEmpty()) {
                view.node("requirements").set(orGroup);
                view.node("minimum").set(1);
                return;
            }
            List<String> all = new ArrayList<>(mandatory);
            if (!orGroup.isEmpty()) {
                warnings.add("combined a multi-world WORLD membership with other requirements; "
                        + "emitted them all as mandatory, verify");
                all.addAll(orGroup);
            }
            view.node("requirements").set(all);
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
