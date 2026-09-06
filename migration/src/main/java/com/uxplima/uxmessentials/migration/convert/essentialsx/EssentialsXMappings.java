package com.uxplima.uxmessentials.migration.convert.essentialsx;

import java.util.List;

import com.uxplima.uxmessentials.migration.MappingRow;
import org.jspecify.annotations.NullMarked;

/**
 * The EssentialsX source's {@code SupportedMappings} rows. The v1 source's claim of what it migrates
 * (docs/12-migration §5.1). This list is one of the three artefacts {@code MigrationMappingDriftTest}
 * holds in lock-step: code table ⇄ the §5.1 doc rows ⇄ the golden-file fixtures. A mapper added without
 * a row here, or a row here with no code path and fixture, fails {@code ./gradlew check}. The deliberate
 * gaps (nicknames, chat, player vaults) are documented in the doc, not represented here.
 */
@NullMarked
public final class EssentialsXMappings {

    private static final List<MappingRow> ROWS = List.of(
            new MappingRow("homes.<name>", "Home", "homes", "UserdataMapper", "(uuid, homeName)"),
            new MappingRow("money", "Wallet", "economy", "UserdataMapper", "uuid"),
            new MappingRow("mail", "Mailbox", "messaging", "UserdataMapper", "uuid"),
            new MappingRow("warps/<name>", "Warp", "warps", "WarpMapper", "warpName"),
            new MappingRow("kits.yml entry", "KitDefinition", "kits", "KitMapper", "kitName"),
            new MappingRow("jailed + jail", "ModerationProfile", "moderation", "JailMuteMapper", "uuid"),
            new MappingRow("muted + muteTimeout", "ModerationProfile", "moderation", "JailMuteMapper", "uuid"));

    private EssentialsXMappings() {}

    /** The EssentialsX mapping rows, in doc order. */
    public static List<MappingRow> rows() {
        return ROWS;
    }
}
