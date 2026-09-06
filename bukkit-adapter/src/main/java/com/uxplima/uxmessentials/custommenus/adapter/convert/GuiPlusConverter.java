package com.uxplima.uxmessentials.custommenus.adapter.convert;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 * Converts a GUIPlus GUI YAML into an equivalent uxmEssentials HOCON menu spec, so an operator migrating off GUIPlus
 * keeps their menus. GUIPlus is a closed-source paid plugin, so this reads the format its wiki documents rather than
 * mirroring a source class: a GUI is a top-level {@code type} / {@code rows} / {@code title} / {@code permission} plus
 * a {@code scenes} map, and each scene holds an {@code items} map of typed {@code click-events} and {@code conditions}.
 * It emits the {@code title} / {@code rows} / {@code items { … }} shape
 * {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader} loads, mirroring the sibling
 * {@link DeluxeMenusConverter}, {@link ZMenuConverter} and {@link OguiConverter}.
 *
 * <p>GUIPlus is multi-<em>scene</em> (a scene is a page/state we do not model) so only the first scene is converted
 * (the {@code '0'} key when present, else the first scene in document order); a menu with more than one scene is
 * converted from its first scene with a warning naming the rest. Each item's {@code click-events} is a map keyed by
 * event type ({@code message}, {@code money-give}, {@code teleport}, …), each carrying an optional {@code clickType}
 * that routes it onto our matching gesture ({@code LEFT} → {@code left}, absent/{@code NONE} → {@code any}); the mapped
 * action refs are collected per gesture. Each item's {@code conditions} is likewise a map keyed by condition type, each
 * with an optional {@code inverted} flag; a mapped condition becomes an item {@code view} requirement (inverted → a
 * leading {@code !}), and a {@code conditionFailMessage} becomes the view's {@code deny} action.
 *
 * <p>The refs are written in our engine's bare {@code id:value} form (not bracketed), the same form the sibling
 * converters emit, so the output re-enters the engine through the ordinary {@code menus/} loader. GUIPlus placeholders
 * ({@code %player%} / {@code %executor%}) are carried through verbatim, our renderer resolves both, while
 * {@code %input%} has no engine equivalent and is left verbatim with a one-time warning.
 *
 * <p>The conversion covers the common surface and degrades gracefully everywhere else: an event or condition type with
 * no clean engine equivalent (scene navigation, a chat/player picker, a serialized item stack, a math save-format, a
 * set-money) is skipped with a warning, an {@code executionDelay} is dropped with a warning, and a legacy colour code
 * ({@code &a}, {@code §c}, {@code #77ff77}) is left as written and warned about once. Every such compromise is a
 * {@link ConversionResult#warnings() warning} the caller logs, never a thrown exception; only a fundamentally
 * unparsable YAML document raises, which the calling service catches per file so one bad export never aborts a whole
 * directory convert. The whole class is Configurate-only and touches no Bukkit type, so it is exercised by plain JUnit.
 */
public final class GuiPlusConverter {

    /** A chest tops out at six rows; a GUIPlus {@code rows} beyond that is clamped with a warning. */
    private static final int MAX_ROWS = 6;

    /** The fallback row count for a menu that declares no {@code rows}. */
    private static final int DEFAULT_ROWS = 6;

    /** A {@code #rrggbb} hex colour code; carried through verbatim like {@code &}/{@code §} codes but warned once. */
    private static final Pattern HEX_COLOUR = Pattern.compile("#[0-9a-fA-F]{6}");

    /** A standalone {@code =}, one not part of {@code <= >= != ==}, that a converted expression rewrites to {@code ==}. */
    private static final Pattern LONE_EQUALS = Pattern.compile("(?<![=<>!])=(?!=)");

    /** The warning emitted once when a menu still carries legacy colour codes the operator may want to move. */
    private static final String LEGACY_COLOUR_WARNING =
            "menu uses legacy '&', '§' or '#rrggbb' colour codes; they still render, but consider moving them to MiniMessage";

    /** The warning emitted once when a converted string still carries the unmodelled {@code %input%} placeholder. */
    private static final String INPUT_PLACEHOLDER_WARNING =
            "menu uses the %input% placeholder, which the engine has no equivalent for; it was left verbatim";

    /** The warning emitted once when at least one item's enchantments were copied across for the operator to verify. */
    private static final String ENCHANT_WARNING =
            "item enchantments were converted verbatim; verify the enchant names resolve on your Minecraft version";

    /** The warning emitted once when a click-event carried an execution delay our bare-ref form cannot express. */
    private static final String DELAY_WARNING =
            "a click-event executionDelay was dropped; the converted action runs without the delay";

    /** The warning emitted once when a cooldown condition's duration was dropped (our cooldown length is quota-driven). */
    private static final String COOLDOWN_DURATION_WARNING =
            "a cooldown condition's duration was dropped; the cooldown length comes from the uxmessentials.cooldown "
                    + "quota nodes, not the menu";

    /**
     * Convert a GUIPlus GUI YAML document into our HOCON spec text plus the warnings the conversion accrued. A
     * fundamentally unparsable YAML document raises {@link IllegalArgumentException}; every recoverable compromise is a
     * warning, not a throw.
     */
    public ConversionResult convert(String guiPlusYaml) {
        Objects.requireNonNull(guiPlusYaml, "guiPlusYaml");
        ConfigurationNode source = parseYaml(guiPlusYaml);
        List<String> warnings = new ArrayList<>();
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder().build();
        CommentedConfigurationNode spec = loader.createNode();
        buildSpec(source, spec, warnings);
        return new ConversionResult(render(spec), List.copyOf(warnings));
    }

    /** Populate our spec node from the GUIPlus source: title, open command, inventory type, rows, open perm, items. */
    private void buildSpec(ConfigurationNode source, CommentedConfigurationNode spec, List<String> warnings) {
        try {
            String title = source.node("title").getString("");
            spec.node("title").set(title);
            warnLegacyOnce(title, List.of(), warnings);
            openCommandNote(source).ifPresent(note -> spec.node("title").comment(note));
            writeInventoryType(source, spec, warnings);
            spec.node("rows").set(rows(source, warnings));
            writeOpenRequirement(source, spec);
            buildItems(firstScene(source, warnings).node("items"), spec.node("items"), warnings);
        } catch (SerializationException failure) {
            throw new IllegalStateException("failed to build converted menu spec", failure);
        }
    }

    /** The GUI's {@code commandAlias} as a note: our opener wiring is separate, as in the sibling converters. */
    private Optional<String> openCommandNote(ConfigurationNode source) {
        String alias = source.node("commandAlias").getString("").strip();
        if (alias.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("open command: /" + alias + ". Wire this via /menu open or an openers.conf entry");
    }

    /** Map a non-chest {@code type} onto our {@code inventory-type}; a chest is omitted, an unknown type warns. */
    private void writeInventoryType(ConfigurationNode source, CommentedConfigurationNode spec, List<String> warnings)
            throws SerializationException {
        String type = source.node("type").getString("chest").strip().toLowerCase(Locale.ROOT);
        switch (type) {
            case "", "chest" -> {
                // A chest is the default window; no inventory-type is emitted, so our rows-based sizing applies.
            }
            case "hopper", "dropper", "dispenser" -> spec.node("inventory-type").set(type);
            default -> warnings.add("inventory type '" + type + "' is not modelled; converted as a chest menu");
        }
    }

    /** The GUIPlus {@code rows} as a clamped row count: absent → six rows, oversized → six + a warning. */
    private int rows(ConfigurationNode source, List<String> warnings) {
        ConfigurationNode rows = source.node("rows");
        if (rows.virtual() || rows.isNull()) {
            return DEFAULT_ROWS;
        }
        int declared = rows.getInt(DEFAULT_ROWS);
        if (declared > MAX_ROWS) {
            warnings.add("rows " + declared + " exceeds a chest maximum; clamped to " + MAX_ROWS);
        }
        return Math.max(1, Math.min(MAX_ROWS, declared));
    }

    /** A menu-level {@code permission} becomes the menu's {@code open-requirement} perm gate; absent leaves none. */
    private void writeOpenRequirement(ConfigurationNode source, CommentedConfigurationNode spec)
            throws SerializationException {
        String permission = source.node("permission").getString("").strip();
        if (!permission.isEmpty()) {
            spec.node("open-requirement").set(List.of("perm:" + permission));
        }
    }

    /**
     * The single scene to convert. GUIPlus menus are multi-scene (a scene is a page/state we do not model), so only the
     * first is converted: the {@code '0'} key when present, else the first scene in document order. A menu with more
     * than one scene keeps its first and warns, naming the total.
     */
    private ConfigurationNode firstScene(ConfigurationNode source, List<String> warnings) {
        ConfigurationNode scenes = source.node("scenes");
        Map<Object, ? extends ConfigurationNode> byId = scenes.childrenMap();
        if (byId.isEmpty()) {
            return scenes;
        }
        if (byId.size() > 1) {
            String id = source.node("id").getString(source.node("title").getString("this menu"));
            warnings.add("menu '" + id + "' has " + byId.size() + " scenes; only the first was converted");
        }
        ConfigurationNode zero = scenes.node("0");
        return !zero.virtual() && !zero.isNull()
                ? zero
                : byId.values().iterator().next();
    }

    /** Convert every {@code items.<id>} block of the scene onto our {@code items.<id>} spec. */
    private void buildItems(ConfigurationNode items, CommentedConfigurationNode out, List<String> warnings)
            throws SerializationException {
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                items.childrenMap().entrySet()) {
            String id = String.valueOf(entry.getKey());
            buildItem(id, entry.getValue(), out.node(id), warnings);
        }
    }

    /** Convert one GUIPlus item: material, name, lore, slot, decor, its click-events and its condition view gate. */
    private void buildItem(String id, ConfigurationNode item, CommentedConfigurationNode out, List<String> warnings)
            throws SerializationException {
        setIfPresent(item.node("item"), out.node("material"));
        setIfPresent(item.node("item-name"), out.node("name"));
        List<String> lore = stringList(item.node("item-lore"));
        if (!lore.isEmpty()) {
            out.node("lore").set(lore);
        }
        warnLegacyOnce(item.node("item-name").getString(""), lore, warnings);
        writeSlot(id, item, out, warnings);
        buildDecor(item, out, warnings);
        buildClicks(item, out, warnings);
        buildView(item, out, warnings);
    }

    /** Copy the item's zero-based {@code slot} onto our {@code slots}; an item with no slot is left unpositioned + warned. */
    private void writeSlot(String id, ConfigurationNode item, CommentedConfigurationNode out, List<String> warnings)
            throws SerializationException {
        ConfigurationNode slot = item.node("slot");
        if (slot.virtual() || slot.isNull()) {
            warnings.add("item '" + id + "' has no slot; position it manually");
            return;
        }
        out.node("slots").set(List.of(String.valueOf(slot.getInt(0))));
    }

    /**
     * Map the item's appearance extras onto our {@code decor} block, best-effort. The {@code amount},
     * {@code unbreakable} flag and {@code item-flags} map straight across; {@code item-enchants} (a map of
     * enchant → level) becomes our {@code enchantments} list with a one-time verify warning; {@code item-attributes}
     * has no clean equivalent and is dropped with a warning.
     */
    private void buildDecor(ConfigurationNode item, CommentedConfigurationNode out, List<String> warnings)
            throws SerializationException {
        if (!item.node("amount").virtual()) {
            out.node("decor", "amount").set(item.node("amount").getInt(1));
        }
        if (item.node("unbreakable").getBoolean(false)) {
            out.node("decor", "unbreakable").set(true);
        }
        List<String> flags = stringList(item.node("item-flags"));
        if (!flags.isEmpty()) {
            out.node("decor", "flags").set(flags);
        }
        List<String> enchants = enchantList(item.node("item-enchants"));
        if (!enchants.isEmpty()) {
            out.node("decor", "enchantments").set(enchants);
            warnOnce(ENCHANT_WARNING, warnings);
        }
        if (isPresent(item.node("item-attributes"))) {
            warnings.add("item-attributes have no engine equivalent and were dropped");
        }
    }

    /** A GUIPlus {@code item-enchants} map ({@code enchant: level}) as our {@code enchant:level} decor list. */
    private static List<String> enchantList(ConfigurationNode node) {
        if (node.virtual() || node.isNull()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                node.childrenMap().entrySet()) {
            out.add(entry.getKey() + ":" + entry.getValue().getInt(1));
        }
        return out;
    }

    // --- click events -------------------------------------------------------------------------------------------

    /**
     * Build the item's click actions from its {@code click-events} map. Each event is routed onto the gesture its
     * {@code clickType} names (absent/{@code NONE} → {@code any}) and its mapped refs are collected there, so several
     * events sharing a gesture concatenate in document order. An {@code executionDelay} is dropped with a warning.
     */
    private void buildClicks(ConfigurationNode item, CommentedConfigurationNode out, List<String> warnings)
            throws SerializationException {
        ConfigurationNode events = item.node("click-events");
        if (events.virtual() || events.isNull()) {
            return;
        }
        Map<String, List<String>> byGesture = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                events.childrenMap().entrySet()) {
            ConfigurationNode node = entry.getValue();
            warnDelay(node, warnings);
            List<String> refs = mapClickEvent(String.valueOf(entry.getKey()), node, warnings);
            if (!refs.isEmpty()) {
                byGesture
                        .computeIfAbsent(gestureOf(node, warnings), key -> new ArrayList<>())
                        .addAll(refs);
            }
        }
        for (Map.Entry<String, List<String>> gesture : byGesture.entrySet()) {
            out.node("click", gesture.getKey(), "actions").set(gesture.getValue());
        }
    }

    /** Warn once when an event carries an {@code executionDelay} our bare-ref action list cannot express. */
    private void warnDelay(ConfigurationNode node, List<String> warnings) {
        if (node.node("executionDelay").getInt(0) > 0) {
            warnOnce(DELAY_WARNING, warnings);
        }
    }

    /** The gesture an event's {@code clickType} routes onto; an unknown type warns and falls back to {@code any}. */
    private String gestureOf(ConfigurationNode node, List<String> warnings) {
        String click = node.node("clickType").getString("").strip().toUpperCase(Locale.ROOT);
        return switch (click) {
            case "", "NONE" -> "any";
            case "LEFT" -> "left";
            case "RIGHT" -> "right";
            case "MIDDLE" -> "middle";
            case "SHIFT_LEFT" -> "shift_left";
            case "SHIFT_RIGHT" -> "shift_right";
            default -> unknownClick(click, warnings);
        };
    }

    /** Record an unmodelled {@code clickType} and route it onto {@code any} so its action is not silently lost. */
    private static String unknownClick(String click, List<String> warnings) {
        warnings.add("unknown clickType '" + click + "'; its action was placed on the any gesture");
        return "any";
    }

    /**
     * Map one GUIPlus click-event onto our action refs. The common effects map onto registered vocabulary, a
     * {@code message} to our {@code message}, a {@code money-give} to {@code give-points}, a {@code teleport} to our
     * coordinate {@code teleport}, and so on. An event with no clean engine equivalent (a set-money, a scene jump, a
     * picker, a serialized-item give, a math save-format) is skipped with a warning. {@code %player%}/{@code %executor%}
     * pass through for our renderer; a {@code %input%} is left verbatim with a one-time warning.
     */
    private List<String> mapClickEvent(String rawType, ConfigurationNode node, List<String> warnings) {
        return switch (rawType.strip().toLowerCase(Locale.ROOT)) {
            case "message" -> valued("message", node.node("message"), warnings);
            case "command" -> commandRefs("command", node, warnings);
            case "console_command" -> commandRefs("console", node, warnings);
            case "title-click-event" -> titleRef(node, warnings);
            case "close-inventory" -> List.of("close");
            case "back" -> List.of("back");
            case "money-give" -> valued("give-points", node.node("amount"), warnings);
            case "money-remove" -> valued("take-points", node.node("amount"), warnings);
            case "player-points-remove" -> pointsRemove(node, warnings);
            case "sound-click-event" -> soundRef(node);
            case "teleport" -> teleportRef(node, warnings);
            case "server-click-event" -> valued("connect", node.node("server"), warnings);
            case "money-set" -> skip("money-set has no engine equivalent (there is no set-money action)", warnings);
            case "save-player-info" ->
                skip("save-player-info uses a math save-format with no engine equivalent", warnings);
            default -> skip("click-event '" + rawType + "' has no engine equivalent", warnings);
        };
    }

    /** A single valued ref ({@code message:hi}) from a node's text; a blank value is skipped, {@code %input%} warns. */
    private List<String> valued(String id, ConfigurationNode node, List<String> warnings) {
        String value = passthrough(node.getString(""), warnings).strip();
        return value.isEmpty() ? List.of() : List.of(id + ":" + value);
    }

    /**
     * A {@code command} or {@code console_command} event → one ref per entry of its {@code commands} list. A
     * {@code command} with {@code setOp: true} warns, our engine runs a player command as the player, never elevated.
     */
    private List<String> commandRefs(String id, ConfigurationNode node, List<String> warnings) {
        if (node.node("setOp").getBoolean(false)) {
            warnings.add("a command event set setOp; the converted command runs as the player, not elevated");
        }
        List<String> refs = new ArrayList<>();
        for (String command : stringList(node.node("commands"))) {
            refs.add(id + ":" + passthrough(command, warnings));
        }
        return refs;
    }

    /** A {@code player-points-remove} event → {@code take-points} (our PlayerPoints withdraw); a blank amount skips. */
    private List<String> pointsRemove(ConfigurationNode node, List<String> warnings) {
        List<String> refs = valued("take-points", node.node("amount"), warnings);
        if (!refs.isEmpty()) {
            warnings.add("player-points-remove was mapped to take-points (the PlayerPoints withdraw action)");
        }
        return refs;
    }

    /**
     * A {@code title-click-event} → our {@code title:<title>|<subtitle>|<fadeIn>|<stay>|<fadeOut>}. The GUIPlus
     * {@code title-format} is a {@code /}-delimited sequence of {@code key/value} pairs; the timings are emitted only
     * when all three parse as whole numbers, otherwise a title (and subtitle when present) alone is emitted. An event
     * with no title text is skipped with a warning.
     */
    private List<String> titleRef(ConfigurationNode node, List<String> warnings) {
        Map<String, String> parts = titleParts(node.node("title-format").getString(""));
        String title = passthrough(parts.getOrDefault("title", ""), warnings).strip();
        if (title.isEmpty()) {
            return skip("title-click-event has no title text", warnings);
        }
        String subtitle =
                passthrough(parts.getOrDefault("subtitle", ""), warnings).strip();
        Integer fadeIn = intOrNull(parts.get("fadein"));
        Integer stay = intOrNull(parts.get("stay"));
        Integer fadeOut = intOrNull(parts.get("fadeout"));
        if (fadeIn != null && stay != null && fadeOut != null) {
            return List.of("title:" + title + "|" + subtitle + "|" + fadeIn + "|" + stay + "|" + fadeOut);
        }
        return List.of(subtitle.isEmpty() ? "title:" + title : "title:" + title + "|" + subtitle);
    }

    /** Read a GUIPlus {@code title-format} into its {@code title}/{@code subtitle}/timing segments (keys lowercased). */
    private static Map<String, String> titleParts(String format) {
        Map<String, String> parts = new LinkedHashMap<>();
        List<String> tokens = Splitter.on('/').splitToList(format);
        for (int i = 0; i + 1 < tokens.size(); i++) {
            String key = tokens.get(i).strip().toLowerCase(Locale.ROOT);
            if (TITLE_KEYS.contains(key)) {
                parts.putIfAbsent(key, tokens.get(i + 1));
            }
        }
        return parts;
    }

    /** The recognised {@code title-format} keys, lowercased so a value between them is never mistaken for a key. */
    private static final List<String> TITLE_KEYS = List.of("title", "subtitle", "fadein", "stay", "fadeout");

    /**
     * A {@code teleport} event → our {@code teleport:<world> <x> <y> <z> [yaw] [pitch]}. GUIPlus stores the
     * destination as a comma-separated {@code world,x,y,z,yaw,pitch}; a {@code current} location or a bare world name
     * carries no coordinates our action needs and is skipped with a warning.
     */
    private List<String> teleportRef(ConfigurationNode node, List<String> warnings) {
        String location = node.node("location").getString("").strip();
        if (location.equalsIgnoreCase("current")) {
            return skip("teleport to 'current' has no fixed coordinates and was not converted", warnings);
        }
        List<String> parts = Splitter.on(',').trimResults().omitEmptyStrings().splitToList(location);
        if (parts.size() < 4) {
            return skip("teleport location '" + location + "' has no world and coordinates; skipped", warnings);
        }
        return List.of("teleport:" + String.join(" ", parts));
    }

    /** A {@code sound-click-event} → our {@code sound:<key> <volume> <pitch>}; a blank sound key is skipped. */
    private static List<String> soundRef(ConfigurationNode node) {
        String sound = node.node("sound").getString("").strip();
        if (sound.isEmpty()) {
            return List.of();
        }
        String volume = node.node("volume").getString("1").strip();
        String pitch = node.node("pitch").getString("1").strip();
        return List.of("sound:" + sound + " " + volume + " " + pitch);
    }

    // --- conditions ---------------------------------------------------------------------------------------------

    /**
     * Map the item's {@code conditions} onto its {@code view} gate. Each condition becomes a mandatory requirement
     * (its {@code inverted} flag → a leading {@code !}); a {@code conditionFailMessage} becomes the view's {@code deny}
     * action. A gate with no requirements and no deny writes nothing.
     */
    private void buildView(ConfigurationNode item, CommentedConfigurationNode out, List<String> warnings)
            throws SerializationException {
        List<String> requirements = new ArrayList<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                item.node("conditions").childrenMap().entrySet()) {
            ConfigurationNode node = entry.getValue();
            mapConditionToken(String.valueOf(entry.getKey()), node, warnings)
                    .ifPresent(
                            token -> requirements.add(node.node("inverted").getBoolean(false) ? "!" + token : token));
        }
        List<String> deny = denyMessage(item, warnings);
        if (requirements.isEmpty() && deny.isEmpty()) {
            return;
        }
        if (!requirements.isEmpty()) {
            out.node("view", "requirements").set(requirements);
        }
        if (!deny.isEmpty()) {
            out.node("view", "deny").set(deny);
        }
    }

    /**
     * Map one GUIPlus condition onto our condition token. Money, permission, slots, level, experience and PlayerPoints
     * map onto registered conditions; a {@code conditional-placeholder} becomes an {@code expr}; a {@code cooldown}
     * becomes our label-keyed {@code cooldown} (its duration is quota-driven, so the GUIPlus milliseconds are dropped
     * with a warning). A condition with no clean equivalent (an integer/double check, a serialized-item check) is
     * skipped with a warning.
     */
    private Optional<String> mapConditionToken(String rawType, ConfigurationNode node, List<String> warnings) {
        return switch (rawType.strip().toLowerCase(Locale.ROOT)) {
            case "has-money" -> valuedToken("has-money", node.node("required-balance"));
            case "has-permission" -> valuedToken("perm", node.node("permission"));
            case "has-empty-slots" -> valuedToken("has-empty-slots", node.node("free-slots"));
            case "level-required" -> valuedToken("has-level", node.node("level"));
            case "xp-required" -> valuedToken("has-exp", node.node("xp"));
            case "player-points" -> valuedToken("has-points", node.node("amount"));
            case "conditional-placeholder" -> exprToken(node, warnings);
            case "cooldown" -> cooldownToken(node, warnings);
            default -> skipCondition(rawType, warnings);
        };
    }

    /** A valued condition token ({@code has-money:100}) from a node; a blank value yields no condition. */
    private static Optional<String> valuedToken(String id, ConfigurationNode node) {
        String value = node.getString("").strip();
        return value.isEmpty() ? Optional.empty() : Optional.of(id + ":" + value);
    }

    /**
     * A {@code conditional-placeholder} → our {@code expr}. GUIPlus writes its comparison in a
     * {@code conditional_condition} string whose operators are {@code = != < > <= >=} plus the parenthesised
     * {@code (=)}/{@code (!=)}; those are rewritten to the {@code == != < > <= >=} our expression evaluator reads. A
     * blank condition yields nothing; a {@code %input%} in it warns.
     */
    private Optional<String> exprToken(ConfigurationNode node, List<String> warnings) {
        String raw = node.node("conditional_condition").getString("").strip();
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("expr:" + translateOperators(passthrough(raw, warnings)));
    }

    /** Rewrite GUIPlus comparison operators to our expression evaluator's: {@code (=)}/{@code (!=)} and a lone {@code =}. */
    private static String translateOperators(String condition) {
        String rewritten = condition.replace("(!=)", "!=").replace("(=)", "==");
        return LONE_EQUALS.matcher(rewritten).replaceAll("==");
    }

    /** A {@code cooldown} condition → our label-keyed {@code cooldown:<id>}; the GUIPlus millisecond duration is dropped. */
    private Optional<String> cooldownToken(ConfigurationNode node, List<String> warnings) {
        String id = node.node("id").getString("").strip();
        if (id.isEmpty()) {
            return Optional.empty();
        }
        warnOnce(COOLDOWN_DURATION_WARNING, warnings);
        return Optional.of("cooldown:" + id);
    }

    /** A {@code conditionFailMessage} → the view's {@code deny} message action, or nothing when blank. */
    private List<String> denyMessage(ConfigurationNode item, List<String> warnings) {
        String message = item.node("conditionFailMessage").getString("");
        if (message.isBlank()) {
            return List.of();
        }
        return List.of("message:" + passthrough(message, warnings));
    }

    /** Record that a condition type we do not model was dropped, and contribute no requirement. */
    private static Optional<String> skipCondition(String type, List<String> warnings) {
        warnings.add("condition '" + type + "' has no engine equivalent and was dropped");
        return Optional.empty();
    }

    // --- shared helpers -----------------------------------------------------------------------------------------

    /** Record {@code warning} and return an empty ref list: the value a skipped click-event contributes. */
    private static List<String> skip(String warning, List<String> warnings) {
        warnings.add(warning);
        return List.of();
    }

    /** Leave GUIPlus placeholders untouched (our renderer resolves them), warning once when {@code %input%} appears. */
    private String passthrough(String text, List<String> warnings) {
        if (text.contains("%input%")) {
            warnOnce(INPUT_PLACEHOLDER_WARNING, warnings);
        }
        return text;
    }

    /** The whole number in {@code token}, or {@code null} when it is absent, blank or not a whole number. */
    private static @Nullable Integer intOrNull(@Nullable String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(token.strip());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /** Record {@code warning} at most once, so a repeated compromise does not flood the operator's convert report. */
    private static void warnOnce(String warning, List<String> warnings) {
        if (!warnings.contains(warning)) {
            warnings.add(warning);
        }
    }

    /** Record the legacy-colour warning once when a name/title or any lore line carries {@code &}/{@code §}/{@code #hex}. */
    private void warnLegacyOnce(String text, List<String> lore, List<String> warnings) {
        boolean legacy = hasLegacy(text) || lore.stream().anyMatch(GuiPlusConverter::hasLegacy);
        if (legacy) {
            warnOnce(LEGACY_COLOUR_WARNING, warnings);
        }
    }

    /** Whether a line carries a legacy {@code &}/{@code §} code or a {@code #rrggbb} hex colour our renderer resolves. */
    private static boolean hasLegacy(String line) {
        return line.contains("&")
                || line.contains("§")
                || HEX_COLOUR.matcher(line).find();
    }

    /** Whether a node was actually present in the source document. */
    private static boolean isPresent(ConfigurationNode node) {
        return !node.virtual() && !node.isNull();
    }

    /** Copy {@code source}'s string value onto {@code target} only when the GUIPlus node was actually present. */
    private static void setIfPresent(ConfigurationNode source, CommentedConfigurationNode target)
            throws SerializationException {
        if (isPresent(source)) {
            target.set(source.getString(""));
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

    /** Parse a GUIPlus YAML document; a fundamentally malformed document raises rather than silently emptying. */
    private static ConfigurationNode parseYaml(String yaml) {
        try {
            return YamlConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new StringReader(yaml)))
                    .build()
                    .load();
        } catch (ConfigurateException failure) {
            throw new IllegalArgumentException("could not parse GUIPlus YAML", failure);
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
