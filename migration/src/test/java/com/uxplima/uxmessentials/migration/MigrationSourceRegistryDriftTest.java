package com.uxplima.uxmessentials.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.migration.convert.Convert;
import com.uxplima.uxmessentials.migration.convert.SourceId;
import com.uxplima.uxmessentials.migration.convert.SourceRegistry;
import com.uxplima.uxmessentials.migration.convert.athelion.AthelionPlayerWarpsConvert;
import com.uxplima.uxmessentials.migration.convert.ax.AxPlayerWarpsConfig;
import com.uxplima.uxmessentials.migration.convert.ax.AxPlayerWarpsConvert;
import com.uxplima.uxmessentials.migration.convert.decentholograms.DecentHologramsConvert;
import com.uxplima.uxmessentials.migration.convert.essentialsx.EssentialsXConvert;
import com.uxplima.uxmessentials.migration.convert.fancyholograms.FancyHologramsConvert;
import com.uxplima.uxmessentials.migration.convert.litebans.LiteBansConfig;
import com.uxplima.uxmessentials.migration.convert.litebans.LiteBansConvert;
import com.uxplima.uxmessentials.migration.convert.live.BalanceFeed;
import com.uxplima.uxmessentials.migration.convert.live.LiveBalanceConvert;
import com.uxplima.uxmessentials.migration.convert.map.ImportedUser;
import com.uxplima.uxmessentials.migration.convert.multiverse.MultiverseConvert;
import com.uxplima.uxmessentials.migration.convert.olzie.OlziePlayerWarpsConfig;
import com.uxplima.uxmessentials.migration.convert.olzie.OlziePlayerWarpsConvert;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;

/**
 * The source-registry drift guard (docs/12-migration §8.3). It keeps the multi-source machinery honest:
 * the built sources are EssentialsX, the live economy sources Vault and PlayerPoints, LiteBans, the two
 * hologram sources DecentHolograms and FancyHolograms, the three player-warp sources AxPlayerWarps, Athelion
 * PlayerWarps and Olzie PlayerWarps, and the world source Multiverse-Core; the remaining planned sources have a reserved id but
 * no registry entry (planned ≠ stubbed, §1.2). The registry keys, the per-source mapping tables, and the
 * built-source roster stay in lock-step. A {@code Convert} impl with no mapping table, or a planned id that
 * tab-completes, fails here.
 */
class MigrationSourceRegistryDriftTest {

    /**
     * The reserved ids of the planned sources. Documented in §1.2, deliberately not built. {@code libertybans}
     * is reserved here as the next moderation JDBC source after LiteBans (deferred): planned, never stubbed,
     * so it must not resolve and must contribute no rows until it is built.
     */
    private static final List<String> PLANNED =
            List.of("cmi", "huskhomes", "playervaultx", "coinsengine", "sunlight", "axvault", "libertybans");

    private final SourceRegistry registry = new SourceRegistry(List.of(
            new EssentialsXConvert(name -> Optional.empty()),
            new LiveBalanceConvert(SourceId.of("vault"), "Vault", "the live Vault economy provider", emptyFeed()),
            new LiveBalanceConvert(
                    SourceId.of("playerpoints"), "PlayerPoints", "the live PlayerPoints economy provider", emptyFeed()),
            new LiteBansConvert(
                    new LiteBansConfig(Optional.empty(), "", "", "litebans_", Optional.empty()), noOpLogger()),
            new DecentHologramsConvert(name -> Optional.empty(), Path.of("DecentHolograms", "holograms")),
            new FancyHologramsConvert(name -> Optional.empty(), Path.of("FancyHolograms", "holograms.yml")),
            new AxPlayerWarpsConvert(
                    new AxPlayerWarpsConfig(Optional.empty(), "", "", Optional.empty()),
                    name -> Optional.empty(),
                    noOpLogger()),
            new AthelionPlayerWarpsConvert(name -> Optional.empty(), Path.of("PlayerWarps", "data.yml"), noOpLogger()),
            new OlziePlayerWarpsConvert(
                    new OlziePlayerWarpsConfig(Optional.empty(), "", "", Optional.empty()),
                    name -> Optional.empty(),
                    noOpLogger()),
            new MultiverseConvert(
                    Path.of("Multiverse-Core", "worlds.yml"), java.time.Clock.systemUTC(), noOpLogger())));

