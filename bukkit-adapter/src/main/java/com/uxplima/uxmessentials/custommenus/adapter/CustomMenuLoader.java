package com.uxplima.uxmessentials.custommenus.adapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.OpenCommandSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ArgumentSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ArgumentSpec.ArgType;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecException;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Reads the operator's {@code menus/*.conf} files into the running menu engine. Each top-level {@code .conf}
 * becomes a spec registered under its file name (sans extension): the file is parsed by the Phase-1
 * {@link MenuSpecLoader} and its refs validated against the registered {@link MenuBindings} before it reaches
 * {@link Menus}. A file that fails to parse, or that names an action/condition/placeholder no binding provides,
 * is skipped with a logged warning rather than aborting the whole load. One operator typo never hides every
 * other menu. An absent {@code menus/} directory is normal on a fresh install and yields an empty result.
 */
public final class CustomMenuLoader {

    /** The reserved opener-item config file that shares the menus directory but is not a menu spec. */
    private static final String RESERVED_OPENERS_FILE = "openers.conf";

    /** The reserved shared-patterns file: item templates every menu may name, loaded once per pass, not a menu spec. */
    private static final String RESERVED_PATTERNS_FILE = "patterns.conf";

    /** The reserved global-placeholders file: custom {@code %name%} definitions, loaded once per pass, not a menu spec. */
    private static final String RESERVED_PLACEHOLDERS_FILE = "placeholders.conf";

    private final MenuSpecLoader specLoader;
    private final MenuBindings bindings;
    private final Menus menus;
    private final Logger log;
    private final CustomPlaceholders customPlaceholders;

    public CustomMenuLoader(
            MenuSpecLoader specLoader,
            MenuBindings bindings,
            Menus menus,
            Logger log,
            CustomPlaceholders customPlaceholders) {
        this.specLoader = Objects.requireNonNull(specLoader, "specLoader");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.log = Objects.requireNonNull(log, "log");
        this.customPlaceholders = Objects.requireNonNull(customPlaceholders, "customPlaceholders");
    }

    /**
     * The four-argument form kept for the call sites (and tests) that predate the global placeholders file. It builds
     * a fresh {@link CustomPlaceholders} over the same {@code bindings}, which registers its fallback there, so the
     * loader still reads {@code placeholders.conf} and validates a {@code %name%} spec without the caller threading one.
     */
    public CustomMenuLoader(MenuSpecLoader specLoader, MenuBindings bindings, Menus menus, Logger log) {
        this(specLoader, bindings, menus, log, new CustomPlaceholders(bindings));
    }

    /**
     * The outcome of one load pass: how many specs registered, the ids skipped over a parse or ref error, and the
     * operator-declared open commands the survivors carry (keyed by menu id, from each file's {@code command {}}
     * block). {@link #loadedNames()} carries the registered ids so {@code /menu list} can name what is open, and
     * {@link #openCommands()} lets the wiring register a {@code /shop}-style command per menu that declared one; a
     * menu with no {@code command {}} block contributes no entry. The two convenience constructors keep the older
     * {@code (loadedNames, skipped)} shape working for callers and tests that predate open commands.
     */
    public record LoadResult(
            int loaded, List<String> loadedNames, List<String> skipped, Map<String, OpenCommandSpec> openCommands) {

        public LoadResult {
            loadedNames = List.copyOf(Objects.requireNonNull(loadedNames, "loadedNames"));
            skipped = List.copyOf(Objects.requireNonNull(skipped, "skipped"));
            openCommands = Map.copyOf(Objects.requireNonNull(openCommands, "openCommands"));
        }

        /** Build a result from the registered ids, the skipped ids, and the parsed open commands. */
        public LoadResult(List<String> loadedNames, List<String> skipped, Map<String, OpenCommandSpec> openCommands) {
            this(loadedNames.size(), loadedNames, skipped, openCommands);
        }

        /** Build a result from the registered ids and the skipped ids, with no open commands. */
        public LoadResult(List<String> loadedNames, List<String> skipped) {
            this(loadedNames, skipped, Map.of());
        }
    }

