package com.uxplima.uxmessentials.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.migration.convert.SourceId;
import com.uxplima.uxmessentials.migration.convert.essentialsx.EssentialsXMappings;
import com.uxplima.uxmessentials.migration.convert.litebans.LiteBansMappings;
import org.junit.jupiter.api.Test;

/**
 * The supported-mapping drift guard (docs/12-migration §8.1). It holds the EssentialsX mapping table
 * honest against the doc's §5.1 rows and the checked-in golden-file fixtures: every claimed mapping has a
 * documented target and a fixture proving it. A mapper added in code with no row here (or a row with no
 * fixture) fails the build, so the three artefacts move together.
 */
class MigrationMappingDriftTest {

    @Test
    void essentialsxRowsMatchTheDocumentedTargets() {
        Set<String> targets =
                EssentialsXMappings.rows().stream().map(MappingRow::target).collect(Collectors.toSet());
        // The §5.1 documented targets the EssentialsX source claims (the deliberate gaps, vaults,
        // nickname, chat: are documented as not-migrated and carry no row). ModerationProfile is the single
        // target of both the jail and the mute rows.
        assertThat(targets)
                .containsExactlyInAnyOrder("Home", "Wallet", "Mailbox", "Warp", "KitDefinition", "ModerationProfile");
    }

    @Test
    void everyEssentialsxRowNamesAConflictUnitAndAMapper() {
        for (MappingRow row : EssentialsXMappings.rows()) {
            assertThat(row.mapper()).isNotBlank();
            assertThat(row.conflictUnit()).isNotBlank();
            assertThat(row.context()).isNotBlank();
        }
    }

    @Test
    void essentialsxIsRegisteredAsABuiltSourceWithRows() {
        assertThat(SupportedMappings.builtSources()).contains(SourceId.of("essentialsx"));
        assertThat(SupportedMappings.rows(SourceId.of("essentialsx"))).isEqualTo(EssentialsXMappings.rows());
    }

    @Test
    void homeWarpAndKitMappingsHaveBackingGoldenFiles() {
        // The three-way equality's fixture leg: the targets that are parsed in the golden-file suite must
        // have a checked-in fixture. (Wallet/Mailbox ride along on the userdata fixture.)
        assertThat(fixtureExists("essentialsx/userdata/00000000-0000-0000-0000-000000000001.yml"))
                .as("userdata fixture backs the Home/Wallet/Mailbox rows")
                .isTrue();
        assertThat(fixtureExists("essentialsx/warps/spawn.yml"))
                .as("warp fixture backs the Warp row")
                .isTrue();
        assertThat(fixtureExists("essentialsx/kits.yml"))
                .as("kits fixture backs the KitDefinition row")
                .isTrue();
        // The moderation rows: the muted player rides on the alex userdata fixture, the jailed player on a
        // dedicated userdata fixture, and the server-wide jail definitions on jail.yml.
        assertThat(fixtureExists("essentialsx/userdata/00000000-0000-0000-0000-000000000002.yml"))
                .as("userdata fixture backs the muted ModerationProfile row")
                .isTrue();
        assertThat(fixtureExists("essentialsx/userdata/00000000-0000-0000-0000-000000000003.yml"))
                .as("userdata fixture backs the jailed ModerationProfile row")
                .isTrue();
        assertThat(fixtureExists("essentialsx/jail.yml"))
                .as("jail definitions fixture backs the jail ModerationProfile row")
                .isTrue();
    }

    @Test
    void liveBalanceSourcesEachMapBalanceToWallet() {
        for (SourceId source : List.of(SourceId.of("vault"), SourceId.of("playerpoints"))) {
            List<MappingRow> rows = SupportedMappings.rows(source);
            assertThat(rows)
                    .as("live source %s migrates a single balance row", source)
                    .hasSize(1);
            MappingRow row = rows.get(0);
            assertThat(row.target()).isEqualTo("Wallet");
            assertThat(row.mapper()).isNotBlank();
            assertThat(row.conflictUnit()).isNotBlank();
            assertThat(row.context()).isNotBlank();
        }
    }

    @Test
    void liteBansIsRegisteredAndMapsTheModerationSurfaces() {
        assertThat(SupportedMappings.builtSources()).contains(SourceId.of("litebans"));
        assertThat(SupportedMappings.rows(SourceId.of("litebans"))).isEqualTo(LiteBansMappings.rows());
        Set<String> targets =
                LiteBansMappings.rows().stream().map(MappingRow::target).collect(Collectors.toSet());
        // The LiteBans source seeds only the moderation context's sanction aggregates (docs/12-migration §5.4).
        assertThat(targets).containsExactlyInAnyOrder("TempbanState", "IpBan", "ModerationProfile", "Warn");
        for (MappingRow row : LiteBansMappings.rows()) {
            assertThat(row.context()).isEqualTo("moderation");
            assertThat(row.mapper()).isNotBlank();
            assertThat(row.conflictUnit()).isNotBlank();
        }
    }

    @Test
    void deliberatelyNotMigratedTargetsCarryNoRow() {
        List<String> targets =
                EssentialsXMappings.rows().stream().map(MappingRow::target).toList();
        // EssentialsX ships no player vaults and nickname/chat are dropped (docs/12-migration §5.1 gaps).
        assertThat(targets).doesNotContain("Vault", "Nickname", "ChatFormat");
    }

    private static boolean fixtureExists(String resource) {
        return MigrationMappingDriftTest.class.getClassLoader().getResource(resource) != null;
    }
}
