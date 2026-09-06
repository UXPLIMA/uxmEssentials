package com.uxplima.uxmessentials.migration.convert.litebans;

import java.util.List;

import com.uxplima.uxmessentials.migration.MappingRow;
import org.jspecify.annotations.NullMarked;

/**
 * The LiteBans source's {@code SupportedMappings} rows. Its claim of what it migrates (docs/12-migration
 * §5.4). Each row ties a LiteBans table to the moderation aggregate it seeds, the mapper that performs the
 * translation, and the identity the conflict policy keys on. {@code MigrationSourceRegistryDriftTest} holds
 * this table in lock-step with the registered source: a built source with no rows, or rows for an
 * unregistered source, fails {@code ./gradlew check}.
 */
@NullMarked
public final class LiteBansMappings {

    private static final List<MappingRow> ROWS = List.of(
            new MappingRow("litebans_bans (ipban=0)", "TempbanState", "moderation", "LiteBansBanMapper", "uuid"),
            new MappingRow("litebans_bans (ipban=1)", "IpBan", "moderation", "LiteBansBanMapper", "ip"),
            new MappingRow("litebans_mutes", "ModerationProfile", "moderation", "LiteBansMuteMapper", "uuid"),
            new MappingRow("litebans_warnings", "Warn", "moderation", "LiteBansWarnMapper", "(uuid, issuedAt)"));

    private LiteBansMappings() {}

    /** The LiteBans mapping rows, in doc order. */
    public static List<MappingRow> rows() {
        return ROWS;
    }
}
