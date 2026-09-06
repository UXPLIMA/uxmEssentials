package com.uxplima.uxmessentials.migration.convert.litebans.map;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.migration.convert.litebans.parse.LiteBansRow;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Shared ACL helpers for resolving the two actors on a LiteBans row, the target and the issuer, into the
 * moderation domain's identity types. Pure: no JDBC, no Bukkit. The translation rules are LiteBans-specific
 * (the {@code #offline#}/{@code #undefined#} target sentinels and the {@code CONSOLE} issuer literal) and
 * are isolated here so the per-sanction mappers stay focused on shape, not identity parsing.
 */
@NullMarked
final class LiteBansActors {

    private static final String CONSOLE = "CONSOLE";
    private static final String SOURCE_NAME = "LiteBans";

    private LiteBansActors() {}

    /**
     * Resolve the row's target as a {@link PlayerRef}, or empty when the UUID is a LiteBans sentinel
     * ({@code #offline#}/{@code #undefined#}) or simply unparseable. Such a row names no real account, so
     * the importer skips it rather than inventing a target.
     */
    static Optional<PlayerRef> target(LiteBansRow row) {
        // LiteBans stores no display name on a punishment row, so the target carries the source name; identity
        // is the UUID alone (PlayerRef equality), so the placeholder name never affects a write.
        return parseUuid(row.uuid()).map(uuid -> new PlayerRef(uuid, SOURCE_NAME));
    }

    /**
     * Resolve the row's issuer: the literal {@code CONSOLE} (or an unparseable issuer UUID) becomes the
     * console actor named for the source; a real UUID becomes a stored player issuer carrying the recorded
     * name. The name always renders even after a rename, since LiteBans stores it on the row.
     */
    static Issuer issuer(LiteBansRow row) {
        String name = row.bannedByName().filter(n -> !n.isBlank()).orElse(SOURCE_NAME);
        if (CONSOLE.equalsIgnoreCase(row.bannedByUuid().strip())) {
            return Issuer.console(name);
        }
        Optional<UUID> uuid = parseUuid(row.bannedByUuid());
        return uuid.<Issuer>map(id -> Issuer.stored(Optional.of(id), name)).orElseGet(() -> Issuer.console(name));
    }

    /** Parse a UUID, rejecting the LiteBans target sentinels and any malformed value. */
    private static Optional<UUID> parseUuid(String raw) {
        String value = raw.strip();
        if (value.isEmpty() || value.startsWith("#")) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
