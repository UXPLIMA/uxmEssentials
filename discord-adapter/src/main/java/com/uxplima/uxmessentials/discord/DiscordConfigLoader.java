package com.uxplima.uxmessentials.discord;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Loads {@code discord.conf} into a typed {@link DiscordConfig}. On first run it extracts the bundled default
 * (with {@code enabled = false}) to {@code config/discord.conf} under the plugin data folder, so an operator
 * has a documented template to edit (docs/09-deployment.md Path C, step 2). Subsequent loads parse the
 * on-disk file; a parse failure yields the dormant default rather than throwing, so a malformed file never
 * crashes plugin enable: the bridge simply stays off and the failure is logged.
 *
 * <p>Configurate types are confined to this class; the rest of the bridge only sees the {@link DiscordConfig}
 * record. The loader takes a plain {@link Path} so it is exercised in tests without a live Bukkit server.
 */
public final class DiscordConfigLoader {

    private static final String FILE_NAME = "discord.conf";
    private static final String RESOURCE = "/discord.conf";
    private static final int DEFAULT_MAX_PER_MINUTE = 60;

    private DiscordConfigLoader() {}

    /** Extract the default if missing, then parse {@code config/discord.conf} under {@code dataFolder}. */
    public static DiscordConfig load(Path dataFolder) throws ConfigurateException {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Path file = dataFolder.resolve("config").resolve(FILE_NAME);
        extractDefaultIfMissing(file);
        HoconConfigurationLoader loader =
                HoconConfigurationLoader.builder().path(file).build();
        return parse(loader.load());
    }

    /** Parse an already-loaded Configurate tree into the typed config. */
    static DiscordConfig parse(ConfigurationNode root) {
        boolean enabled = root.node("enabled").getBoolean(false);
        String token = root.node("token").getString("");
        long minEco = root.node("min-eco-notify").getLong(0L);
        int maxPerMinute = root.node("max-per-minute").getInt(DEFAULT_MAX_PER_MINUTE);
        return new DiscordConfig(
                enabled, token, readChannels(root.node("channels")), Math.max(0L, minEco), Math.max(1, maxPerMinute));
    }

    private static Map<EventCategory, String> readChannels(ConfigurationNode channels) {
        Map<EventCategory, String> mapped = new EnumMap<>(EventCategory.class);
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                channels.childrenMap().entrySet()) {
            String key = String.valueOf(entry.getKey());
            String channelId = entry.getValue().getString("");
            EventCategory.fromConfigKey(key)
                    .filter(category -> !channelId.isBlank())
                    .ifPresent(category -> mapped.put(category, channelId));
        }
        return mapped;
    }

    private static void extractDefaultIfMissing(Path file) {
        if (Files.exists(file)) {
            return;
        }
        try (InputStream resource = DiscordConfigLoader.class.getResourceAsStream(RESOURCE)) {
            Files.createDirectories(Objects.requireNonNull(file.getParent(), "parent"));
            if (resource == null) {
                writeFallbackDefault(file);
                return;
            }
            Files.copy(resource, file);
        } catch (IOException failure) {
            throw new UncheckedExtractException("failed to extract default " + FILE_NAME, failure);
        }
    }

    private static void writeFallbackDefault(Path file) throws IOException {
        CommentedConfigurationNode root = CommentedConfigurationNode.root();
        try {
            root.node("enabled").set(false);
            root.node("token").set("");
            root.node("min-eco-notify").set(0L);
            root.node("max-per-minute").set(DEFAULT_MAX_PER_MINUTE);
            HoconConfigurationLoader.builder().path(file).build().save(root);
        } catch (ConfigurateException failure) {
            throw new IOException("failed to write fallback default", failure);
        }
    }

    /** Wraps an extraction I/O failure as unchecked so enable fails loudly with a clear cause. */
    static final class UncheckedExtractException extends RuntimeException {
        UncheckedExtractException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
