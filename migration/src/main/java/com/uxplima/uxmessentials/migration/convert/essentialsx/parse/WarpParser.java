package com.uxplima.uxmessentials.migration.convert.essentialsx.parse;

import java.io.IOException;
import java.io.Reader;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Parses one EssentialsX {@code warps/<name>.yml} into an {@link EssXWarp}. The warp name is taken from
 * the file name stem (EssentialsX names each warp's file after the warp), and the location is the
 * top-level coordinate block. A file with no resolvable world yields {@code null} and the caller counts
 * it as a skipped record (docs/12-migration §4).
 */
@NullMarked
public final class WarpParser {

    /** Parse the warp file at {@code file}, taking the name from the file name stem. */
    public @Nullable EssXWarp parse(java.nio.file.Path file) throws IOException {
        String name = stem(file.getFileName().toString());
        return parse(name, YamlSource.load(file));
    }

    /** Parse from a reader with an explicit warp name: the form the golden-file tests drive. */
    public @Nullable EssXWarp parse(String name, Reader reader) throws IOException {
        return parse(name, YamlSource.load(reader));
    }

    private @Nullable EssXWarp parse(String name, ConfigurationNode root) {
        EssXLocation location = LocationNodes.read(root);
        return location == null ? null : new EssXWarp(name, location);
    }

    private static String stem(String fileName) {
        return fileName.endsWith(".yml") ? fileName.substring(0, fileName.length() - 4) : fileName;
    }
}
