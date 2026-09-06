package com.uxplima.uxmessentials.shared.adapter.outbound.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The {@link ConfigStore} backed by Configurate HOCON. The on-disk layout is per-module: a root
 * {@code config.conf} holds globals, and each {@code modules/<module>/config.conf} (plus any sibling
 * {@code <x>.conf}) is mounted into one in-memory tree at {@code modules.<module>} (and
 * {@code modules.<module>.<x>}). Callers still navigate dotted paths against the merged tree, so the
 * file split is invisible above this adapter. The tree is held in an {@link AtomicReference} swapped
 * whole on {@link #reload()} (CLAUDE.md atomic-reload rule). A legacy monolith {@code config.conf}
 * (inline {@code modules.*}) still resolves because the root file is the base of the merged tree.
 *
 * <p>Dotted HOCON paths ({@code modules.homes.enabled}) are navigated by splitting on {@code .} and
 * descending through {@link ConfigurationNode#node(Object...)}; an absent or virtual node yields the
 * caller's fallback. The kernel only ever sees the {@link ConfigStore} contract: Configurate types
 * stay behind this adapter.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. The current tree lives in an {@link AtomicReference} swapped
 * whole on reload; reads are lock-free against the snapshot they fetch.
 */
@NullMarked
public final class ConfigurateConfigStore implements ConfigStore {

    private final Path rootFile;
    private final @Nullable Path modulesDir; // null = single-file mode (back-compat / tests)
    private final Logger log;
    private final AtomicReference<ConfigurationNode> tree;

    private ConfigurateConfigStore(Path rootFile, @Nullable Path modulesDir, Logger log, ConfigurationNode tree) {
        this.rootFile = rootFile;
        this.modulesDir = modulesDir;
        this.log = log;
        this.tree = new AtomicReference<>(tree);
    }

    /** Single-file load (legacy / tests): the whole tree is one {@code file}. */
    public static ConfigurateConfigStore load(Path file, Logger log) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(log, "log");
        return new ConfigurateConfigStore(file, null, log, merged(file, null, log, false));
    }

    /** Layout load: root {@code config.conf} plus every {@code modules/<module>/} file under {@code dataFolder}. */
    public static ConfigurateConfigStore loadLayout(Path dataFolder, Logger log) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        Path root = dataFolder.resolve("config.conf");
        Path modules = dataFolder.resolve("modules");
        return new ConfigurateConfigStore(root, modules, log, merged(root, modules, log, false));
    }

    @Override
    public boolean getBoolean(String path, boolean fallback) {
        return at(path).getBoolean(fallback);
    }

    @Override
    public String getString(String path, String fallback) {
        return at(path).getString(fallback);
    }

    @Override
    public int getInt(String path, int fallback) {
        return at(path).getInt(fallback);
    }

    @Override
    public long getLong(String path, long fallback) {
        return at(path).getLong(fallback);
    }

    @Override
    public double getDouble(String path, double fallback) {
        return at(path).getDouble(fallback);
    }

    @Override
    public List<String> getStringList(String path, List<String> fallback) {
        Objects.requireNonNull(fallback, "fallback");
        ConfigurationNode node = at(path);
        if (node.virtual() || !node.isList()) {
            return List.copyOf(fallback);
        }
        List<String> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            String value = child.getString();
            if (value != null) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    @Override
    public void reload() {
        // Build the complete candidate before publishing it. A malformed sibling must never replace the running
        // tree with an empty/partial one: callers keep observing the last-known-good snapshot when this throws.
        tree.set(merged(rootFile, modulesDir, log, true));
    }

    @Override
    public List<String> getKeys(String path) {
        ConfigurationNode node = at(path);
        if (node.virtual() || !node.isMap()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (Object key : node.childrenMap().keySet()) {
            if (key != null) {
                keys.add(key.toString());
            }
        }
        return List.copyOf(keys);
    }

    private ConfigurationNode at(String path) {
        Objects.requireNonNull(path, "path");
        Object[] segments = path.split("\\.");
        return Objects.requireNonNull(tree.get(), "tree").node(segments);
    }

    /** Build the merged tree: the root file, then each module file mounted at its path. */
    private static ConfigurationNode merged(
            Path rootFile, @Nullable Path modulesDir, Logger log, boolean failOnReadError) {
        ConfigurationNode tree = readFile(rootFile, log, failOnReadError);
        if (modulesDir != null && Files.isDirectory(modulesDir)) {
            try (Stream<Path> dirs = Files.list(modulesDir)) {
                dirs.filter(Files::isDirectory).sorted().forEach(dir -> mountModule(tree, dir, log, failOnReadError));
            } catch (IOException failure) {
                log.error("could not list module config dir " + modulesDir, failure);
                if (failOnReadError) {
                    throw new IllegalStateException("could not list module config directory " + modulesDir, failure);
                }
            }
        }
        return tree;
    }

    private static void mountModule(ConfigurationNode tree, Path moduleDir, Logger log, boolean failOnReadError) {
        String module = moduleDir.getFileName().toString();
        try (Stream<Path> files = Files.list(moduleDir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".conf"))
                    .sorted()
                    .forEach(file -> {
                        String name = file.getFileName().toString();
                        ConfigurationNode target = name.equals("config.conf")
                                ? tree.node("modules", module)
                                : tree.node("modules", module, name.substring(0, name.length() - ".conf".length()));
                        mergeInto(target, file, log, failOnReadError);
                    });
        } catch (IOException failure) {
            log.error("could not list module config files in " + moduleDir, failure);
            if (failOnReadError) {
                throw new IllegalStateException("could not list module config files in " + moduleDir, failure);
            }
        }
    }

    private static void mergeInto(ConfigurationNode target, Path file, Logger log, boolean failOnReadError) {
        try {
            target.mergeFrom(
                    HoconConfigurationLoader.builder().path(file).build().load());
        } catch (ConfigurateException failure) {
            log.error("failed to load config " + file, failure);
            if (failOnReadError) {
                throw new IllegalStateException("failed to parse config " + file, failure);
            }
        }
    }

    private static ConfigurationNode readFile(Path file, Logger log, boolean failOnReadError) {
        if (!Files.exists(file)) {
            return CommentedConfigurationNode.root();
        }
        try {
            return HoconConfigurationLoader.builder().path(file).build().load();
        } catch (ConfigurateException failure) {
            log.error("failed to load config " + file + "; keeping defaults", failure);
            if (failOnReadError) {
                throw new IllegalStateException("failed to parse config " + file, failure);
            }
            return CommentedConfigurationNode.root();
        }
    }
}
