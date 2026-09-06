package com.uxplima.uxmessentials.migration.convert.multiverse.parse;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.YamlSource;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Parses Multiverse-Core's {@code plugins/Multiverse-Core/worlds.yml} into one {@link MultiverseWorld} per entry
 * (docs/12-migration §5). The Configurate codec is confined to the shared {@link YamlSource} seam, so the foreign
 * YAML never escapes {@code parse/} (docs/12-migration §2).
 *
 * <p><b>Two layouts, one parser.</b> Multiverse 4 nests every world under a {@code worlds:} map and names its keys
 * in camelCase ({@code autoLoad}, {@code playerLimit}, {@code spawnLocation}); Multiverse 5 lifts the worlds to the
 * top level, renames the keys to kebab-case, and moves the immutable ones under {@code read-only:}. Both are in the
 * field on live servers, and an operator should not have to know which one they have, so every field is read from
 * the version-5 key first and the version-4 key second. Multiverse 5 also escapes a dot in a world name as
 * {@code [dot]} in the config key, which is undone here.
 *
 * <p>An entry that carries no recognisable world field at all is not a world section (the top-level {@code version}
 * scalar is the usual case) and is skipped; a structurally broken entry is skipped rather than aborting the file,
 * exactly as the other on-disk sources do (docs/12-migration §4).
 */
@NullMarked
public final class MultiverseWorldParser {

    /** The Multiverse 5 escape for a dot in a world name, which would otherwise open a config sub-path. */
    private static final String DOT_ESCAPE = "[dot]";

    /** Multiverse 5's "this world has no stored seed" sentinel. */
    private static final long NO_SEED = Long.MIN_VALUE;

    /** Multiverse 4's "the entry fee is money, not an item" currency id. */
    private static final int MONEY_CURRENCY = -1;

    /** The keys that identify a node as a world section rather than config bookkeeping. */
    private static final List<String> WORLD_MARKERS =
            List.of("alias", "environment", "read-only", "auto-load", "autoLoad", "difficulty", "pvp", "generator");

    /** Parse the {@code worlds.yml} at {@code file}. */
    public List<MultiverseWorld> parse(Path file) throws IOException {
        return parse(YamlSource.load(file));
    }

    /** Parse from a reader: the form the golden-file tests drive. */
    public List<MultiverseWorld> parse(Reader reader) throws IOException {
        return parse(YamlSource.load(reader));
    }

    private List<MultiverseWorld> parse(ConfigurationNode root) {
        List<MultiverseWorld> parsed = new ArrayList<>();
        for (var entry : entries(root).childrenMap().entrySet()) {
            ConfigurationNode section = entry.getValue();
            if (!isWorldSection(section)) {
                continue;
            }
            parsed.add(read(String.valueOf(entry.getKey()), section));
        }
        return List.copyOf(parsed);
    }

    /** The node the world entries hang off: Multiverse 4's {@code worlds:} map, or the version-5 root itself. */
    private static ConfigurationNode entries(ConfigurationNode root) {
        ConfigurationNode nested = root.node("worlds");
        return nested.isMap() ? nested : root;
    }

    private static boolean isWorldSection(ConfigurationNode section) {
        return section.isMap() && WORLD_MARKERS.stream().anyMatch(marker -> present(section.node(marker)));
    }

    private static MultiverseWorld read(String key, ConfigurationNode entry) {
        return new MultiverseWorld(
                key.replace(DOT_ESCAPE, "."),
                string(pick(entry, "alias")),
                upper(string(pick(entry, "read-only", "environment"), pick(entry, "environment"))),
                seed(entry),
                generator(entry),
                upper(string(pick(entry, "difficulty"))),
                bool(pick(entry, "pvp")),
                bool(pick(entry, "auto-load"), pick(entry, "autoLoad")),
                integer(pick(entry, "player-limit"), pick(entry, "playerLimit")),
                upper(string(pick(entry, "gamemode"), pick(entry, "gameMode"))),
                entryFee(entry),
                spawn(pick(entry, "spawn-location"), pick(entry, "spawnLocation")));
    }

    private static Optional<Long> seed(ConfigurationNode entry) {
        ConfigurationNode node = firstPresent(pick(entry, "read-only", "seed"), pick(entry, "seed"));
        if (!present(node)) {
            return Optional.empty();
        }
        long seed = node.getLong(NO_SEED);
        return seed == NO_SEED ? Optional.empty() : Optional.of(seed);
    }

    /** Multiverse 4 writes the literal string {@code null} for "no generator"; both versions write an empty one. */
    private static Optional<String> generator(ConfigurationNode entry) {
        return string(pick(entry, "generator")).filter(value -> !value.equalsIgnoreCase("null"));
    }

    /**
     * The entry fee, but only when Multiverse is charging money. Both versions can charge an item instead (a
     * Material in version 5, a numeric item id in version 4), which our worlds module has no equivalent for, so an
     * item fee reads as no fee rather than silently becoming a money one.
     */
    private static Optional<Double> entryFee(ConfigurationNode entry) {
        ConfigurationNode fee = firstPresent(pick(entry, "entry-fee"), pick(entry, "entryfee"));
        if (!present(fee)) {
            return Optional.empty();
        }
        if (present(fee.node("enabled")) && !fee.node("enabled").getBoolean()) {
            return Optional.empty();
        }
        if (!isMoney(fee.node("currency"))) {
            return Optional.empty();
        }
        double amount = fee.node("amount").getDouble(0);
        return amount > 0 ? Optional.of(amount) : Optional.empty();
    }

    private static boolean isMoney(ConfigurationNode currency) {
        if (!present(currency)) {
            return true;
        }
        String raw = String.valueOf(currency.raw()).strip();
        return raw.isEmpty() || raw.equalsIgnoreCase("null") || raw.equals(String.valueOf(MONEY_CURRENCY));
    }

    private static Optional<MultiverseSpawn> spawn(ConfigurationNode... candidates) {
        ConfigurationNode node = firstPresent(candidates);
        if (!node.isMap() || !present(node.node("x"))) {
            return Optional.empty();
        }
        return Optional.of(new MultiverseSpawn(
                node.node("x").getDouble(0),
                node.node("y").getDouble(0),
                node.node("z").getDouble(0),
                node.node("yaw").getFloat(0),
                node.node("pitch").getFloat(0)));
    }

    private static ConfigurationNode pick(ConfigurationNode entry, Object... path) {
        return entry.node(path);
    }

    private static ConfigurationNode firstPresent(ConfigurationNode... candidates) {
        for (ConfigurationNode candidate : candidates) {
            if (present(candidate)) {
                return candidate;
            }
        }
        return candidates[candidates.length - 1];
    }

    private static boolean present(ConfigurationNode node) {
        return !node.virtual() && node.raw() != null;
    }

    private static Optional<String> string(ConfigurationNode... candidates) {
        ConfigurationNode node = firstPresent(candidates);
        String value = node.getString();
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.strip());
    }

    private static Optional<String> upper(Optional<String> value) {
        return value.map(raw -> raw.toUpperCase(Locale.ROOT));
    }

    private static Optional<Boolean> bool(ConfigurationNode... candidates) {
        ConfigurationNode node = firstPresent(candidates);
        return present(node) ? Optional.of(node.getBoolean()) : Optional.empty();
    }

    private static Optional<Integer> integer(ConfigurationNode... candidates) {
        ConfigurationNode node = firstPresent(candidates);
        return present(node) ? Optional.of(node.getInt()) : Optional.empty();
    }
}
