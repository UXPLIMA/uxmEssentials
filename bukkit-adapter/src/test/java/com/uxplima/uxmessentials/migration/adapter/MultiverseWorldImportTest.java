package com.uxplima.uxmessentials.migration.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.ImportPlan;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.PlayerWarpRecordWriter;
import com.uxplima.uxmessentials.migration.RecordOutcome;
import com.uxplima.uxmessentials.migration.RecordWriter;
import com.uxplima.uxmessentials.migration.convert.multiverse.MultiverseConvert;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.worlds.WorldRepositories;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldDifficulty;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Multiverse end-to-end golden-file: it seeds a {@code worlds.yml} fixture, drives the real
 * {@link MultiverseConvert} plan into the live and dry-run writers, and asserts the worlds land in our registry over
 * the default embedded SQLite backend. It proves the whole chain. A fully-configured world carries its alias, seed,
 * environment, generator, spawn and settings across; a bare entry takes our defaults instead of Multiverse's; a world
 * named in a shape our registry cannot hold is dropped; a dry run writes nothing; and a second run adds nothing.
 *
 * <p>The fixture is the Multiverse 5 layout (worlds at the top level, kebab-case keys, the immutable creation facts
 * under {@code read-only}); {@link #alsoReadsTheMultiverse4Layout()} drives the version-4 one (worlds nested under
 * {@code worlds:}, camelCase keys) through the same plan, because both are on live servers today.
 */
class MultiverseWorldImportTest {

    private static final String WORLDS_YML = """
            version: 5.0
            world:
              alias: "&aOverworld"
              auto-load: true
              difficulty: HARD
              pvp: false
              player-limit: 40
              gamemode: ADVENTURE
              generator: VoidGen
              entry-fee:
                enabled: true
                amount: 250.0
              spawn-location:
                x: 12.5
                y: 64.0
                z: -8.25
                yaw: 90.0
                pitch: 12.0
              read-only:
                environment: NORMAL
                seed: 8675309
            creative_flat:
              alias: ''
              difficulty: PEACEFUL
              read-only:
                environment: THE_END
            world[dot]nether:
              alias: "Nether"
              read-only:
                environment: NETHER
            """;

    private static final String LEGACY_WORLDS_YML = """
            worlds:
              legacy:
                alias: "Old World"
                autoLoad: false
                environment: NETHER
                seed: 4242
                generator: 'null'
                difficulty: EASY
                pvp: true
                playerLimit: -1
                gameMode: SURVIVAL
                spawnLocation:
                  ==: MVSpawnLocation
                  x: 1.0
                  y: 70.0
                  z: 2.0
                  yaw: 0.0
                  pitch: 0.0
                entryfee:
                  ==: MVEntryFee
                  amount: 5.0
                  currency: 264
            """;

    private Persistence persistence;
    private WorldRepository worlds;
    private RecordWriter live;
    private RecordWriter dryRun;

