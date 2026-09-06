package com.uxplima.uxmessentials.migration.convert.essentialsx.parse;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Reads EssentialsX's mute and jail state. Two surfaces feed it (docs/12-migration §4): the per-player
 * {@code muted}/{@code jailed} fields inside each {@code userdata/<uuid>.yml} (the importable sanction
 * state), and the server-wide {@code jail.yml} jail <em>definitions</em> (named locations). The userdata
 * read is the one the importer maps to a {@code ModerationProfile}; the definitions read only pins the
 * jail-location shape so an operator can confirm a jailed player's jail exists.
 *
 * <p>Field names match EssentialsX's {@code UserConfigHolder} as serialised by Configurate's default
 * naming scheme ({@code muted}, {@code muteReason}, {@code jailed}, {@code jail}, and the nested
 * {@code timestamps.mute}/{@code timestamps.jail}/{@code timestamps.onlinejail}). The legacy top-level
 * {@code muteTimeout} key (used by older EssentialsX) is read as a fallback so an old userdata file still
 * parses. Defensive throughout: a player with no sanctions parses to {@link EssXModeration#none()}.
 */
@NullMarked
public final class JailMuteParser {

    /** Read the moderation block from an already-loaded userdata node tree. */
    public EssXModeration parseUser(ConfigurationNode root) {
        Optional<EssXMute> mute = root.node("muted").getBoolean(false) ? Optional.of(mute(root)) : Optional.empty();
        Optional<EssXJail> jail = root.node("jailed").getBoolean(false) ? jail(root) : Optional.empty();
        if (mute.isEmpty() && jail.isEmpty()) {
            return EssXModeration.none();
        }
        return new EssXModeration(mute, jail);
    }

    /** Parse the server-wide {@code jail.yml} into named jail definition locations. */
    public Map<String, EssXLocation> parseDefinitions(Reader reader) throws IOException {
        return definitions(YamlSource.load(reader));
    }

    private EssXMute mute(ConfigurationNode root) {
        Optional<String> reason = optionalString(root.node("muteReason").getString());
        return new EssXMute(reason, muteExpiry(root));
    }

    private Optional<EssXJail> jail(ConfigurationNode root) {
        String name = root.node("jail").getString();
        if (name == null || name.isBlank()) {
            // Jailed with no named jail is an unusable record, skipped, never fatal (docs/12-migration §4).
            return Optional.empty();
        }
        ConfigurationNode timestamps = root.node("timestamps");
        return Optional.of(new EssXJail(
                name.strip(),
                positiveMillis(timestamps.node("jail").getLong(0L)),
                positiveMillis(timestamps.node("onlinejail").getLong(0L))));
    }

    private Optional<Long> muteExpiry(ConfigurationNode root) {
        long nested = root.node("timestamps", "mute").getLong(0L);
        long expiry = nested > 0L ? nested : root.node("muteTimeout").getLong(0L);
        return positiveMillis(expiry);
    }

    private Map<String, EssXLocation> definitions(ConfigurationNode root) {
        Map<String, EssXLocation> jails = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                root.node("jails").childrenMap().entrySet()) {
            EssXLocation location = LocationNodes.read(entry.getValue());
            if (location != null) {
                jails.put(String.valueOf(entry.getKey()), location);
            }
        }
        return jails;
    }

    private static Optional<Long> positiveMillis(long value) {
        // EssentialsX writes 0 for "no timer" and may leave a lapsed expiry behind; carry only a positive
        // figure, leaving the permanent-vs-timed decision to the mapper.
        return value > 0L ? Optional.of(value) : Optional.empty();
    }

    private static Optional<String> optionalString(@Nullable String raw) {
        return raw == null || raw.isBlank() ? Optional.empty() : Optional.of(raw.strip());
    }
}