    /**
     * Parse, validate and register every {@code *.conf} directly under {@code menusDir}. A missing directory is
     * treated as "no operator menus yet" and returns an empty result; a present directory is walked at its top
     * level, each file loaded independently so a single bad spec cannot fail the rest.
     */
    public LoadResult loadFrom(Path menusDir) {
        Objects.requireNonNull(menusDir, "menusDir");
        if (!Files.isDirectory(menusDir)) {
            customPlaceholders.reload(Map.of());
            return new LoadResult(List.of(), List.of());
        }
        ConfigurationNode globalPatterns = loadGlobalPatterns(menusDir);
        // Publish the custom %name% definitions before any spec is validated, so a %welcome% token a menu references
        // is already claimed by the custom fallback when validate() checks it; a /menu reload re-reads the file here.
        customPlaceholders.reload(loadGlobalPlaceholders(menusDir));
        List<String> loaded = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Map<String, OpenCommandSpec> openCommands = new LinkedHashMap<>();
        try (Stream<Path> entries = Files.list(menusDir)) {
            for (Path file : confFiles(entries)) {
                loadOne(file, globalPatterns, loaded, skipped, openCommands);
            }
        } catch (java.io.IOException failure) {
            log.warn("could not list menu directory {} : {}", menusDir, String.valueOf(failure.getMessage()));
        }
        log.info("loaded {} custom menus, skipped {}", loaded.size(), skipped.size());
        return new LoadResult(loaded, skipped, openCommands);
    }

    /**
     * The outcome of a single-file reload. {@code /menu reload <menu>}. {@code found} is whether a
     * {@code menus/<name>.conf} menu spec file existed at all (a missing one is the not-found reply); {@code loaded}
     * and {@code skipped} count that one file's outcome, so a re-registered menu reports {@code loaded=1, skipped=0}
     * and one that failed to parse or named an unknown id reports {@code loaded=0, skipped=1}.
     */
    public record SingleLoad(String name, boolean found, int loaded, int skipped) {
        public SingleLoad {
            Objects.requireNonNull(name, "name");
        }
    }

    /**
     * Re-parse, validate and re-register the single {@code menus/<name>.conf} spec, the per-menu {@code /menu reload
     * <menu>} target. A missing menus directory, a reserved file name ({@code openers}/{@code patterns}/{@code
     * placeholders}), or an absent {@code <name>.conf} yields a not-found result; otherwise the one file is loaded
     * exactly as {@link #loadFrom} loads each, through the shared {@link #loadOne}. The global patterns and custom
     * placeholders are re-read first (a menu may reference them, and validation checks against the current set) and
     * the survivor is re-registered into {@link Menus}, overwriting the previous spec under that id. One bad spec is
     * skipped with a logged warning, never thrown. Runs once per command, never on a hot path.
     */
    public SingleLoad loadSingle(Path menusDir, String name) {
        Objects.requireNonNull(menusDir, "menusDir");
        Objects.requireNonNull(name, "name");
        Path file = menusDir.resolve(name + ".conf");
        if (!Files.isDirectory(menusDir) || isReserved(file.getFileName().toString()) || !Files.isRegularFile(file)) {
            return new SingleLoad(name, false, 0, 0);
        }
        ConfigurationNode globalPatterns = loadGlobalPatterns(menusDir);
        customPlaceholders.reload(loadGlobalPlaceholders(menusDir));
        List<String> loaded = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        loadOne(file, globalPatterns, loaded, skipped, new LinkedHashMap<>());
        return new SingleLoad(name, true, loaded.size(), skipped.size());
    }

