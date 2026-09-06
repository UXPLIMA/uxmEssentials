package com.uxplima.uxmessentials.migration.convert.athelion.parse;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.YamlSource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Parses Athelion's single {@code plugins/PlayerWarps/data.yml} into one {@link AthelionWarp} per entry under its
 * {@code warps:} map (docs/12-migration §5). Each entry is keyed by the warp uuid and stores the Bukkit-serialised warp
 * fields, {@code owner-id}, {@code name}, {@code loc}, {@code lore}, {@code password}, {@code status}, {@code ratings},
 * {@code reviewers}, {@code blocked-players}, and the rest. The Configurate codec is confined to the shared
 * {@link YamlSource} seam, so the foreign YAML never escapes {@code parse/} (docs/12-migration §2).
 *
 * <p>An entry that is structurally unusable (no owner uuid, no name, or no world in its {@code loc} block) is dropped
 * from the returned list rather than aborting the file, exactly as the EssentialsX warp parser skips a world-less file
 * (docs/12-migration §4). A warp whose world the live server does not know still parses here and is dropped later by the
 * mapper; this parser drops only what cannot be shaped at all.
 */
@NullMarked
public final class AthelionWarpParser {

    /** Parse the {@code data.yml} at {@code file}. */
    public List<AthelionWarp> parse(Path file) throws IOException {
        return parse(YamlSource.load(file));
    }

    /** Parse from a reader: the form the golden-file tests drive. */
    public List<AthelionWarp> parse(Reader reader) throws IOException {
        return parse(YamlSource.load(reader));
    }

    private List<AthelionWarp> parse(ConfigurationNode root) throws IOException {
        List<AthelionWarp> parsed = new ArrayList<>();
        for (ConfigurationNode entry : root.node("warps").childrenMap().values()) {
            AthelionWarp warp = read(entry);
            if (warp != null) {
                parsed.add(warp);
            }
        }
        return parsed;
    }

    private static @Nullable AthelionWarp read(ConfigurationNode entry) throws IOException {
        UUID owner = uuidOrNull(entry.node("owner-id").getString());
        String name = entry.node("name").getString();
        AthelionLocation location = location(entry.node("loc"));
        if (owner == null || name == null || name.isBlank() || location == null) {
            // Missing the owner, the name, or a world is unusable: Athelion could not teleport to it either.
            return null;
        }
        return new AthelionWarp(
                owner,
                name.strip(),
                entry.node("display-name").getString(name.strip()),
                entry.node("lore").getString(),
                location,
                entry.node("password").getString(),
                entry.node("status").getString(),
                entry.node("admission").getInt(0),
                entry.node("visits").getInt(0),
                entry.node("ratings").getInt(0),
                uuidList(entry.node("reviewers")),
                uuidList(entry.node("blocked-players")),
                entry.node("category").getString(),
                entry.node("date-created").getLong(0L));
    }

    private static @Nullable AthelionLocation location(ConfigurationNode loc) {
        String world = loc.node("world").getString();
        if (world == null || world.isBlank()) {
            return null;
        }
        return new AthelionLocation(
                world,
                loc.node("x").getDouble(),
                loc.node("y").getDouble(),
                loc.node("z").getDouble(),
                (float) loc.node("yaw").getDouble(),
                (float) loc.node("pitch").getDouble());
    }

    private static List<UUID> uuidList(ConfigurationNode node) throws IOException {
        List<String> raw = node.getList(String.class);
        if (raw == null) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>();
        for (String value : raw) {
            UUID id = uuidOrNull(value);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static @Nullable UUID uuidOrNull(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.strip());
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