    private static Logger noOpLogger() {
        return new Logger() {
            @Override
            public void info(String message, Object... args) {}

            @Override
            public void warn(String message, Object... args) {}

            @Override
            public void error(String message, Throwable cause) {}

            @Override
            public void debug(String message, Object... args) {}
        };
    }

    private static BalanceFeed emptyFeed() {
        return new BalanceFeed() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public Stream<ImportedUser> users() {
                return Stream.empty();
            }
        };
    }

    @Test
    void theBuiltSourcesAreEssentialsxVaultPlayerpointsLitebansTheHologramPlayerWarpAndWorldSourcesAndResolve() {
        assertThat(registry.builtIds())
                .containsExactlyInAnyOrder(
                        SourceId.of("essentialsx"),
                        SourceId.of("vault"),
                        SourceId.of("playerpoints"),
                        SourceId.of("litebans"),
                        SourceId.of("decentholograms"),
                        SourceId.of("fancyholograms"),
                        SourceId.of("axplayerwarps"),
                        SourceId.of("athelionplayerwarps"),
                        SourceId.of("olzieplayerwarps"),
                        SourceId.of("multiverse"));
        assertThat(registry.resolve(SourceId.of("essentialsx")))
                .map(Convert::id)
                .contains(SourceId.of("essentialsx"));
        assertThat(registry.resolve(SourceId.of("litebans"))).map(Convert::id).contains(SourceId.of("litebans"));
        assertThat(registry.resolve(SourceId.of("decentholograms")))
                .map(Convert::id)
                .contains(SourceId.of("decentholograms"));
        assertThat(registry.resolve(SourceId.of("fancyholograms")))
                .map(Convert::id)
                .contains(SourceId.of("fancyholograms"));
        assertThat(registry.resolve(SourceId.of("axplayerwarps")))
                .map(Convert::id)
                .contains(SourceId.of("axplayerwarps"));
        assertThat(registry.resolve(SourceId.of("athelionplayerwarps")))
                .map(Convert::id)
                .contains(SourceId.of("athelionplayerwarps"));
        assertThat(registry.resolve(SourceId.of("olzieplayerwarps")))
                .map(Convert::id)
                .contains(SourceId.of("olzieplayerwarps"));
        assertThat(registry.resolve(SourceId.of("multiverse"))).map(Convert::id).contains(SourceId.of("multiverse"));
    }

    @Test
    void everyBuiltSourceHasAMappingTable() {
        for (SourceId id : registry.builtIds()) {
            assertThat(SupportedMappings.rows(id))
                    .as("built source %s must contribute a SupportedMappings table", id)
                    .isNotEmpty();
        }
        assertThat(SupportedMappings.builtSources()).containsExactlyInAnyOrderElementsOf(registry.builtIds());
    }

    @Test
    void plannedSourcesNeverResolveAndContributeNoRows() {
        for (String planned : PLANNED) {
            SourceId id = SourceId.of(planned);
            assertThat(registry.resolve(id))
                    .as("planned source %s must not resolve (planned != stubbed)", planned)
                    .isEmpty();
            assertThat(SupportedMappings.rows(id))
                    .as("planned source %s must contribute no mapping rows", planned)
                    .isEmpty();
            assertThat(registry.builtIds())
                    .as("planned source %s must not tab-complete", planned)
                    .doesNotContain(id);
        }
    }

    @Test
    void anUnknownSourceResolvesToEmptyNotASilentNoOp() {
        assertThat(registry.resolve(SourceId.of("nonsense"))).isEmpty();
    }

    @Test
    void registryRejectsDuplicateSources() {
        EssentialsXConvert one = new EssentialsXConvert(name -> Optional.empty());
        EssentialsXConvert two = new EssentialsXConvert(name -> Optional.empty());
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> new SourceRegistry(List.of(one, two)));
    }
}
