package com.uxplima.uxmessentials.worlds.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.WorldTeleportService;
import com.uxplima.uxmessentials.worlds.application.WorldsMessageKey;
import com.uxplima.uxmessentials.worlds.application.WorldsSettings;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.BackupId;
import com.uxplima.uxmessentials.worlds.domain.BackupRef;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the backup/restore orchestration against a real {@link WorldArchiver} and the real
 * filesystem, with the tick boundary collapsed: a synchronous {@link Scheduler} double runs every
 * {@code async}/{@code onGlobal}/{@code onEntity} body inline, so a single call drives the whole flow.
 *
 * <p>MockBukkit's {@code ServerMock} cannot back these tests: {@code getWorldContainer()} throws
 * {@code UnimplementedOperationException}, so the world container has to be a {@link TempDir} we control
 * and {@link Server}, {@link World} and {@link Player} are Mockito doubles here. Backup deliberately does
 * NOT call {@code World#save()} (a full-world flush has no single owning thread on Folia): it copies the
 * on-disk, auto-saved world folder, so the backup test below loads no world at all and still produces a
 * complete archive. The load-bearing coverage is the {@code WorldArchiver} (zip/unzip/delete) and the
 * orchestration order, both of which run live; {@code getPlayers()} returns the doubles we seed.
 */
class BukkitWorldArchiveTest {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final WorldName WORLD = WorldName.of("arena");
    private static final PlayerRef INITIATOR = new PlayerRef(UUID.randomUUID(), "Admin");

    private final WorldArchiver archiver = new WorldArchiver();
    private final CapturingScheduler scheduler = new CapturingScheduler();
    private final FakeWorldEngine engine = new FakeWorldEngine();
    private final FakeWorldRepository repository = new FakeWorldRepository();
    private final List<Forced> forcedTeleports = new ArrayList<>();
    private final WorldTeleportService teleporter = recordingTeleporter();
    private final ForcedWorldEntryMarker marker = new ForcedWorldEntryMarker();
    private final RecordingMessages messages = new RecordingMessages();
    private final Notifier notifier = new Notifier(messages, (viewer, text) -> {});
    private final NoOpLogger log = new NoOpLogger();

    /** A {@code WorldTeleportService} mock whose {@code forced} call appends to {@link #forcedTeleports}. */
    private WorldTeleportService recordingTeleporter() {
        WorldTeleportService mock = mock(WorldTeleportService.class);
        lenient().when(mock.forced(any(), any(), any())).thenAnswer(inv -> {
            forcedTeleports.add(new Forced(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
            return Result.ok();
        });
        return mock;
    }

    private record Forced(PlayerRef actor, PlayerRef subject, WorldName target) {}

    @Test
    void backupZipsTheWorldFolderPrunesAndNotifiesCreated(@TempDir Path container, @TempDir Path dataFolder)
            throws IOException {
        seedWorldFolder(container, "level.dat", "level-bytes");
        seedWorldFolder(container, "region/r.0.0.mca", "region-bytes");
        // The world is loaded, but backup must NOT flush it with World#save(). A full-world save has no single
        // owning thread on Folia. It copies the on-disk, auto-saved folder instead, so save() is never called.
        World live = world(new ArrayList<>());
        when(server.getWorld("arena")).thenReturn(live);
        BukkitWorldArchive archive = archive(container, dataFolder, settings("backups/worlds", 10));

        Result<BackupId, WorldError> result = archive.backup(INITIATOR, WORLD);

        assertThat(result.isOk()).isTrue();
        org.mockito.Mockito.verify(live, org.mockito.Mockito.never()).save();
        BackupId id = result.orElseThrow();
        Path zip = dataFolder.resolve("backups/worlds/arena").resolve(id.value() + ".zip");
        assertThat(Files.isRegularFile(zip)).isTrue();
        assertThat(messages.keysFor(INITIATOR)).contains(WorldsMessageKey.WORLD_BACKUP_CREATED);

        Path roundTrip = dataFolder.resolve("round-trip");
        archiver.unzip(zip, roundTrip);
        assertThat(read(roundTrip.resolve("level.dat"))).isEqualTo("level-bytes");
        assertThat(read(roundTrip.resolve("region/r.0.0.mca"))).isEqualTo("region-bytes");
    }

    @Test
    void backupPrunesDownToTheRetentionCount(@TempDir Path container, @TempDir Path dataFolder) throws IOException {
        seedWorldFolder(container, "level.dat", "level-bytes");
        Path backups = Files.createDirectories(dataFolder.resolve("backups/worlds/arena"));
        // Twelve pre-existing archives, oldest → newest by last-modified time.
        for (int i = 0; i < 12; i++) {
            Path old = backups.resolve(STAMP.format(Instant.EPOCH.plus(Duration.ofMinutes(i))) + ".zip");
            writeFile(old, "old".getBytes(StandardCharsets.UTF_8));
            Files.setLastModifiedTime(old, FileTime.from(Instant.EPOCH.plus(Duration.ofMinutes(i))));
        }
        BukkitWorldArchive archive = archive(container, dataFolder, settings("backups/worlds", 10));

        archive.backup(INITIATOR, WORLD);

        try (var stream = Files.list(backups)) {
            assertThat(stream.filter(p -> p.toString().endsWith(".zip")).count())
                    .isEqualTo(10);
        }
        // The two oldest (minute 0 and minute 1) are the ones pruned.
        assertThat(Files.exists(backups.resolve(STAMP.format(Instant.EPOCH) + ".zip")))
                .isFalse();
        assertThat(Files.exists(backups.resolve(STAMP.format(Instant.EPOCH.plus(Duration.ofMinutes(1))) + ".zip")))
                .isFalse();
    }

    @Test
    void listReturnsArchivesNewestFirstAndIgnoresStrangers(@TempDir Path container, @TempDir Path dataFolder)
            throws IOException {
        Path backups = Files.createDirectories(dataFolder.resolve("backups/worlds/arena"));
        Path older = backups.resolve(STAMP.format(Instant.EPOCH) + ".zip");
        Path newer = backups.resolve(STAMP.format(Instant.EPOCH.plus(Duration.ofHours(1))) + ".zip");
        writeFile(older, "a".getBytes(StandardCharsets.UTF_8));
        writeFile(newer, "b".getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(older, FileTime.from(Instant.EPOCH));
        Files.setLastModifiedTime(newer, FileTime.from(Instant.EPOCH.plus(Duration.ofHours(1))));
        writeFile(backups.resolve("notes.txt"), "x".getBytes(StandardCharsets.UTF_8)); // not a .zip
        writeFile(backups.resolve("bad name.zip"), "y".getBytes(StandardCharsets.UTF_8)); // stem fails BackupId
        BukkitWorldArchive archive = archive(container, dataFolder, settings("backups/worlds", 10));

        List<BackupRef> refs = archive.list(WORLD);

        String newerStem = newer.getFileName().toString().replace(".zip", "");
        String olderStem = older.getFileName().toString().replace(".zip", "");
        assertThat(refs).extracting(r -> r.id().value()).containsExactly(newerStem, olderStem);
    }

    @Test
    void listIsEmptyWhenNoBackupsDirectoryExists(@TempDir Path container, @TempDir Path dataFolder) {
        BukkitWorldArchive archive = archive(container, dataFolder, settings("backups/worlds", 10));

        assertThat(archive.list(WORLD)).isEmpty();
    }

    @Test
    void restoreDrainsToEmptyThenUnloadsSwapsAndReloads(@TempDir Path container, @TempDir Path dataFolder)
            throws IOException {
        // A known archive (a single file "from-backup") and a world folder with different live content.
        BackupId id = new BackupId(STAMP.format(Instant.EPOCH));
        Path knownTree = dataFolder.resolve("known");
        writeFile(knownTree.resolve("level.dat"), "from-backup".getBytes(StandardCharsets.UTF_8));
        Path archiveFile = dataFolder.resolve("backups/worlds/arena").resolve(id.value() + ".zip");
        archiver.zip(knownTree, archiveFile);
        seedWorldFolder(container, "level.dat", "live-and-stale");
        repository.save(ManagedWorld.created(WORLD, WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH));
        engine.loaded.add(WORLD.value());
        engine.defaultWorld = WorldName.of("world");
        Player resident = player();
        List<Player> residents = new ArrayList<>(List.of(resident));
        World live = world(residents);
        // The world is visible while loaded; once the engine unloads it, the lookup returns null (as Paper does),
        // so the swap re-guard sees the world gone and proceeds.
        when(server.getWorld("arena")).thenAnswer(inv -> engine.isLoaded(WORLD) ? live : null);
        BukkitWorldArchive archive = archive(container, dataFolder, settings("backups/worlds", 10));

        Result<Unit, WorldError> result = archive.restore(INITIATOR, WORLD, id);

        assertThat(result.isOk()).isTrue();
        assertThat(marker.consume(resident.getUniqueId())).isTrue(); // the resident was marked as a forced entry
        assertThat(forcedTeleports).hasSize(1);
        assertThat(forcedTeleports.get(0).target()).isEqualTo(WorldName.of("world"));
        CapturedLoop drain = scheduler.last();
        assertThat(drain.initialDelay).isEqualTo(Duration.ZERO);
        assertThat(drain.period).isEqualTo(Duration.ofMillis(50));
        // Nothing touched the folder yet: the players are still present, so the world is still loaded.
        assertThat(engine.unloaded).isEmpty();
        assertThat(read(container.resolve("arena/level.dat"))).isEqualTo("live-and-stale");

        residents.clear(); // the async evacuation has completed; the next drain tick sees the world empty
        drain.task.run();

        assertThat(engine.unloaded).containsExactly(WORLD); // unload-checked happened only once empty
        assertThat(drain.handle.closed).isTrue(); // the loop stopped itself
        ManagedWorld reloaded = Objects.requireNonNull(engine.lastLoaded, "reload happened");
        assertThat(reloaded.name()).isEqualTo(WORLD);
        assertThat(read(container.resolve("arena/level.dat"))).isEqualTo("from-backup"); // folder swapped
        assertThat(messages.keysFor(INITIATOR)).contains(WorldsMessageKey.WORLD_RESTORED);
    }

    @Test
    void restoreAbortsAndKeepsTheFolderWhenPlayersNeverLeave(@TempDir Path container, @TempDir Path dataFolder)
            throws IOException {
        // The archive exists, the world is managed, but the residents never evacuate. The drain must give up
        // after RESTORE_EVACUATE_MAX_TICKS rather than delete a still-loaded world's files.
        BackupId id = new BackupId(STAMP.format(Instant.EPOCH));
        Path knownTree = dataFolder.resolve("known");
        writeFile(knownTree.resolve("level.dat"), "from-backup".getBytes(StandardCharsets.UTF_8));
        archiver.zip(knownTree, dataFolder.resolve("backups/worlds/arena").resolve(id.value() + ".zip"));
        seedWorldFolder(container, "level.dat", "live-and-stale");
        repository.save(ManagedWorld.created(WORLD, WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH));
        engine.loaded.add(WORLD.value());
        List<Player> residents = new ArrayList<>(List.of(player())); // never cleared. The world stays occupied
        World live = world(residents);
        when(server.getWorld("arena")).thenReturn(live);
        BukkitWorldArchive archive = archive(container, dataFolder, settings("backups/worlds", 10));

        assertThat(archive.restore(INITIATOR, WORLD, id).isOk()).isTrue();
        CapturedLoop drain = scheduler.last();
        for (int i = 0; i < 60; i++) { // RESTORE_EVACUATE_MAX_TICKS drain ticks, world never empties
            drain.task.run();
        }

        assertThat(drain.handle.closed).isTrue(); // the loop aborted and stopped itself
        assertThat(engine.unloaded).isEmpty(); // never unloaded a loaded world
        assertThat(read(container.resolve("arena/level.dat"))).isEqualTo("live-and-stale"); // folder NOT deleted
        assertThat(engine.lastLoaded).isNull(); // no reload, the swap never ran
        assertThat(messages.keysFor(INITIATOR)).contains(WorldsMessageKey.WORLD_RESTORE_FAILED);
    }

    @Test
    void restoreAbortsAndKeepsTheFolderWhenUnloadFails(@TempDir Path container, @TempDir Path dataFolder)
            throws IOException {
        // The world empties, but the engine refuses to unload it. The folder must survive, untouched.
        BackupId id = new BackupId(STAMP.format(Instant.EPOCH));
        Path knownTree = dataFolder.resolve("known");
        writeFile(knownTree.resolve("level.dat"), "from-backup".getBytes(StandardCharsets.UTF_8));
        archiver.zip(knownTree, dataFolder.resolve("backups/worlds/arena").resolve(id.value() + ".zip"));
        seedWorldFolder(container, "level.dat", "live-and-stale");
        repository.save(ManagedWorld.created(WORLD, WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH));
        engine.loaded.add(WORLD.value());
        engine.unloadResult = Result.err(WorldError.IO_ERROR);
        List<Player> residents = new ArrayList<>(List.of(player()));
        World live = world(residents);
        when(server.getWorld("arena")).thenReturn(live);
        BukkitWorldArchive archive = archive(container, dataFolder, settings("backups/worlds", 10));

        assertThat(archive.restore(INITIATOR, WORLD, id).isOk()).isTrue();
        CapturedLoop drain = scheduler.last();
        residents.clear(); // world empties so the drain attempts the unload
        drain.task.run();

        assertThat(engine.unloaded).containsExactly(WORLD); // the unload was attempted
        assertThat(drain.handle.closed).isTrue(); // the loop stopped itself
        assertThat(read(container.resolve("arena/level.dat"))).isEqualTo("live-and-stale"); // folder NOT deleted
        assertThat(engine.lastLoaded).isNull(); // no reload, the swap never ran
        assertThat(messages.keysFor(INITIATOR)).contains(WorldsMessageKey.WORLD_RESTORE_FAILED);
    }

    @Test
    void restoreOfMissingArchiveIsRejectedNotifiesAndLeavesTheFolderUntouched(
            @TempDir Path container, @TempDir Path dataFolder) throws IOException {
        seedWorldFolder(container, "level.dat", "untouched");
        repository.save(ManagedWorld.created(WORLD, WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH));
        BukkitWorldArchive archive = archive(container, dataFolder, settings("backups/worlds", 10));

        Result<Unit, WorldError> result = archive.restore(INITIATOR, WORLD, new BackupId(STAMP.format(Instant.EPOCH)));

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(WorldError.BACKUP_NOT_FOUND);
        assertThat(read(container.resolve("arena/level.dat"))).isEqualTo("untouched"); // never deleted
        assertThat(engine.unloaded).isEmpty(); // nothing was destroyed before the validation
        assertThat(forcedTeleports).isEmpty();
        assertThat(scheduler.loops).isEmpty(); // no drain loop was scheduled
        assertThat(messages.keysFor(INITIATOR)).contains(WorldsMessageKey.WORLD_BACKUP_NOT_FOUND);
    }

    @Test
    void restoreOfUnmanagedWorldIsRejectedNotifiesAndLeavesTheFolderUntouched(
            @TempDir Path container, @TempDir Path dataFolder) throws IOException {
        BackupId id = new BackupId(STAMP.format(Instant.EPOCH));
        Path archiveFile = dataFolder.resolve("backups/worlds/arena").resolve(id.value() + ".zip");
        writeFile(archiveFile, "ignored".getBytes(StandardCharsets.UTF_8));
        seedWorldFolder(container, "level.dat", "untouched");
        // repository deliberately has no record of the world
        BukkitWorldArchive archive = archive(container, dataFolder, settings("backups/worlds", 10));

        Result<Unit, WorldError> result = archive.restore(INITIATOR, WORLD, id);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(WorldError.NOT_FOUND);
        assertThat(read(container.resolve("arena/level.dat"))).isEqualTo("untouched");
        assertThat(engine.unloaded).isEmpty();
        assertThat(scheduler.loops).isEmpty(); // no drain loop was scheduled
        assertThat(messages.keysFor(INITIATOR)).contains(WorldsMessageKey.WORLD_NOT_FOUND);
    }

    // ---- fixtures -------------------------------------------------------------------------------

    /** One {@link Server} mock per test; {@code getWorldContainer()} tracks {@link #worldContainer}. */
    private final Server server = newServer();

    @org.jspecify.annotations.Nullable private Path worldContainer;

    private Server newServer() {
        Server s = mock(Server.class);
        lenient()
                .when(s.getWorldContainer())
                .thenAnswer(inv -> Objects.requireNonNull(worldContainer).toFile());
        lenient().when(s.getWorld("arena")).thenReturn(null);
        return s;
    }

    private BukkitWorldArchive archive(Path container, Path dataFolder, WorldsSettings settings) {
        worldContainer = container;
        return new BukkitWorldArchive(
                server,
                scheduler,
                engine,
                repository,
                archiver,
                teleporter,
                marker,
                settings,
                notifier,
                log,
                dataFolder);
    }

    private static WorldsSettings settings(String dir, int retention) {
        return new WorldsSettings(
                new FixedConfig(Map.of("backup.directory", dir, "backup.retention-count", retention)));
    }

    /** A world whose {@code getPlayers()} reads {@code residents} live, so a test can drain it between calls. */
    private World world(List<Player> residents) {
        World w = mock(World.class);
        lenient().when(w.getPlayers()).thenAnswer(inv -> List.copyOf(residents));
        return w;
    }

    private static Player player() {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        lenient().when(p.getName()).thenReturn("Resident");
        return p;
    }

    private static void seedWorldFolder(Path container, String relative, String contents) throws IOException {
        writeFile(container.resolve("arena").resolve(relative), contents.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFile(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(bytes);
        }
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    // ---- doubles --------------------------------------------------------------------------------

    /**
     * Collapses the tick boundary for the fire-and-forget contexts, {@code async}/{@code onGlobal}/
     * {@code onEntity}/{@code onRegion} all run inline, but <em>captures</em> the restore drain loop
     * registered through {@code repeatGlobal} rather than running it. A test advances the drain by
     * invoking {@link CapturedLoop#task} and asserts the loop was stopped via {@link RecordingHandle#closed}.
     */
    private static final class CapturingScheduler implements Scheduler {

        final List<CapturedLoop> loops = new ArrayList<>();

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            CapturedLoop loop = new CapturedLoop(task, initialDelay, period);
            loops.add(loop);
            return loop.handle;
        }

        CapturedLoop last() {
            return loops.get(loops.size() - 1);
        }

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    /** The {@code Runnable} + cancel handle + delays captured from one {@code repeatGlobal} call. */
    private static final class CapturedLoop {
        final Runnable task;
        final RecordingHandle handle = new RecordingHandle();
        final Duration initialDelay;
        final Duration period;

        CapturedLoop(Runnable task, Duration initialDelay, Duration period) {
            this.task = task;
            this.initialDelay = initialDelay;
            this.period = period;
        }
    }

    /** An {@link AutoCloseable} that records whether the drain loop was cancelled. */
    private static final class RecordingHandle implements AutoCloseable {
        boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }

    /** Records the resolved {@code (viewer, key)} so notifications can be asserted without a real sink. */
    private static final class RecordingMessages implements Messages {
        private final List<PlayerRef> viewers = new ArrayList<>();
        private final List<MessageKey> keys = new ArrayList<>();

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            viewers.add(viewer);
            keys.add(key);
            return key.toString();
        }

        List<MessageKey> keysFor(PlayerRef viewer) {
            List<MessageKey> matched = new ArrayList<>();
            for (int i = 0; i < viewers.size(); i++) {
                if (viewers.get(i).equals(viewer)) {
                    matched.add(keys.get(i));
                }
            }
            return matched;
        }
    }

    /** A {@link WorldEngine} fake that records unloads and the last reload, with a settable default world. */
    private static final class FakeWorldEngine implements WorldEngine {
        final Set<String> loaded = new HashSet<>();
        final List<WorldName> unloaded = new ArrayList<>();
        WorldName defaultWorld = WorldName.of("world");
        Result<Unit, WorldError> unloadResult = Result.ok();

        @org.jspecify.annotations.Nullable ManagedWorld lastLoaded;

        @Override
        public Result<Unit, WorldError> create(ManagedWorld world) {
            loaded.add(world.name().value());
            return Result.ok();
        }

        @Override
        public Result<Unit, WorldError> load(ManagedWorld world) {
            lastLoaded = world;
            loaded.add(world.name().value());
            return Result.ok();
        }

        @Override
        public Result<Unit, WorldError> unload(WorldName name, boolean save) {
            unloaded.add(name);
            if (unloadResult.isErr()) {
                return unloadResult; // leave the world "loaded" so the swap guard can observe it
            }
            loaded.remove(name.value());
            return unloadResult;
        }

        @Override
        public Result<Unit, WorldError> deleteFiles(WorldName name) {
            return Result.ok();
        }

        @Override
        public Optional<DetectedWorld> scanFolder(WorldName name) {
            return Optional.empty();
        }

        @Override
        public boolean exists(WorldName name) {
            return loaded.contains(name.value());
        }

        @Override
        public boolean isLoaded(WorldName name) {
            return loaded.contains(name.value());
        }

        @Override
        public Set<WorldName> loadedWorldNames() {
            Set<WorldName> s = new HashSet<>();
            loaded.forEach(n -> s.add(WorldName.of(n)));
            return s;
        }

        @Override
        public Optional<WorldName> defaultWorldName() {
            return Optional.ofNullable(defaultWorld);
        }

        @Override
        public Optional<UUID> uidOf(WorldName name) {
            return Optional.empty();
        }

        @Override
        public int playerCount(WorldName name) {
            return 0;
        }

        @Override
        public Optional<Position> spawnPoint(WorldName name) {
            return Optional.empty();
        }
    }

    /** A map-backed {@link WorldRepository} adequate for the find/save the archive needs. */
    private static final class FakeWorldRepository implements WorldRepository {
        private final Map<String, ManagedWorld> byName = new java.util.HashMap<>();

        @Override
        public Optional<ManagedWorld> find(WorldName name) {
            return Optional.ofNullable(byName.get(name.value()));
        }

        @Override
        public List<ManagedWorld> all() {
            return List.copyOf(byName.values());
        }

        @Override
        public boolean exists(WorldName name) {
            return byName.containsKey(name.value());
        }

        @Override
        public void save(ManagedWorld world) {
            byName.put(world.name().value(), world);
        }

        @Override
        public void delete(WorldName name) {
            byName.remove(name.value());
        }
    }

    /** A map-backed {@link com.uxplima.uxmessentials.shared.application.port.ConfigStore} addressed by dotted path. */
    private record FixedConfig(Map<String, Object> values)
            implements com.uxplima.uxmessentials.shared.application.port.ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.get(path) instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.get(path) instanceof Integer i ? i : fallback;
        }
    }

    private static final class NoOpLogger implements Logger {
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
