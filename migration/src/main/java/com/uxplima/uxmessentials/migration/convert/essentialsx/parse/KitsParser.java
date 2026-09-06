package com.uxplima.uxmessentials.migration.convert.essentialsx.parse;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Parses an EssentialsX {@code kits.yml} into a list of {@link EssXKit}. The file holds a {@code kits:}
 * map of {@code <name> -> { delay, items: [...] }}; each entry's item lines are kept raw (the writer
 * turns a descriptor into a stack). EssentialsX also supports a {@code kits/<name>.yml} split layout on
 * newer versions, {@link #parseSingle} handles one such file, so both layouts feed the same list
 * (docs/12-migration §4).
 */
@NullMarked
public final class KitsParser {

    /** Parse a combined {@code kits.yml} with a top-level {@code kits:} map. */
    public List<EssXKit> parse(java.nio.file.Path file) throws IOException {
        return parse(YamlSource.load(file));
    }

    /** Parse a combined {@code kits.yml} from a reader: the form the golden-file tests drive. */
    public List<EssXKit> parse(Reader reader) throws IOException {
        return parse(YamlSource.load(reader));
    }

    private List<EssXKit> parse(ConfigurationNode root) {
        List<EssXKit> kits = new ArrayList<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                root.node("kits").childrenMap().entrySet()) {
            kits.add(readKit(String.valueOf(entry.getKey()), entry.getValue()));
        }
        return kits;
    }

    /** Parse a single split-layout {@code kits/<name>.yml} file whose root is the kit body. */
    public EssXKit parseSingle(String name, Reader reader) throws IOException {
        return readKit(name, YamlSource.load(reader));
    }

    private EssXKit readKit(String name, ConfigurationNode body) {
        long delay = body.node("delay").getLong(0L);
        List<String> items = new ArrayList<>();
        for (ConfigurationNode item : body.node("items").childrenList()) {
            String line = item.getString();
            if (line != null && !line.isBlank()) {
                items.add(line.strip());
            }
        }
        return new EssXKit(name, delay, items);
    }
}
