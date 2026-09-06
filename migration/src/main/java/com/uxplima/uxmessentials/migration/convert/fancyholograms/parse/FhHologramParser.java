package com.uxplima.uxmessentials.migration.convert.fancyholograms.parse;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.migration.convert.decentholograms.parse.DhHologram;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.YamlSource;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Parses FancyHolograms' single {@code plugins/FancyHolograms/holograms.yml} into one {@link DhHologram}
 * per entry under its {@code holograms} section (docs/12-migration §5.6). Each entry is keyed by the
 * hologram name and stores {@code type}, a nested {@code location.{world,x,y,z}}, and, for {@code TEXT}
 * holograms: a {@code text} string list. FancyHolograms stamps the file {@code version: 2}; an older
 * layout is refused wholesale, exactly as FancyHolograms itself skips loading it. Item and block holograms
 * carry no {@code text}, so they yield no lines and the shared {@code HologramMapper} counts them skipped
 * (docs/12-migration §4). The parsed shape is the DecentHolograms one. Both sources reduce to a name, a
 * world, a position, and text lines, so they share the mapped {@link DhHologram} and its ACL mapper, the
 * same way both file sources share the world resolver.
 */
@NullMarked
public final class FhHologramParser {

    private static final int SUPPORTED_VERSION = 2;

    /** Parse the {@code holograms.yml} at {@code file}. */
    public List<DhHologram> parse(Path file) throws IOException {
        return parse(YamlSource.load(file));
    }

    /** Parse from a reader: the form the golden-file tests drive. */
    public List<DhHologram> parse(Reader reader) throws IOException {
        return parse(YamlSource.load(reader));
    }

    private List<DhHologram> parse(ConfigurationNode root) throws SerializationException {
        if (root.node("version").getInt(1) != SUPPORTED_VERSION) {
            // FancyHolograms refuses to load a pre-v2 layout; the importer mirrors that rather than guess.
            return List.of();
        }
        List<DhHologram> parsed = new ArrayList<>();
        for (var entry : root.node("holograms").childrenMap().entrySet()) {
            parsed.add(read(String.valueOf(entry.getKey()), entry.getValue()));
        }
        return parsed;
    }

    private static DhHologram read(String name, ConfigurationNode section) throws SerializationException {
        ConfigurationNode location = section.node("location");
        String world = location.node("world").getString("world");
        double x = location.node("x").getDouble();
        double y = location.node("y").getDouble();
        double z = location.node("z").getDouble();
        return new DhHologram(name, world, x, y, z, textLines(section));
    }

    private static List<String> textLines(ConfigurationNode section) throws SerializationException {
        List<String> text = section.node("text").getList(String.class);
        return text == null ? List.of() : text;
    }
}
