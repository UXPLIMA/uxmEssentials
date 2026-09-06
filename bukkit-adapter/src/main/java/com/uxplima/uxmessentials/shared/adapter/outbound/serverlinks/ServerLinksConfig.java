package com.uxplima.uxmessentials.shared.adapter.outbound.serverlinks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Reads the {@code server-links} list from the root {@code config.conf}. The block is a list of maps, each with
 * either a built-in {@code type} or a custom {@code label} plus a {@code url}, a shape the dotted-path
 * {@code ConfigStore} cannot express, so this reads the root file directly with Configurate, exactly as the
 * communication context reads its content siblings.
 *
 * <p>Reading is total: an absent file, an absent {@code server-links} key, a non-list value, or any individual
 * malformed entry yields the entries that did parse (an empty list when none did). A malformed entry is dropped
 * with one warning by the applier, never fatal. An empty result means the feature is off. The applier then leaves
 * the live {@link org.bukkit.ServerLinks} untouched.
 */
@NullMarked
public final class ServerLinksConfig {

    private static final String FILE = "config.conf";
    private static final String SERVER_LINKS = "server-links";

    private final Path dataFolder;
    private final Logger log;

    public ServerLinksConfig(Path dataFolder, Logger log) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** The configured raw link entries (type/label/url fields), in declaration order; empty when unconfigured. */
    public List<RawLink> read() {
        ConfigurationNode root = load();
        ConfigurationNode links = root.node(SERVER_LINKS);
        if (links.virtual() || !links.isList()) {
            return List.of();
        }
        List<RawLink> raw = new ArrayList<>();
        for (ConfigurationNode entry : links.childrenList()) {
            raw.add(new RawLink(
                    entry.node("type").getString(),
                    entry.node("label").getString(),
                    entry.node("url").getString()));
        }
        return List.copyOf(raw);
    }

    private ConfigurationNode load() {
        Path file = dataFolder.resolve(FILE);
        if (!Files.exists(file)) {
            return CommentedConfigurationNode.root();
        }
        try {
            return HoconConfigurationLoader.builder().path(file).build().load();
        } catch (ConfigurateException failure) {
            log.error("failed to read server-links from " + file, failure);
            return CommentedConfigurationNode.root();
        }
    }

    /** One raw, un-validated link entry as read from the file. */
    public record RawLink(
            @Nullable String type,
            @Nullable String label,
            @Nullable String url) {}
}