    @TempDir
    Path pluginsDir;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), NoopLogger.INSTANCE);
        worlds = WorldRepositories.cached(persistence);
        PlayerWarpRecordWriter playerWarps = mock(PlayerWarpRecordWriter.class);
        live = new RepositoryRecordWriter(
                mock(HomeRepository.class),
                mock(WarpRepository.class),
                mock(WalletRepository.class),
                mock(ModerationRepository.class),
                mock(KitRepository.class),
                mock(HologramRepository.class),
                worlds,
                playerWarps,
                mock(Currency.class),
                Clock.systemUTC());
        dryRun = new DryRunRecordWriter(
                mock(WarpRepository.class),
                mock(ModerationRepository.class),
                mock(KitRepository.class),
                mock(HologramRepository.class),
                worlds,
                playerWarps,
                Clock.systemUTC());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void importsTheConfiguredWorldWithItsAliasSpecAndSettings() {
        importAll(live, WORLDS_YML);

        ManagedWorld world = world("world");
        assertThat(world.alias()).contains("&aOverworld");
        assertThat(world.autoLoad()).isTrue();
        assertThat(world.adopted())
                .as("the world folder already existed; we did not generate it")
                .isTrue();
        assertThat(world.knownUid())
                .as("the uid is unknown until the world is first loaded")
                .isEmpty();
        assertThat(world.spec().environment()).isEqualTo(WorldEnvironment.NORMAL);
        assertThat(world.spec().seed()).contains(8675309L);
        assertThat(world.spec().generator().orElseThrow().value()).isEqualTo("VoidGen");

        WorldSettings settings = world.settings();
        assertThat(settings.get(WorldProperties.DIFFICULTY)).isEqualTo(WorldDifficulty.HARD);
        assertThat(settings.get(WorldProperties.PVP)).isFalse();
        assertThat(settings.get(WorldProperties.PLAYER_LIMIT)).isEqualTo(40);
        assertThat(settings.get(WorldProperties.ENTRY_FEE)).isEqualByComparingTo("250.0");
        assertThat(settings.spawn()).contains("12.5;64.0;-8.25;90.0;12.0");
    }

    @Test
    void aBareEntryTakesOurDefaultsRatherThanMultiversesOwn() {
        importAll(live, WORLDS_YML);

        ManagedWorld flat = world("creative_flat");
        assertThat(flat.alias()).as("Multiverse's empty alias is no alias").isEmpty();
        assertThat(flat.spec().environment()).isEqualTo(WorldEnvironment.THE_END);
        assertThat(flat.spec().seed()).isEmpty();
        assertThat(flat.settings().get(WorldProperties.DIFFICULTY)).isEqualTo(WorldDifficulty.PEACEFUL);
        assertThat(flat.settings().raw())
                .as("only the chosen difficulty is stored; everything else stays at our default")
                .containsOnlyKeys(WorldProperties.DIFFICULTY.key());
    }

    @Test
    void dropsTheWorldOurRegistryCannotName() {
        importAll(live, WORLDS_YML);

        // Multiverse escapes the dot in "world.nether"; our world names have no room for one, and neither would
        // /world tp, so the entry is dropped rather than landing under a mangled name.
        assertThat(worlds.all().stream().map(managed -> managed.name().value()))
                .containsExactlyInAnyOrder("world", "creative_flat");
    }

    @Test
    void alsoReadsTheMultiverse4Layout() {
        importAll(live, LEGACY_WORLDS_YML);

        ManagedWorld legacy = world("legacy");
        assertThat(legacy.alias()).contains("Old World");
        assertThat(legacy.autoLoad()).isFalse();
        assertThat(legacy.spec().environment()).isEqualTo(WorldEnvironment.NETHER);
        assertThat(legacy.spec().seed()).contains(4242L);
        assertThat(legacy.spec().generator())
                .as("Multiverse 4 writes the string null for no generator")
                .isEmpty();
        assertThat(legacy.settings().get(WorldProperties.DIFFICULTY)).isEqualTo(WorldDifficulty.EASY);
        assertThat(legacy.settings().rawValue(WorldProperties.PLAYER_LIMIT.key()))
                .as("Multiverse's -1 is its no-limit, which is our default")
                .isEmpty();
        assertThat(legacy.settings().rawValue(WorldProperties.ENTRY_FEE.key()))
                .as("a fee charged in items has no money equivalent, so it is not imported as one")
                .isEmpty();
        assertThat(legacy.settings().spawn()).contains("1.0;70.0;2.0;0.0;0.0");
    }

    @Test
    void aDryRunWritesNothing() {
        List<ImportRecord> records = planRecords(WORLDS_YML);

        records.forEach(record ->
                assertThat(dryRun.write(record, ImportOptions.live(pluginsDir))).isEqualTo(RecordOutcome.WRITTEN));

        assertThat(worlds.all()).as("a dry run writes no rows").isEmpty();
    }

    @Test
    void aSecondRunImportsNothingNew() {
        importAll(live, WORLDS_YML);
        int afterFirst = worlds.all().size();

        importAll(live, WORLDS_YML);

        assertThat(worlds.all()).as("the re-run adds no worlds").hasSize(afterFirst);
    }

    private void importAll(RecordWriter writer, String fixture) {
        planRecords(fixture).forEach(record -> writer.write(record, ImportOptions.live(pluginsDir)));
    }

    private List<ImportRecord> planRecords(String fixture) {
        MultiverseConvert convert =
                new MultiverseConvert(writeWorldsFile(fixture), Clock.systemUTC(), NoopLogger.INSTANCE);
        try (ImportPlan plan = convert.plan(ImportOptions.live(pluginsDir))) {
            return plan.records().toList();
        }
    }

    private Path writeWorldsFile(String fixture) {
        Path worldsFile = pluginsDir.resolve("worlds.yml");
        try {
            Files.writeString(worldsFile, fixture);
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to seed the Multiverse worlds.yml fixture", failure);
        }
        return worldsFile;
    }

    private ManagedWorld world(String name) {
        return worlds.find(WorldName.of(name))
                .orElseThrow(() -> new AssertionError("no world named " + name + " was imported"));
    }

    /** A config that selects the embedded SQLite backend with every default: no network coordinates. */
    private record SqliteConfig() implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }

    private enum NoopLogger implements Logger {
        INSTANCE;

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