    /**
     * The {@code .conf} files directly under the menus directory that are menu specs, in a stable order. Three files
     * are reserved and live alongside the menus without being specs: {@code openers.conf} (opener-item config,
     * {@code OpenerLoader}), {@code patterns.conf} (shared item templates, loaded once via {@link #loadGlobalPatterns})
     * and {@code placeholders.conf} (custom {@code %name%} definitions, loaded once via {@link #loadGlobalPlaceholders}).
     * All three are excluded here, parsing any of them as a menu spec would only skip it with a spurious warning.
     */
    private static List<Path> confFiles(Stream<Path> entries) {
        return entries.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".conf"))
                .filter(p -> !isReserved(p.getFileName().toString()))
                .sorted()
                .toList();
    }

    /**
     * Whether a file name is one of the reserved non-menu configs ({@code openers.conf} / {@code patterns.conf} /
     * {@code placeholders.conf}).
     */
    private static boolean isReserved(String fileName) {
        return fileName.equalsIgnoreCase(RESERVED_OPENERS_FILE)
                || fileName.equalsIgnoreCase(RESERVED_PATTERNS_FILE)
                || fileName.equalsIgnoreCase(RESERVED_PLACEHOLDERS_FILE);
    }

    /**
     * Read the optional shared {@code patterns.conf} once per load pass into its {@code patterns} block, a node of
     * reusable item templates every menu in the pass may name. Shared patterns are opt-in, so a missing or unreadable
     * file yields an empty node rather than an error. This runs once here, never on a hot path; a {@code /menu reload}
     * re-reads it as part of the same {@link #loadFrom} re-run.
     */
    private ConfigurationNode loadGlobalPatterns(Path menusDir) {
        Path file = menusDir.resolve(RESERVED_PATTERNS_FILE);
        if (!Files.isRegularFile(file)) {
            return CommentedConfigurationNode.root();
        }
        try {
            return HoconConfigurationLoader.builder().path(file).build().load().node("patterns");
        } catch (RuntimeException | java.io.IOException invalid) {
            log.warn("could not read shared menu patterns {} : {}", file, String.valueOf(invalid.getMessage()));
            return CommentedConfigurationNode.root();
        }
    }

    /**
     * Read the optional global {@code placeholders.conf} once per load pass into a {@code name -> template} map, the
     * custom {@code %name%} placeholders every menu in the pass may reference. Each child of the {@code placeholders}
     * block maps its key to its string value. Custom placeholders are opt-in, so a missing or unreadable file yields
     * an empty map rather than an error. This runs once here, never on a hot path; a {@code /menu reload} re-reads it
     * as part of the same {@link #loadFrom} re-run.
     */
    private Map<String, String> loadGlobalPlaceholders(Path menusDir) {
        Path file = menusDir.resolve(RESERVED_PLACEHOLDERS_FILE);
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        try {
            ConfigurationNode node =
                    HoconConfigurationLoader.builder().path(file).build().load().node("placeholders");
            return readPlaceholderDefinitions(node);
        } catch (RuntimeException | java.io.IOException invalid) {
            log.warn("could not read custom menu placeholders {} : {}", file, String.valueOf(invalid.getMessage()));
            return Map.of();
        }
    }

    /** Collect a {@code placeholders} node's children into a {@code name -> template} map, in declared order. */
    private static Map<String, String> readPlaceholderDefinitions(ConfigurationNode node) {
        Map<String, String> definitions = new LinkedHashMap<>();
        node.childrenMap().forEach((key, child) -> definitions.put(String.valueOf(key), child.getString("")));
        return definitions;
    }

    /** Load one file; records its id in {@code loaded} when it registered, or in {@code skipped} on a parse/ref error. */
    private void loadOne(
            Path file,
            ConfigurationNode globalPatterns,
            List<String> loaded,
            List<String> skipped,
            Map<String, OpenCommandSpec> openCommands) {
        String id = stripConf(file.getFileName().toString());
        MenuSpec spec;
        try {
            spec = specLoader.load(file, globalPatterns);
        } catch (MenuSpecException invalid) {
            log.warn("skipped menu {} : {}", id, String.valueOf(invalid.getMessage()));
            skipped.add(id);
            return;
        }
        List<String> missing = bindings.validate(List.of(spec));
        if (!missing.isEmpty()) {
            log.warn("skipped menu {} : references unknown ids {}", id, missing);
            skipped.add(id);
            return;
        }
        menus.registerSpec(id, spec);
        loaded.add(id);
        parseOpenCommand(file, id).ifPresent(command -> openCommands.put(id, command));
    }

    /**
     * Read the optional top-level {@code command {}} block of a menu file into an {@link OpenCommandSpec}. A file
     * with no such block contributes no open command. A malformed block (a name with spaces, an unreadable file)
     * is logged and skipped without touching the already-registered menu, which still opens through
     * {@code /menu open <id>}; one bad command block never hides its menu. The file is read a second time here rather
     * than threading the node out of {@link MenuSpecLoader}: this runs once on enable / reload, never on a hot path.
     */
    private Optional<OpenCommandSpec> parseOpenCommand(Path file, String menuId) {
        try {
            ConfigurationNode command =
                    HoconConfigurationLoader.builder().path(file).build().load().node("command");
            if (command.virtual() || command.isNull()) {
                return Optional.empty();
            }
            return Optional.of(new OpenCommandSpec(
                    command.node("name").getString(menuId),
                    stringList(command.node("aliases")),
                    optionalString(command.node("permission")),
                    optionalString(command.node("deny-message")),
                    command.node("console").getBoolean(false),
                    parseArguments(command.node("arguments"), menuId),
                    optionalString(command.node("usage"))));
        } catch (RuntimeException | java.io.IOException invalid) {
            log.warn("menu {} has an invalid command block : {}", menuId, String.valueOf(invalid.getMessage()));
            return Optional.empty();
        }
    }

    /**
     * Parse the ordered {@code arguments = [{ name=..., type=... }, ...]} list of a command block. Each entry needs
     * a non-blank {@code name}; an entry without one is logged and skipped rather than aborting the command. An
     * unrecognised {@code type} degrades to a plain {@link ArgType#STRING} word argument with a warning, so a config
     * typo never drops the command: it just loses the stricter parsing and autocomplete for that one argument.
     */
    private List<ArgumentSpec> parseArguments(ConfigurationNode node, String menuId) {
        if (node.virtual() || node.isNull()) {
            return List.of();
        }
        List<ArgumentSpec> arguments = new ArrayList<>();
        for (ConfigurationNode entry : node.childrenList()) {
            String name = entry.node("name").getString("");
            if (name.isBlank()) {
                log.warn("menu {} declares a command argument with no name; skipping it", menuId);
                continue;
            }
            boolean greedy = entry.node("greedy").getBoolean(false);
            arguments.add(
                    new ArgumentSpec(name, argType(entry.node("type").getString("string"), menuId, name), greedy));
        }
        warnOnNonTerminalGreedy(arguments, menuId);
        return List.copyOf(arguments);
    }

    /**
     * Warn when a {@code greedy} flag sits on any argument other than the last. Brigadier can only capture the
     * remainder of the input on a terminal node, so a greedy flag before a further argument is silently ignored at
     * build time, surfacing it here lets an operator reorder the arguments rather than wonder why only one word was
     * read. The spec itself is left as declared; the command builder is what honours greedy only on the final node.
     */
    private void warnOnNonTerminalGreedy(List<ArgumentSpec> arguments, String menuId) {
        for (int i = 0; i < arguments.size() - 1; i++) {
            if (arguments.get(i).greedy()) {
                log.warn(
                        "menu {} command argument {} is greedy but not the last argument; greedy is only honoured "
                                + "on the final argument",
                        menuId,
                        arguments.get(i).name());
            }
        }
    }

    /** The {@link ArgType} named by {@code token}, defaulting to {@link ArgType#STRING} with a warning when unknown. */
    private ArgType argType(String token, String menuId, String argument) {
        return ArgType.parse(token).orElseGet(() -> {
            log.warn(
                    "menu {} command argument {} has unknown type '{}'; treating it as a string",
                    menuId,
                    argument,
                    token);
            return ArgType.STRING;
        });
    }

    /** The string-list value at {@code node}, or an empty list when the node is absent or not a string list. */
    private static List<String> stringList(ConfigurationNode node) {
        if (node.virtual() || node.isNull()) {
            return List.of();
        }
        try {
            @Nullable List<String> values = node.getList(String.class);
            return values == null ? List.of() : List.copyOf(values);
        } catch (org.spongepowered.configurate.serialize.SerializationException malformed) {
            return List.of();
        }
    }

    /** The verbatim non-blank string at {@code node} wrapped in an Optional, or empty when absent or blank. */
    private static Optional<String> optionalString(ConfigurationNode node) {
        if (node.virtual() || node.isNull()) {
            return Optional.empty();
        }
        String value = node.getString("");
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static String stripConf(String fileName) {
        return fileName.substring(0, fileName.length() - ".conf".length());
    }
}
