package com.uxplima.uxmessentials.migration.convert.decentholograms.parse;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.YamlSource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Parses one DecentHolograms {@code holograms/<name>.yml} into a {@link DhHologram}. The hologram's name
 * is the file-name stem (DecentHolograms names each file after the hologram). The location is the {@code
 * location} string. {@code world:x:y:z} with three decimals, the form
 * {@code LocationUtils.asString(loc, false)} writes; a comma decimal separator is normalised to a dot
 * before the colon split, exactly as DecentHolograms itself parses it. Lines come from the first page's
 * {@code lines[].content}, falling back to the legacy top-level {@code lines} section older files carry. A
 * file with no resolvable location yields {@code null} and the caller counts it as a skipped record
 * (docs/12-migration §4).
 */
@NullMarked
public final class DhHologramParser {

    /** Parse the hologram file at {@code file}, taking the name from the file-name stem. */
    public @Nullable DhHologram parse(Path file) throws IOException {
        return parse(stem(file.getFileName().toString()), YamlSource.load(file));
    }

    /** Parse from a reader with an explicit hologram name: the form the golden-file tests drive. */
    public @Nullable DhHologram parse(String name, Reader reader) throws IOException {
        return parse(name, YamlSource.load(reader));
    }

    private @Nullable DhHologram parse(String name, ConfigurationNode root) {
        String location = root.node("location").getString();
        if (location == null || location.isBlank()) {
            return null;
        }
        // DecentHolograms normalises a comma decimal separator to a dot, then splits on ':' into
        // world:x:y:z(:yaw:pitch). We keep only world and the three coordinates.
        String[] parts = location.replace(",", ".").split(":", -1);
        if (parts.length < 4) {
            return null;
        }
        Double x = parseDouble(parts[1]);
        Double y = parseDouble(parts[2]);
        Double z = parseDouble(parts[3]);
        if (x == null || y == null || z == null) {
            return null;
        }
        return new DhHologram(name, parts[0], x, y, z, lines(root));
    }

    /** The first page's line contents, or the legacy top-level {@code lines} section for older files. */
    private static List<String> lines(ConfigurationNode root) {
        List<String> contents = new ArrayList<>();
        ConfigurationNode pages = root.node("pages");
        List<? extends ConfigurationNode> pageList = pages.childrenList();
        if (!pageList.isEmpty()) {
            collectLines(pageList.get(0).node("lines"), contents);
            return contents;
        }
        collectLines(root.node("lines"), contents);
        return contents;
    }

    /** Drain a {@code lines} node, a list of {@code {content: …}} maps, a list of strings, or a numbered map. */
    private static void collectLines(ConfigurationNode lines, List<String> out) {
        if (!lines.childrenList().isEmpty()) {
            for (ConfigurationNode line : lines.childrenList()) {
                addContent(line, out);
            }
        } else if (!lines.childrenMap().isEmpty()) {
            for (ConfigurationNode line : lines.childrenMap().values()) {
                addContent(line, out);
            }
        }
    }

    /** Add one line's text: the {@code content} field of a map line, or the scalar of a plain-string line. */
    private static void addContent(ConfigurationNode line, List<String> out) {
        String content = line.isMap() ? line.node("content").getString() : line.getString();
        if (content != null) {
            out.add(content);
        }
    }

    private static @Nullable Double parseDouble(String raw) {
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException notADouble) {
            return null;
        }
    }

    private static String stem(String fileName) {
        return fileName.endsWith(".yml") ? fileName.substring(0, fileName.length() - 4) : fileName;
    }
}
