package com.uxplima.uxmessentials.migration.convert.essentialsx.parse;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Parses one EssentialsX {@code userdata/<uuid>.yml} into an {@link EssXUserdata}. The uuid comes from
 * the file name (EssentialsX keys each player's file by uuid); the node tree supplies the name, homes,
 * balance, and mail. Unknown keys are ignored and missing keys fall back to defaults, so a minor schema
 * variation never aborts a record (docs/12-migration §4). This class only reads value nodes, the YAML
 * codec lives in {@link YamlSource}.
 */
@NullMarked
public final class UserdataParser {

    private final JailMuteParser jailMute = new JailMuteParser();

    /** Parse the file at {@code file}, taking the uuid from the file name stem. */
    public EssXUserdata parse(java.nio.file.Path file) throws IOException {
        UUID uuid = uuidFromFileName(file.getFileName().toString());
        return parse(uuid, YamlSource.load(file));
    }

    /** Parse from a reader with an explicit uuid: the form the fixtures and golden-file tests drive. */
    public EssXUserdata parse(UUID uuid, Reader reader) throws IOException {
        return parse(uuid, YamlSource.load(reader));
    }

    private EssXUserdata parse(UUID uuid, ConfigurationNode root) {
        String name =
                root.node("lastAccountName").getString(root.node("nickname").getString(uuid.toString()));
        return new EssXUserdata(
                uuid,
                name,
                homes(root.node("homes")),
                balance(root),
                mail(root.node("mail")),
                jailMute.parseUser(root));
    }

    private Map<String, EssXLocation> homes(ConfigurationNode homesNode) {
        Map<String, EssXLocation> homes = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                homesNode.childrenMap().entrySet()) {
            String homeName = String.valueOf(entry.getKey());
            EssXLocation location = LocationNodes.read(entry.getValue());
            if (location != null) {
                homes.put(homeName, location);
            }
        }
        return homes;
    }

    private Optional<BigDecimal> balance(ConfigurationNode root) {
        String raw = root.node("money").getString();
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(raw.strip()));
        } catch (NumberFormatException malformed) {
            // A non-numeric money value is treated as "no balance" rather than a failed record; the
            // user still imports their homes and mail (docs/12-migration §4, defensive parsing).
            return Optional.empty();
        }
    }

    private List<String> mail(ConfigurationNode mailNode) {
        List<String> lines = new ArrayList<>();
        for (ConfigurationNode line : mailNode.childrenList()) {
            String text = line.getString();
            if (text != null && !text.isBlank()) {
                lines.add(text.strip());
            }
        }
        return lines;
    }

    private static UUID uuidFromFileName(String fileName) {
        String stem = fileName.endsWith(".yml") ? fileName.substring(0, fileName.length() - 4) : fileName;
        return UUID.fromString(stem);
    }
}
